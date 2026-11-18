package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.ClinicCalendar
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.local.ClinicCacheDao
import com.example.data.local.PendingSyncActionEntity
import com.example.domain.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class SakhaPublicAndAuthRepository(
    val db: FirebaseFirestore,
    private val cacheDao: ClinicCacheDao? = null,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context, cacheDao: ClinicCacheDao? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        cacheDao = cacheDao
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    fun observePublicClinicProfile(): Flow<PublicClinicProfile> =
        db.collection("public_clinic_profile").document("main")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(PublicClinicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PublicClinicProfile()
                } else {
                    PublicClinicProfile()
                }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                Log.w(TAG, "Using bundled clinic profile fallback", error)
                emit(PublicClinicProfile())
            }

    fun observePublicDoctorProfile(): Flow<PublicDoctorProfile> =
        db.collection("public_doctor_profile").document("main")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(PublicDoctorProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PublicDoctorProfile()
                } else {
                    PublicDoctorProfile()
                }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                Log.w(TAG, "Using bundled doctor profile fallback", error)
                emit(PublicDoctorProfile())
            }

    /**
     * Public service catalogue strictly sourced from `/public_services` which holds ZERO price fields.
     */
    fun observePublicServices(): Flow<List<PublicService>> =
        db.collection("public_services")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(PublicService::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.published }
                    .sortedBy { it.sortOrder }
                if (list.isEmpty()) ClinicSeedData.defaultServices else list
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                Log.w(TAG, "Using bundled public services fallback", error)
                emit(ClinicSeedData.defaultServices)
            }

    fun observeAllServicesForAdmin(): Flow<List<PublicService>> = flow {
        requireUserId()
        emit(Unit)
    }.let {
        db.collection("public_services")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(PublicService::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.sortOrder }
                list.ifEmpty { ClinicSeedData.defaultServices }
            }
    }

    fun observePublicMedia(): Flow<List<PublicMediaItem>> =
        db.collection("public_media")
            .whereEqualTo("consentApproved", true)
            .whereEqualTo("published", true)
            .snapshots()
            .map { it.toObjects(PublicMediaItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                emit(emptyList())
            }

    suspend fun resolveCurrentUserRole(): UserRole {
        val user = auth.currentUser ?: return UserRole.GUEST
        if (user.isEmailVerified && user.email.equals(OWNER_EMAIL, ignoreCase = true)) {
            return UserRole.SUPER_ADMIN
        }
        return try {
            val staffSnap = db.collection("staff_roles").document(user.uid).get().await()
            if (staffSnap.exists() && (staffSnap.getBoolean("active") == true)) {
                UserRole.fromString(staffSnap.getString("role"))
            } else {
                UserRole.PATIENT
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve staff role; defaulting to PATIENT", e)
            UserRole.PATIENT
        }
    }

    suspend fun isBootstrapAvailable(): Boolean {
        if (auth.currentUser == null) return false
        return try {
            !db.collection("clinic_bootstrap").document("lock").get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun bootstrapInitialSuperAdmin(): Result<Unit> = try {
        val user = auth.currentUser ?: error("Sign in required")
        val batch = db.batch()
        val lockRef = db.collection("clinic_bootstrap").document("lock")
        val staffRef = db.collection("staff_roles").document(user.uid)
        batch.set(
            lockRef,
            mapOf(
                "ownerUid" to user.uid,
                "ownerEmail" to (user.email ?: ""),
                "clinicName" to "Sakha Specialised Dental Centre",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            staffRef,
            mapOf(
                "uid" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: "Super Admin"),
                "role" to UserRole.SUPER_ADMIN.name,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        seedInitialPublicDataIfNeeded()
        logAudit("BOOTSTRAP_SUPER_ADMIN", "staff_roles", user.uid, "Initialized clinic owner account", UserRole.SUPER_ADMIN.name)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "clinic_bootstrap/lock")
        Result.failure(e)
    }

    suspend fun ensureUserAccountInitialized(defaultLang: String = "DARI", defaultCal: String = "DUAL"): Result<UserAccount> = try {
        val user = auth.currentUser ?: error("Not signed in")
        val ref = db.collection("users").document(user.uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val payload = mapOf(
                "userId" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName?.takeIf { it.isNotBlank() } ?: "Patient"),
                "phone" to (user.phoneNumber ?: ""),
                "preferredLanguage" to defaultLang,
                "calendarMode" to defaultCal,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(
                UserAccount(
                    id = user.uid,
                    userId = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: "Patient",
                    preferredLanguage = defaultLang,
                    calendarMode = defaultCal
                )
            )
        } else {
            val account = snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserAccount(id = user.uid, userId = user.uid)
            Result.success(account)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users")
        Result.failure(e)
    }

    suspend fun updateUserPreferences(displayName: String, phone: String, lang: String, calMode: String): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName.trim().ifBlank { "User" },
                "phone" to phone.trim(),
                "preferredLanguage" to lang,
                "calendarMode" to calMode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users")
        Result.failure(e)
    }

    suspend fun seedInitialPublicDataIfNeeded() {
        try {
            requireUserId()
            val clinicRef = db.collection("public_clinic_profile").document("main")
            if (!clinicRef.get().await().exists()) {
                savePublicClinicProfile(PublicClinicProfile())
            }
            val doctorRef = db.collection("public_doctor_profile").document("main")
            if (!doctorRef.get().await().exists()) {
                savePublicDoctorProfile(PublicDoctorProfile())
            }
            val servicesSnap = db.collection("public_services").limit(1).get().await()
            if (servicesSnap.isEmpty) {
                ClinicSeedData.defaultServices.forEach { srv ->
                    savePublicService(srv)
                }
            }
            val pricingSnap = db.collection("internal_service_pricing").limit(1).get().await()
            if (pricingSnap.isEmpty) {
                ClinicSeedData.defaultInternalPricing.forEach { price ->
                    db.collection("internal_service_pricing").document(price.id).set(
                        mapOf(
                            "serviceId" to price.serviceId,
                            "serviceName" to price.serviceName,
                            "category" to price.category,
                            "unitPriceAfn" to price.unitPriceAfn,
                            "bridgeUnitPriceAfn" to price.bridgeUnitPriceAfn,
                            "active" to price.active,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val settingsRef = db.collection("clinic_settings").document("main")
            if (!settingsRef.get().await().exists()) {
                saveClinicSettings(ClinicSettings())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Seed check skipped or failed", e)
        }
    }

    suspend fun savePublicClinicProfile(profile: PublicClinicProfile): Result<Unit> = try {
        requireUserId()
        val payload = mapOf(
            "nameEn" to profile.nameEn.trim(),
            "nameFa" to profile.nameFa.trim(),
            "namePs" to profile.namePs.trim(),
            "motto" to profile.motto.trim(),
            "addressFa" to profile.addressFa.trim(),
            "addressEn" to profile.addressEn.trim(),
            "phones" to profile.phones,
            "email" to profile.email.trim(),
            "workingHoursEn" to profile.workingHoursEn.trim(),
            "workingHoursFa" to profile.workingHoursFa.trim(),
            "fridayStatusEn" to profile.fridayStatusEn.trim(),
            "fridayStatusFa" to profile.fridayStatusFa.trim(),
            "announcementTitle" to profile.announcementTitle.trim(),
            "announcementBody" to profile.announcementBody.trim(),
            "websiteHeroTitle" to profile.websiteHeroTitle.trim(),
            "websiteHeroSubtitle" to profile.websiteHeroSubtitle.trim(),
            "websitePublished" to profile.websitePublished,
            "socialFacebook" to profile.socialFacebook.trim(),
            "socialInstagram" to profile.socialInstagram.trim(),
            "socialWhatsapp" to profile.socialWhatsapp.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("public_clinic_profile").document("main").set(payload).await()
        logAudit("UPDATE_CLINIC_PROFILE", "public_clinic_profile", "main", "Updated clinic & website info", "SUPER_ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "public_clinic_profile/main")
        Result.failure(e)
    }

    suspend fun savePublicDoctorProfile(profile: PublicDoctorProfile): Result<Unit> = try {
        requireUserId()
        val nextVersion = profile.version + 1L
        val payload = mapOf(
            "fullNameEn" to profile.fullNameEn.trim(),
            "fullNameFa" to profile.fullNameFa.trim(),
            "titleFa" to profile.titleFa.trim(),
            "titleEn" to profile.titleEn.trim(),
            "bioFa" to profile.bioFa.trim(),
            "bioEn" to profile.bioEn.trim(),
            "bioPs" to profile.bioPs.trim(),
            "education" to profile.education,
            "training" to profile.training,
            "licenses" to profile.licenses,
            "experience" to profile.experience,
            "practiceAreas" to profile.practiceAreas,
            "achievements" to profile.achievements,
            "published" to profile.published,
            "version" to nextVersion,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("public_doctor_profile").document("main").set(payload).await()
        logAudit("UPDATE_DOCTOR_PROFILE", "public_doctor_profile", "main", "Version $nextVersion published", "SUPER_ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "public_doctor_profile/main")
        Result.failure(e)
    }

    /**
     * Saves a service to `/public_services` strictly WITHOUT any price fields.
     */
    suspend fun savePublicService(service: PublicService): Result<Unit> = try {
        requireUserId()
        val docRef = if (service.id.isBlank()) {
            db.collection("public_services").document()
        } else {
            db.collection("public_services").document(service.id)
        }
        val payload = mapOf(
            "nameEn" to service.nameEn.trim(),
            "nameFa" to service.nameFa.trim(),
            "namePs" to service.namePs.trim(),
            "category" to service.category.trim(),
            "descriptionEn" to service.descriptionEn.trim(),
            "descriptionFa" to service.descriptionFa.trim(),
            "descriptionPs" to service.descriptionPs.trim(),
            "generalInfoEn" to service.generalInfoEn.trim(),
            "generalInfoFa" to service.generalInfoFa.trim(),
            "generalInfoPs" to service.generalInfoPs.trim(),
            "imageKey" to service.imageKey,
            "published" to service.published,
            "sortOrder" to service.sortOrder,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        logAudit("SAVE_PUBLIC_SERVICE", "public_services", docRef.id, service.nameEn, "SUPER_ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "public_services")
        Result.failure(e)
    }

    suspend fun deletePublicService(serviceId: String): Result<Unit> = try {
        requireUserId()
        db.collection("public_services").document(serviceId).delete().await()
        logAudit("DELETE_PUBLIC_SERVICE", "public_services", serviceId, "Deleted public service", "SUPER_ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "public_services/$serviceId")
        Result.failure(e)
    }

    suspend fun savePublicMedia(item: PublicMediaItem): Result<Unit> = try {
        requireUserId()
        val docRef = if (item.id.isBlank()) db.collection("public_media").document() else db.collection("public_media").document(item.id)
        docRef.set(
            mapOf(
                "title" to item.title.trim(),
                "category" to item.category,
                "description" to item.description.trim(),
                "imageUri" to item.imageUri,
                "consentApproved" to item.consentApproved,
                "published" to item.published,
                "likesCount" to item.likesCount,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "public_media")
        Result.failure(e)
    }

    fun observeStaffRoles(): Flow<List<StaffRoleRecord>> =
        db.collection("staff_roles")
            .snapshots()
            .map { it.toObjects(StaffRoleRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun saveStaffRole(uid: String, email: String, displayName: String, role: UserRole, active: Boolean): Result<Unit> = try {
        requireUserId()
        val docRef = db.collection("staff_roles").document(uid.trim())
        val existing = docRef.get().await()
        if (existing.exists()) {
            docRef.update(
                mapOf(
                    "uid" to uid.trim(),
                    "email" to email.trim(),
                    "displayName" to displayName.trim(),
                    "role" to role.name,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                mapOf(
                    "uid" to uid.trim(),
                    "email" to email.trim(),
                    "displayName" to displayName.trim(),
                    "role" to role.name,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        logAudit("ASSIGN_STAFF_ROLE", "staff_roles", uid, "$email -> ${role.name} (active=$active)", "SUPER_ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "staff_roles/$uid")
        Result.failure(e)
    }

    // --- PATIENT LINK REQUESTS (NO AUTO-MERGE) ---
    fun observeLinkRequests(isAdmin: Boolean): Flow<List<PatientLinkRequest>> = flow {
        val uid = requireUserId()
        val query = if (isAdmin) {
            db.collection("patient_link_requests")
        } else {
            db.collection("patient_link_requests").whereEqualTo("userId", uid)
        }
        query.snapshots()
            .map { it.toObjects(PatientLinkRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .collect { emit(it) }
    }

    suspend fun submitPatientLinkRequest(name: String, phone: String): Result<Unit> = try {
        val uid = requireUserId()
        val ref = db.collection("patient_link_requests").document()
        ref.set(
            mapOf(
                "userId" to uid,
                "requesterName" to name.trim(),
                "requesterPhone" to phone.trim(),
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "patient_link_requests")
        Result.failure(e)
    }

    suspend fun reviewPatientLinkRequest(requestId: String, patientId: String?, approve: Boolean, note: String): Result<Unit> = try {
        requireUserId()
        val reqRef = db.collection("patient_link_requests").document(requestId)
        val reqSnap = reqRef.get().await()
        val targetUid = reqSnap.getString("userId") ?: error("Missing requester userId")
        val batch = db.batch()
        batch.update(
            reqRef,
            mapOf(
                "status" to if (approve) "APPROVED" else "REJECTED",
                "matchedPatientId" to patientId,
                "adminNote" to note,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
        )
        if (approve && !patientId.isNullOrBlank()) {
            batch.update(
                db.collection("patients").document(patientId),
                mapOf("linkedUserUid" to targetUid, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("users").document(targetUid),
                mapOf("linkedPatientId" to patientId, "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        batch.commit().await()
        logAudit("REVIEW_PATIENT_LINK", "patient_link_requests", requestId, "approve=$approve patientId=$patientId", "ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "patient_link_requests/$requestId")
        Result.failure(e)
    }

    // --- PARTNER / SPOUSE LINKING ---
    fun observePartnerLinks(): Flow<List<PartnerLink>> = flow {
        val uid = requireUserId()
        db.collection("partner_links")
            .whereEqualTo("initiatorUid", uid)
            .snapshots()
            .map { it.toObjects(PartnerLink::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .collect { emit(it) }
    }

    suspend fun invitePartner(partnerEmail: String, shareAppointments: Boolean, shareProgress: Boolean): Result<Unit> = try {
        val uid = requireUserId()
        val ref = db.collection("partner_links").document()
        ref.set(
            mapOf(
                "initiatorUid" to uid,
                "partnerUid" to "",
                "partnerEmail" to partnerEmail.trim(),
                "status" to "INVITED",
                "consentInitiator" to true,
                "consentPartner" to false,
                "shareAppointments" to shareAppointments,
                "shareProgress" to shareProgress,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("PARTNER_INVITE", "partner_links", ref.id, "Invited $partnerEmail", "PATIENT")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "partner_links")
        Result.failure(e)
    }

    suspend fun revokePartnerLink(linkId: String): Result<Unit> = try {
        requireUserId()
        db.collection("partner_links").document(linkId).update(
            mapOf(
                "status" to "REVOKED",
                "consentInitiator" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("PARTNER_REVOKE", "partner_links", linkId, "Revoked partner link", "PATIENT")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "partner_links/$linkId")
        Result.failure(e)
    }

    // --- NOTIFICATIONS, FEEDBACK, SETTINGS, AUDIT, BACKUP ---
    fun observeNotifications(isStaff: Boolean): Flow<List<NotificationItem>> = flow {
        val uid = requireUserId()
        val query = if (isStaff) {
            db.collection("notifications")
        } else {
            db.collection("notifications").whereEqualTo("targetUserId", uid)
        }
        query.snapshots()
            .map { it.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .collect { list -> emit(list.sortedByDescending { it.createdAt?.seconds ?: 0L }) }
    }

    suspend fun createNotification(targetUserId: String, targetRole: String, title: String, body: String, type: String): Result<Unit> = try {
        requireUserId()
        val ref = db.collection("notifications").document()
        ref.set(
            mapOf(
                "targetUserId" to targetUserId,
                "targetRole" to targetRole,
                "title" to title,
                "body" to body,
                "type" to type,
                "readFlag" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "notifications")
        Result.failure(e)
    }

    suspend fun markNotificationRead(id: String): Result<Unit> = try {
        requireUserId()
        db.collection("notifications").document(id).update("readFlag", true).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun observeFeedback(isAdmin: Boolean): Flow<List<FeedbackComplaintItem>> = flow {
        val uid = requireUserId()
        val query = if (isAdmin) {
            db.collection("feedback_complaints")
        } else {
            db.collection("feedback_complaints").whereEqualTo("userId", uid)
        }
        query.snapshots()
            .map { it.toObjects(FeedbackComplaintItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .collect { emit(it) }
    }

    suspend fun submitFeedback(kind: String, rating: Long, message: String): Result<Unit> = try {
        val user = auth.currentUser ?: error("Sign in required")
        val ref = db.collection("feedback_complaints").document()
        ref.set(
            mapOf(
                "userId" to user.uid,
                "authorName" to (user.displayName ?: "Patient"),
                "kind" to kind,
                "rating" to rating,
                "message" to message.trim(),
                "adminResponse" to "",
                "status" to "OPEN",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "feedback_complaints")
        Result.failure(e)
    }

    suspend fun respondToFeedback(itemId: String, response: String, status: String): Result<Unit> = try {
        requireUserId()
        db.collection("feedback_complaints").document(itemId).update(
            mapOf(
                "adminResponse" to response.trim(),
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("RESPOND_FEEDBACK", "feedback_complaints", itemId, status, "ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "feedback_complaints/$itemId")
        Result.failure(e)
    }

    fun observeClinicSettings(): Flow<ClinicSettings> =
        db.collection("clinic_settings").document("main")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(ClinicSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: ClinicSettings()
                } else ClinicSettings()
            }

    suspend fun saveClinicSettings(settings: ClinicSettings): Result<Unit> = try {
        requireUserId()
        db.collection("clinic_settings").document("main").set(
            mapOf(
                "bridgeDefaultPriceAfn" to settings.bridgeDefaultPriceAfn,
                "bookingDepositAfn" to settings.bookingDepositAfn,
                "lateNoShowMinutes" to settings.lateNoShowMinutes,
                "currencyRates" to settings.currencyRates,
                "enabledPaymentMethods" to settings.enabledPaymentMethods,
                "disabledModules" to settings.disabledModules,
                "defaultLanguage" to settings.defaultLanguage,
                "defaultCalendar" to settings.defaultCalendar,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("UPDATE_SETTINGS", "clinic_settings", "main", "Updated system settings", "SUPER_ADMIN")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "clinic_settings/main")
        Result.failure(e)
    }

    fun observeAuditLogs(): Flow<List<AuditLogEntry>> =
        db.collection("audit_logs")
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLogEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun logAudit(action: String, entityType: String, entityId: String, details: String, role: String) {
        val user = auth.currentUser ?: return
        runCatching {
            db.collection("audit_logs").document().set(
                mapOf(
                    "actorUid" to user.uid,
                    "actorEmail" to (user.email ?: ""),
                    "actorRole" to role,
                    "action" to action,
                    "entityType" to entityType,
                    "entityId" to entityId,
                    "details" to details,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeBackups(): Flow<List<BackupSnapshot>> =
        db.collection("backups")
            .snapshots()
            .map { snap ->
                snap.toObjects(BackupSnapshot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun createBackupSnapshot(label: String): Result<BackupSnapshot> = try {
        val user = auth.currentUser ?: error("Sign in required")
        val collections = listOf("patients", "treatments", "appointments", "invoices", "payments", "expenses", "medicines", "suppliers")
        val root = JSONObject()
        var totalDocs = 0L
        for (col in collections) {
            val snap = db.collection(col).get().await()
            val arr = JSONArray()
            snap.documents.forEach { d ->
                val obj = JSONObject()
                obj.put("docId", d.id)
                d.data?.forEach { (k, v) ->
                    obj.put(k, v?.toString() ?: "")
                }
                arr.put(obj)
                totalDocs++
            }
            root.put(col, arr)
        }
        val ref = db.collection("backups").document()
        val jsonPayload = root.toString()
        ref.set(
            mapOf(
                "createdBy" to (user.email ?: user.uid),
                "label" to label.ifBlank { "Manual Backup ${ClinicCalendar.todayIso()}" },
                "summaryCount" to totalDocs,
                "payloadJson" to jsonPayload,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("CREATE_BACKUP", "backups", ref.id, "Backed up $totalDocs records", "SUPER_ADMIN")
        Result.success(BackupSnapshot(id = ref.id, createdBy = user.email ?: user.uid, label = label, summaryCount = totalDocs, payloadJson = jsonPayload))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "backups")
        Result.failure(e)
    }

    suspend fun enqueueOfflineAction(collection: String, docId: String, op: String, json: String) {
        cacheDao?.enqueueAction(
            PendingSyncActionEntity(
                collectionPath = collection,
                documentId = docId,
                operation = op,
                payloadJson = json,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun flushOfflineQueue(): Result<Int> = try {
        val pending = cacheDao?.getPendingActions().orEmpty()
        var syncedCount = 0
        for (action in pending) {
            val map = mutableMapOf<String, Any>()
            val obj = JSONObject(action.payloadJson)
            obj.keys().forEach { key -> map[key] = obj.get(key) }
            map["updatedAt"] = FieldValue.serverTimestamp()
            val ref = if (action.documentId.isBlank()) {
                db.collection(action.collectionPath).document()
            } else {
                db.collection(action.collectionPath).document(action.documentId)
            }
            ref.set(map).await()
            cacheDao?.updateActionStatus(action.localId, "SYNCED")
            syncedCount++
        }
        cacheDao?.clearSyncedActions()
        Result.success(syncedCount)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private const val TAG = "SakhaPublicAuthRepo"
        const val OWNER_EMAIL = "khanchrom7@gmail.com"
    }
}
