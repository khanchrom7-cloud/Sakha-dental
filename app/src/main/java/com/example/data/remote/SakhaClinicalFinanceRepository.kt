package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.core.ClinicCalendar
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SakhaClinicalFinanceRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    // --- PATIENTS ---
    fun observePatients(isStaff: Boolean): Flow<List<PatientRecord>> = flow {
        val uid = requireUserId()
        val query = if (isStaff) {
            db.collection("patients")
        } else {
            db.collection("patients").whereEqualTo("linkedUserUid", uid)
        }
        query.snapshots()
            .map { it.toObjects(PatientRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .collect { emit(it) }
    }

    suspend fun savePatient(patient: PatientRecord): Result<String> = try {
        requireUserId()
        val ref = if (patient.id.isBlank()) db.collection("patients").document() else db.collection("patients").document(patient.id)
        val isNew = patient.id.isBlank()
        val payload = mutableMapOf<String, Any>(
            "patientCode" to patient.patientCode.ifBlank { "SKH-${(1000..9999).random()}" },
            "fullName" to patient.fullName.trim(),
            "phone" to patient.phone.trim(),
            "age" to patient.age,
            "gender" to patient.gender,
            "address" to patient.address.trim(),
            "medicalHistory" to patient.medicalHistory.trim(),
            "allergies" to patient.allergies.trim(),
            "archived" to patient.archived,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!patient.linkedUserUid.isNullOrBlank()) {
            payload["linkedUserUid"] = patient.linkedUserUid
        }
        if (isNew) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            ref.set(payload).await()
        } else {
            ref.update(payload).await()
        }
        Result.success(ref.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "patients")
        Result.failure(e)
    }

    suspend fun setPatientArchived(patientId: String, archived: Boolean): Result<Unit> = try {
        requireUserId()
        db.collection("patients").document(patientId).update(
            mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "patients/$patientId")
        Result.failure(e)
    }

    suspend fun addInternalClinicalNote(patientId: String, note: String): Result<Unit> = try {
        requireUserId()
        db.collection("patients").document(patientId)
            .collection("internal_notes").document()
            .set(mapOf("note" to note.trim(), "createdAt" to FieldValue.serverTimestamp()))
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "patients/$patientId/internal_notes")
        Result.failure(e)
    }

    fun observeInternalNotes(patientId: String): Flow<List<String>> =
        db.collection("patients").document(patientId).collection("internal_notes")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.getString("note") } }

    // --- INTERNAL SERVICE PRICING (STAFF ONLY) ---
    fun observeInternalPricing(): Flow<List<InternalServicePricing>> =
        db.collection("internal_service_pricing")
            .snapshots()
            .map { snap ->
                snap.toObjects(InternalServicePricing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .ifEmpty { ClinicSeedData.defaultInternalPricing }
            }

    suspend fun saveInternalPricing(pricing: InternalServicePricing): Result<Unit> = try {
        requireUserId()
        val ref = if (pricing.id.isBlank()) db.collection("internal_service_pricing").document() else db.collection("internal_service_pricing").document(pricing.id)
        ref.set(
            mapOf(
                "serviceId" to pricing.serviceId.ifBlank { ref.id },
                "serviceName" to pricing.serviceName.trim(),
                "category" to pricing.category.trim(),
                "unitPriceAfn" to pricing.unitPriceAfn,
                "bridgeUnitPriceAfn" to pricing.bridgeUnitPriceAfn,
                "active" to pricing.active,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "internal_service_pricing")
        Result.failure(e)
    }

    // --- TREATMENTS & PROGRESS ---
    fun observeTreatments(isStaff: Boolean): Flow<List<TreatmentPlan>> = flow {
        val uid = requireUserId()
        val query = if (isStaff) {
            db.collection("treatments")
        } else {
            db.collection("treatments").whereEqualTo("linkedUserUid", uid)
        }
        query.snapshots()
            .map { it.toObjects(TreatmentPlan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .collect { emit(it) }
    }

    /**
     * Saves or edits a treatment plan. On edit, recalculates totalPriceAfn cleanly without creating duplicate charges.
     */
    suspend fun saveTreatment(plan: TreatmentPlan): Result<String> = try {
        requireUserId()
        val isNew = plan.id.isBlank()
        val ref = if (isNew) db.collection("treatments").document() else db.collection("treatments").document(plan.id)
        val calculatedTotal = if (plan.bridgeFlag) {
            val (_, t) = ClinicBusinessRules.calculateBridgeTotal(
                plan.abutmentTeeth.map { it.toInt() },
                plan.ponticTeeth.map { it.toInt() },
                plan.unitPriceAfn
            )
            t
        } else {
            ClinicBusinessRules.calculateTreatmentTotal(plan.unitCount.toInt(), plan.unitPriceAfn)
        }

        val payload = mutableMapOf<String, Any>(
            "patientId" to plan.patientId,
            "linkedUserUid" to plan.linkedUserUid,
            "patientName" to plan.patientName,
            "doctorName" to plan.doctorName,
            "treatmentName" to plan.treatmentName,
            "category" to plan.category,
            "teethFdi" to plan.teethFdi,
            "bridgeFlag" to plan.bridgeFlag,
            "abutmentTeeth" to plan.abutmentTeeth,
            "ponticTeeth" to plan.ponticTeeth,
            "unitCount" to plan.unitCount,
            "unitPriceAfn" to plan.unitPriceAfn,
            "totalPriceAfn" to calculatedTotal,
            "status" to plan.status,
            "steps" to plan.steps,
            "completedSteps" to plan.completedSteps,
            "clinicalNotes" to plan.clinicalNotes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isNew) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            ref.set(payload).await()
        } else {
            ref.update(payload).await()
        }
        Result.success(ref.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "treatments")
        Result.failure(e)
    }

    suspend fun deleteTreatment(treatmentId: String): Result<Unit> = try {
        requireUserId()
        db.collection("treatments").document(treatmentId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "treatments/$treatmentId")
        Result.failure(e)
    }

    // --- ORTHO, IMPLANTS, LAB, PRESCRIPTIONS, INVENTORY ---
    fun observeOrthodonticCases(isStaff: Boolean): Flow<List<OrthodonticCase>> = flow {
        val uid = requireUserId()
        val q = if (isStaff) db.collection("orthodontic_cases") else db.collection("orthodontic_cases").whereEqualTo("linkedUserUid", uid)
        q.snapshots().map { it.toObjects(OrthodonticCase::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    suspend fun saveOrthodonticCase(ortho: OrthodonticCase): Result<Unit> = try {
        requireUserId()
        val ref = if (ortho.id.isBlank()) db.collection("orthodontic_cases").document() else db.collection("orthodontic_cases").document(ortho.id)
        ref.set(
            mapOf(
                "patientId" to ortho.patientId,
                "linkedUserUid" to ortho.linkedUserUid,
                "patientName" to ortho.patientName,
                "caseType" to ortho.caseType,
                "applianceInfo" to ortho.applianceInfo,
                "initialAssessment" to ortho.initialAssessment,
                "totalStages" to ortho.totalStages,
                "currentStage" to ortho.currentStage,
                "adjustmentHistory" to ortho.adjustmentHistory,
                "clinicalNotes" to ortho.clinicalNotes,
                "totalFeeAfn" to ortho.totalFeeAfn,
                "status" to ortho.status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observeImplants(isStaff: Boolean): Flow<List<ImplantRecord>> = flow {
        val uid = requireUserId()
        val q = if (isStaff) db.collection("implant_records") else db.collection("implant_records").whereEqualTo("linkedUserUid", uid)
        q.snapshots().map { it.toObjects(ImplantRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    suspend fun saveImplantRecord(implant: ImplantRecord): Result<Unit> = try {
        requireUserId()
        val ref = if (implant.id.isBlank()) db.collection("implant_records").document() else db.collection("implant_records").document(implant.id)
        ref.set(
            mapOf(
                "patientId" to implant.patientId,
                "linkedUserUid" to implant.linkedUserUid,
                "patientName" to implant.patientName,
                "toothFdi" to implant.toothFdi,
                "siteDescription" to implant.siteDescription,
                "brandModel" to implant.brandModel,
                "dimensions" to implant.dimensions,
                "batchLotNumber" to implant.batchLotNumber,
                "surgeryDate" to implant.surgeryDate,
                "healingStage" to implant.healingStage,
                "prostheticStage" to implant.prostheticStage,
                "finalRestorationDone" to implant.finalRestorationDone,
                "followUpNotes" to implant.followUpNotes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observeLabOrders(): Flow<List<LabOrder>> =
        db.collection("lab_orders").snapshots().map { it.toObjects(LabOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun saveLabOrder(order: LabOrder): Result<Unit> = try {
        requireUserId()
        val ref = if (order.id.isBlank()) db.collection("lab_orders").document() else db.collection("lab_orders").document(order.id)
        ref.set(
            mapOf(
                "patientId" to order.patientId,
                "patientName" to order.patientName,
                "workType" to order.workType,
                "laboratoryName" to order.laboratoryName,
                "orderDate" to order.orderDate,
                "expectedDelivery" to order.expectedDelivery,
                "shade" to order.shade,
                "material" to order.material,
                "instructions" to order.instructions,
                "costAfn" to order.costAfn,
                "status" to order.status,
                "fittingHistory" to order.fittingHistory,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observePrescriptions(isStaff: Boolean): Flow<List<PrescriptionRecord>> = flow {
        val uid = requireUserId()
        val q = if (isStaff) db.collection("prescriptions") else db.collection("prescriptions").whereEqualTo("linkedUserUid", uid)
        q.snapshots().map { it.toObjects(PrescriptionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    suspend fun savePrescription(rx: PrescriptionRecord): Result<Unit> = try {
        requireUserId()
        val ref = if (rx.id.isBlank()) db.collection("prescriptions").document() else db.collection("prescriptions").document(rx.id)
        val medMaps = rx.medicines.map {
            mapOf(
                "medicineName" to it.medicineName,
                "dosage" to it.dosage,
                "frequency" to it.frequency,
                "duration" to it.duration,
                "quantity" to it.quantity,
                "mealTiming" to it.mealTiming,
                "instructions" to it.instructions
            )
        }
        ref.set(
            mapOf(
                "patientId" to rx.patientId,
                "linkedUserUid" to rx.linkedUserUid,
                "patientName" to rx.patientName,
                "patientAge" to rx.patientAge,
                "doctorName" to rx.doctorName,
                "visitDate" to rx.visitDate.ifBlank { ClinicCalendar.todayIso() },
                "medicines" to medMaps,
                "notes" to rx.notes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observeMedicines(): Flow<List<MedicineItem>> =
        db.collection("medicines").snapshots().map { it.toObjects(MedicineItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun saveMedicine(med: MedicineItem): Result<Unit> = try {
        requireUserId()
        val ref = if (med.id.isBlank()) db.collection("medicines").document() else db.collection("medicines").document(med.id)
        ref.set(
            mapOf(
                "name" to med.name.trim(),
                "category" to med.category,
                "quantity" to med.quantity,
                "unit" to med.unit,
                "purchasePriceAfn" to med.purchasePriceAfn,
                "sellingPriceAfn" to med.sellingPriceAfn,
                "expiryDate" to med.expiryDate,
                "batchNumber" to med.batchNumber,
                "supplierName" to med.supplierName,
                "minStockLevel" to med.minStockLevel,
                "movementHistory" to med.movementHistory,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    // --- APPOINTMENTS ---
    fun observeAppointments(isStaff: Boolean): Flow<List<AppointmentRecord>> = flow {
        val uid = requireUserId()
        val q = if (isStaff) db.collection("appointments") else db.collection("appointments").whereEqualTo("userId", uid)
        q.snapshots().map { it.toObjects(AppointmentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    suspend fun saveAppointment(appt: AppointmentRecord, existingAll: List<AppointmentRecord>): Result<String> {
        return try {
            val uid = requireUserId()
            if (ClinicBusinessRules.hasAppointmentConflict(
                    existingAll,
                    appt.dateIso,
                    appt.timeSlot,
                    appt.doctorName,
                    ignoreAppointmentId = appt.id.takeIf { it.isNotBlank() }
                )
            ) {
                return Result.failure(IllegalStateException("Time slot conflict: Dr. Sakha already has a booking at ${appt.timeSlot} on ${appt.dateIso}."))
            }
            val isNew = appt.id.isBlank()
            val ref = if (isNew) db.collection("appointments").document() else db.collection("appointments").document(appt.id)
            val payload = mutableMapOf<String, Any>(
                "patientId" to appt.patientId,
                "userId" to appt.userId.ifBlank { uid },
                "patientName" to appt.patientName.trim(),
                "patientPhone" to appt.patientPhone.trim(),
                "doctorName" to appt.doctorName,
                "serviceName" to appt.serviceName,
                "dateIso" to appt.dateIso,
                "solarHijriDate" to appt.solarHijriDate.ifBlank { ClinicCalendar.fromIsoDate(appt.dateIso).toNumericString() },
                "timeSlot" to appt.timeSlot,
                "visitType" to appt.visitType,
                "status" to appt.status,
                "depositRequiredAfn" to appt.depositRequiredAfn,
                "depositPaid" to appt.depositPaid,
                "latePolicyMinutes" to appt.latePolicyMinutes,
                "notes" to appt.notes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "appointments")
            Result.failure(e)
        }
    }

    suspend fun cancelAppointmentByPatient(appointmentId: String): Result<Unit> = try {
        requireUserId()
        db.collection("appointments").document(appointmentId).update(
            mapOf(
                "status" to "CANCELLED",
                "visitType" to "CANCELLED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "appointments/$appointmentId")
        Result.failure(e)
    }

    // --- SIMPLE MTU RECEIPT UPLOAD & MANUAL VERIFICATION ---
    fun observeMtuReceipts(isAdmin: Boolean): Flow<List<MtuReceiptSubmission>> = flow {
        val uid = requireUserId()
        val q = if (isAdmin) db.collection("mtu_receipts") else db.collection("mtu_receipts").whereEqualTo("userId", uid)
        q.snapshots().map { it.toObjects(MtuReceiptSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    /**
     * Submits ONLY the uploaded receipt screenshot. Does NOT count as income or reduce balances.
     */
    suspend fun submitSimpleMtuReceipt(
        patientId: String,
        patientName: String,
        appointmentId: String,
        invoiceId: String,
        receiptImageBase64: String
    ): Result<String> = try {
        val uid = requireUserId()
        val ref = db.collection("mtu_receipts").document()
        ref.set(
            mapOf(
                "userId" to uid,
                "patientId" to patientId,
                "patientName" to patientName.ifBlank { auth.currentUser?.displayName ?: "Patient" },
                "appointmentId" to appointmentId,
                "invoiceId" to invoiceId,
                "receiptImageBase64" to receiptImageBase64,
                "status" to "PENDING_VERIFICATION",
                "approvedAmountAfn" to 0.0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        // Notify administration
        db.collection("notifications").document().set(
            mapOf(
                "targetUserId" to "",
                "targetRole" to "SUPER_ADMIN",
                "title" to "MTU Receipt Uploaded",
                "body" to "$patientName submitted an MTU receipt for verification.",
                "type" to "MTU_SUBMISSION",
                "readFlag" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(ref.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "mtu_receipts")
        Result.failure(e)
    }

    /**
     * Transactional manual approval preventing duplicate approvals and enforcing strict accounting rules.
     */
    suspend fun approveMtuReceipt(receipt: MtuReceiptSubmission, approvedAmountAfn: Double): Result<Unit> = try {
        val adminUid = requireUserId()
        require(approvedAmountAfn > 0.0) { "Approved amount must be greater than 0 AFN." }
        val receiptRef = db.collection("mtu_receipts").document(receipt.id)
        val paymentRef = db.collection("payments").document("mtu_${receipt.id}")

        db.runTransaction { tx ->
            val currentSnap = tx.get(receiptRef)
            val currentStatus = currentSnap.getString("status")
            if (currentStatus != "PENDING_VERIFICATION") {
                throw IllegalStateException("Receipt already processed ($currentStatus). Duplicate approval blocked.")
            }

            tx.update(
                receiptRef,
                mapOf(
                    "status" to "APPROVED",
                    "approvedAmountAfn" to approvedAmountAfn,
                    "reviewedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                paymentRef,
                mapOf(
                    "patientId" to receipt.patientId.ifBlank { receipt.userId },
                    "userId" to receipt.userId,
                    "invoiceId" to receipt.invoiceId,
                    "mtuReceiptId" to receipt.id,
                    "amountAfn" to approvedAmountAfn,
                    "currency" to "AFN",
                    "originalAmount" to approvedAmountAfn,
                    "exchangeRate" to 1.0,
                    "method" to "MTU",
                    "status" to "CONFIRMED",
                    "recordedBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (receipt.appointmentId.isNotBlank()) {
                val apptRef = db.collection("appointments").document(receipt.appointmentId)
                if (tx.get(apptRef).exists()) {
                    tx.update(apptRef, mapOf("depositPaid" to true, "updatedAt" to FieldValue.serverTimestamp()))
                }
            }
            if (receipt.invoiceId.isNotBlank()) {
                val invRef = db.collection("invoices").document(receipt.invoiceId)
                val invSnap = tx.get(invRef)
                if (invSnap.exists()) {
                    val total = invSnap.getDouble("totalAfn") ?: 0.0
                    val oldPaid = invSnap.getDouble("paidAfn") ?: 0.0
                    val newPaid = oldPaid + approvedAmountAfn
                    val newBalance = (total - newPaid).coerceAtLeast(0.0)
                    val newInvStatus = when {
                        newBalance <= 0.01 && newPaid > 0.0 -> "PAID"
                        newPaid > 0.0 -> "PARTIAL"
                        else -> "UNPAID"
                    }
                    tx.update(
                        invRef,
                        mapOf(
                            "paidAfn" to newPaid,
                            "balanceAfn" to newBalance,
                            "status" to newInvStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
        }.await()

        db.collection("notifications").document().set(
            mapOf(
                "targetUserId" to receipt.userId,
                "targetRole" to "PATIENT",
                "title" to "MTU Payment Approved",
                "body" to "Your MTU payment of $approvedAmountAfn AFN was verified and credited.",
                "type" to "MTU_APPROVED",
                "readFlag" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mtu_receipts/${receipt.id}")
        Result.failure(e)
    }

    suspend fun rejectMtuReceipt(receipt: MtuReceiptSubmission, reason: String): Result<Unit> = try {
        val adminUid = requireUserId()
        val ref = db.collection("mtu_receipts").document(receipt.id)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") != "PENDING_VERIFICATION") {
                throw IllegalStateException("Receipt already reviewed.")
            }
            tx.update(
                ref,
                mapOf(
                    "status" to "REJECTED",
                    "rejectionReason" to reason.trim().ifBlank { "Payment could not be matched with clinic receiving phone." },
                    "reviewedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        db.collection("notifications").document().set(
            mapOf(
                "targetUserId" to receipt.userId,
                "targetRole" to "PATIENT",
                "title" to "MTU Receipt Rejected",
                "body" to reason.trim().ifBlank { "Receipt could not be verified." },
                "type" to "MTU_REJECTED",
                "readFlag" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mtu_receipts/${receipt.id}")
        Result.failure(e)
    }

    // --- PAYMENTS, INVOICES, EXPENSES, SUPPLIERS, PAYROLL ---
    fun observePayments(isStaff: Boolean): Flow<List<PaymentRecord>> = flow {
        val uid = requireUserId()
        val q = if (isStaff) db.collection("payments") else db.collection("payments").whereEqualTo("userId", uid)
        q.snapshots().map { it.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    fun observeInvoices(isStaff: Boolean): Flow<List<InvoiceRecord>> = flow {
        val uid = requireUserId()
        val q = if (isStaff) db.collection("invoices") else db.collection("invoices").whereEqualTo("userId", uid)
        q.snapshots().map { it.toObjects(InvoiceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.collect { emit(it) }
    }

    suspend fun createInvoice(patientId: String, userId: String, patientName: String, subtotalAfn: Double, discountAfn: Double): Result<Unit> = try {
        requireUserId()
        val total = (subtotalAfn - discountAfn).coerceAtLeast(0.0)
        db.collection("invoices").document().set(
            mapOf(
                "patientId" to patientId,
                "userId" to userId,
                "patientName" to patientName,
                "subtotalAfn" to subtotalAfn,
                "discountAfn" to discountAfn,
                "totalAfn" to total,
                "paidAfn" to 0.0,
                "balanceAfn" to total,
                "status" to "UNPAID",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    suspend fun recordDirectPayment(
        patientId: String,
        userId: String,
        invoiceId: String,
        originalAmount: Double,
        currency: String,
        exchangeRate: Double,
        method: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        val amountAfn = com.example.core.CurrencyManager.convertToAfn(originalAmount, exchangeRate)
        require(amountAfn > 0.0)
        db.runTransaction { tx ->
            val pRef = db.collection("payments").document()
            tx.set(
                pRef,
                mapOf(
                    "patientId" to patientId,
                    "userId" to userId,
                    "invoiceId" to invoiceId,
                    "mtuReceiptId" to "",
                    "amountAfn" to amountAfn,
                    "currency" to currency,
                    "originalAmount" to originalAmount,
                    "exchangeRate" to exchangeRate,
                    "method" to method,
                    "status" to "CONFIRMED",
                    "recordedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (invoiceId.isNotBlank()) {
                val invRef = db.collection("invoices").document(invoiceId)
                val invSnap = tx.get(invRef)
                if (invSnap.exists()) {
                    val total = invSnap.getDouble("totalAfn") ?: 0.0
                    val paid = (invSnap.getDouble("paidAfn") ?: 0.0) + amountAfn
                    val balance = (total - paid).coerceAtLeast(0.0)
                    val status = if (balance <= 0.01 && paid > 0.0) "PAID" else if (paid > 0.0) "PARTIAL" else "UNPAID"
                    tx.update(invRef, mapOf("paidAfn" to paid, "balanceAfn" to balance, "status" to status, "updatedAt" to FieldValue.serverTimestamp()))
                }
            }
        }.await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observeExpenses(): Flow<List<ExpenseRecord>> =
        db.collection("expenses").snapshots().map { it.toObjects(ExpenseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun addExpense(title: String, category: String, originalAmount: Double, currency: String, exchangeRate: Double, notes: String): Result<Unit> = try {
        val uid = requireUserId()
        val amountAfn = com.example.core.CurrencyManager.convertToAfn(originalAmount, exchangeRate)
        db.collection("expenses").document().set(
            mapOf(
                "title" to title.trim(),
                "category" to category,
                "amountAfn" to amountAfn,
                "currency" to currency,
                "originalAmount" to originalAmount,
                "exchangeRate" to exchangeRate,
                "notes" to notes.trim(),
                "voided" to false,
                "voidReason" to "",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    suspend fun voidExpense(expenseId: String, reason: String): Result<Unit> = try {
        requireUserId()
        db.collection("expenses").document(expenseId).update(
            mapOf(
                "voided" to true,
                "voidReason" to reason.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observeSuppliers(): Flow<List<SupplierRecord>> =
        db.collection("suppliers").snapshots().map { it.toObjects(SupplierRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun saveSupplier(s: SupplierRecord): Result<Unit> = try {
        requireUserId()
        val ref = if (s.id.isBlank()) db.collection("suppliers").document() else db.collection("suppliers").document(s.id)
        val remaining = (s.totalDebtAfn - s.paidAfn).coerceAtLeast(0.0)
        ref.set(
            mapOf(
                "name" to s.name.trim(),
                "phone" to s.phone.trim(),
                "purchaseSummary" to s.purchaseSummary.trim(),
                "totalDebtAfn" to s.totalDebtAfn,
                "paidAfn" to s.paidAfn,
                "remainingBalanceAfn" to remaining,
                "dueDate" to s.dueDate,
                "installmentsSummary" to s.installmentsSummary,
                "overdue" to s.overdue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    fun observePayroll(): Flow<List<PayrollRecord>> =
        db.collection("payroll").snapshots().map { it.toObjects(PayrollRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun savePayroll(p: PayrollRecord): Result<Unit> = try {
        requireUserId()
        val ref = if (p.id.isBlank()) db.collection("payroll").document() else db.collection("payroll").document(p.id)
        val net = (p.baseSalaryAfn - p.advancesAfn - p.deductionsAfn).coerceAtLeast(0.0)
        ref.set(
            mapOf(
                "employeeName" to p.employeeName.trim(),
                "roleTitle" to p.roleTitle.trim(),
                "baseSalaryAfn" to p.baseSalaryAfn,
                "advancesAfn" to p.advancesAfn,
                "deductionsAfn" to p.deductionsAfn,
                "netPaidAfn" to net,
                "periodLabel" to p.periodLabel,
                "status" to p.status,
                "paidDate" to p.paidDate,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
}
