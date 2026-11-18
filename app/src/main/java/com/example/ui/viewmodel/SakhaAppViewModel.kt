package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.CalendarMode
import com.example.core.ClinicCalendar
import com.example.data.local.ClinicPreferencesStore
import com.example.data.remote.AiUiState
import com.example.data.remote.DentalAiAssistant
import com.example.data.remote.SakhaClinicalFinanceRepository
import com.example.data.remote.SakhaPublicAndAuthRepository
import com.example.domain.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

class SakhaAppViewModel(
    private val publicAuthRepo: SakhaPublicAndAuthRepository,
    private val clinicalRepo: SakhaClinicalFinanceRepository,
    private val prefsStore: ClinicPreferencesStore,
    private val aiAssistant: DentalAiAssistant = DentalAiAssistant()
) : ViewModel() {

    val language: StateFlow<AppLanguage> = prefsStore.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.DARI)

    val calendarMode: StateFlow<CalendarMode> = prefsStore.calendarModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CalendarMode.DUAL)

    val lastSyncIso: StateFlow<String> = prefsStore.lastSyncFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    // Public Portal & Connected Website State (accessible without registration/login, ZERO prices)
    val clinicProfile: StateFlow<PublicClinicProfile> = publicAuthRepo.observePublicClinicProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PublicClinicProfile())

    val doctorProfile: StateFlow<PublicDoctorProfile> = publicAuthRepo.observePublicDoctorProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PublicDoctorProfile())

    val publicServices: StateFlow<List<PublicService>> = publicAuthRepo.observePublicServices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ClinicSeedData.defaultServices)

    val publicMedia: StateFlow<List<PublicMediaItem>> = publicAuthRepo.observePublicMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Authenticated Session & RBAC State
    private val _actualRole = MutableStateFlow(UserRole.GUEST)
    val actualRole: StateFlow<UserRole> = _actualRole.asStateFlow()

    private val _previewRole = MutableStateFlow<UserRole?>(null)
    val previewRole: StateFlow<UserRole?> = _previewRole.asStateFlow()

    val effectiveRole: StateFlow<UserRole> = combine(_actualRole, _previewRole) { actual, preview ->
        if (actual == UserRole.SUPER_ADMIN && preview != null) preview else actual
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserRole.GUEST)

    private val _currentUid = MutableStateFlow<String?>(null)
    val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    private val _userAccount = MutableStateFlow<UserAccount?>(null)
    val userAccount: StateFlow<UserAccount?> = _userAccount.asStateFlow()

    private val _bootstrapAvailable = MutableStateFlow(false)
    val bootstrapAvailable: StateFlow<Boolean> = _bootstrapAvailable.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Authenticated Domain Collections (only populated after login & role resolution)
    private val _patients = MutableStateFlow<List<PatientRecord>>(emptyList())
    val patients: StateFlow<List<PatientRecord>> = _patients.asStateFlow()

    private val _treatments = MutableStateFlow<List<TreatmentPlan>>(emptyList())
    val treatments: StateFlow<List<TreatmentPlan>> = _treatments.asStateFlow()

    private val _appointments = MutableStateFlow<List<AppointmentRecord>>(emptyList())
    val appointments: StateFlow<List<AppointmentRecord>> = _appointments.asStateFlow()

    private val _mtuReceipts = MutableStateFlow<List<MtuReceiptSubmission>>(emptyList())
    val mtuReceipts: StateFlow<List<MtuReceiptSubmission>> = _mtuReceipts.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentRecord>>(emptyList())
    val payments: StateFlow<List<PaymentRecord>> = _payments.asStateFlow()

    private val _invoices = MutableStateFlow<List<InvoiceRecord>>(emptyList())
    val invoices: StateFlow<List<InvoiceRecord>> = _invoices.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseRecord>>(emptyList())
    val expenses: StateFlow<List<ExpenseRecord>> = _expenses.asStateFlow()

    private val _suppliers = MutableStateFlow<List<SupplierRecord>>(emptyList())
    val suppliers: StateFlow<List<SupplierRecord>> = _suppliers.asStateFlow()

    private val _payroll = MutableStateFlow<List<PayrollRecord>>(emptyList())
    val payroll: StateFlow<List<PayrollRecord>> = _payroll.asStateFlow()

    private val _medicines = MutableStateFlow<List<MedicineItem>>(emptyList())
    val medicines: StateFlow<List<MedicineItem>> = _medicines.asStateFlow()

    private val _orthoCases = MutableStateFlow<List<OrthodonticCase>>(emptyList())
    val orthoCases: StateFlow<List<OrthodonticCase>> = _orthoCases.asStateFlow()

    private val _implants = MutableStateFlow<List<ImplantRecord>>(emptyList())
    val implants: StateFlow<List<ImplantRecord>> = _implants.asStateFlow()

    private val _labOrders = MutableStateFlow<List<LabOrder>>(emptyList())
    val labOrders: StateFlow<List<LabOrder>> = _labOrders.asStateFlow()

    private val _prescriptions = MutableStateFlow<List<PrescriptionRecord>>(emptyList())
    val prescriptions: StateFlow<List<PrescriptionRecord>> = _prescriptions.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _feedback = MutableStateFlow<List<FeedbackComplaintItem>>(emptyList())
    val feedback: StateFlow<List<FeedbackComplaintItem>> = _feedback.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupSnapshot>>(emptyList())
    val backups: StateFlow<List<BackupSnapshot>> = _backups.asStateFlow()

    private val _clinicSettings = MutableStateFlow(ClinicSettings())
    val clinicSettings: StateFlow<ClinicSettings> = _clinicSettings.asStateFlow()

    private val _internalPricing = MutableStateFlow<List<InternalServicePricing>>(emptyList())
    val internalPricing: StateFlow<List<InternalServicePricing>> = _internalPricing.asStateFlow()

    private val _staffRoles = MutableStateFlow<List<StaffRoleRecord>>(emptyList())
    val staffRoles: StateFlow<List<StaffRoleRecord>> = _staffRoles.asStateFlow()

    private val _linkRequests = MutableStateFlow<List<PatientLinkRequest>>(emptyList())
    val linkRequests: StateFlow<List<PatientLinkRequest>> = _linkRequests.asStateFlow()

    private val _partnerLinks = MutableStateFlow<List<PartnerLink>>(emptyList())
    val partnerLinks: StateFlow<List<PartnerLink>> = _partnerLinks.asStateFlow()

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private var authJobs = mutableListOf<Job>()

    fun onSessionChanged(uid: String?) {
        if (uid == _currentUid.value && authJobs.isNotEmpty()) return
        clearAuthenticatedListeners()
        _currentUid.value = uid
        _previewRole.value = null
        if (uid == null) {
            _actualRole.value = UserRole.GUEST
            _userAccount.value = null
            _bootstrapAvailable.value = false
            return
        }
        viewModelScope.launch {
            val role = publicAuthRepo.resolveCurrentUserRole()
            _actualRole.value = role
            _bootstrapAvailable.value = if (role == UserRole.PATIENT) publicAuthRepo.isBootstrapAvailable() else false
            publicAuthRepo.ensureUserAccountInitialized().onSuccess { account ->
                _userAccount.value = account
            }
            if (role == UserRole.SUPER_ADMIN) {
                publicAuthRepo.seedInitialPublicDataIfNeeded()
            }
            attachAuthenticatedStreams(role)
        }
    }

    fun clearAuthenticatedListeners() {
        authJobs.forEach { it.cancel() }
        authJobs.clear()
        _patients.value = emptyList()
        _treatments.value = emptyList()
        _appointments.value = emptyList()
        _mtuReceipts.value = emptyList()
        _payments.value = emptyList()
        _invoices.value = emptyList()
        _expenses.value = emptyList()
        _suppliers.value = emptyList()
        _payroll.value = emptyList()
        _medicines.value = emptyList()
        _orthoCases.value = emptyList()
        _implants.value = emptyList()
        _labOrders.value = emptyList()
        _prescriptions.value = emptyList()
        _notifications.value = emptyList()
        _feedback.value = emptyList()
        _auditLogs.value = emptyList()
        _backups.value = emptyList()
        _internalPricing.value = emptyList()
        _staffRoles.value = emptyList()
        _linkRequests.value = emptyList()
        _partnerLinks.value = emptyList()
    }

    private fun <T> collectSafe(flow: Flow<T>, target: MutableStateFlow<T>) {
        val job = viewModelScope.launch {
            flow.catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Stream warning: ${(fsError ?: error).message}")
            }.collect { value ->
                target.value = value
            }
        }
        authJobs.add(job)
    }

    private fun attachAuthenticatedStreams(role: UserRole) {
        val isStaff = role.isStaff
        val isAdmin = role.isAdminOrAbove
        val isSeniorOrOwner = role.isSeniorAdminOrAbove

        collectSafe(clinicalRepo.observePatients(isStaff), _patients)
        collectSafe(clinicalRepo.observeTreatments(isStaff), _treatments)
        collectSafe(clinicalRepo.observeAppointments(isStaff), _appointments)
        collectSafe(clinicalRepo.observeMtuReceipts(isAdmin), _mtuReceipts)
        collectSafe(clinicalRepo.observePayments(isStaff), _payments)
        collectSafe(clinicalRepo.observeInvoices(isStaff), _invoices)
        collectSafe(clinicalRepo.observeOrthodonticCases(isStaff), _orthoCases)
        collectSafe(clinicalRepo.observeImplants(isStaff), _implants)
        collectSafe(clinicalRepo.observePrescriptions(isStaff), _prescriptions)
        collectSafe(publicAuthRepo.observeNotifications(isStaff), _notifications)
        collectSafe(publicAuthRepo.observeFeedback(isAdmin), _feedback)
        collectSafe(publicAuthRepo.observeLinkRequests(isAdmin), _linkRequests)
        collectSafe(publicAuthRepo.observePartnerLinks(), _partnerLinks)

        // Staff-only protected collections (Patients NEVER subscribe to internal_service_pricing, expenses, payroll, etc.)
        if (isStaff) {
            collectSafe(clinicalRepo.observeInternalPricing(), _internalPricing)
            collectSafe(clinicalRepo.observeMedicines(), _medicines)
            collectSafe(clinicalRepo.observeLabOrders(), _labOrders)
            collectSafe(publicAuthRepo.observeClinicSettings(), _clinicSettings)
        }
        if (isAdmin) {
            collectSafe(clinicalRepo.observeExpenses(), _expenses)
            collectSafe(clinicalRepo.observeSuppliers(), _suppliers)
        }
        if (isSeniorOrOwner) {
            collectSafe(clinicalRepo.observePayroll(), _payroll)
            collectSafe(publicAuthRepo.observeAuditLogs(), _auditLogs)
        }
        if (role == UserRole.SUPER_ADMIN) {
            collectSafe(publicAuthRepo.observeStaffRoles(), _staffRoles)
            collectSafe(publicAuthRepo.observeBackups(), _backups)
        }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefsStore.setLanguage(lang) }
    }

    fun setCalendarMode(mode: CalendarMode) {
        viewModelScope.launch { prefsStore.setCalendarMode(mode) }
    }

    fun setRolePreview(role: UserRole?) {
        if (_actualRole.value == UserRole.SUPER_ADMIN) {
            _previewRole.value = role
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    private fun reportResult(result: Result<*>, successMessage: String) {
        result.fold(
            onSuccess = { _statusBanner.value = successMessage },
            onFailure = { _statusBanner.value = it.localizedMessage ?: "Operation failed" }
        )
    }

    fun bootstrapOwner() {
        viewModelScope.launch {
            val res = publicAuthRepo.bootstrapInitialSuperAdmin()
            reportResult(res, "Super Admin initialized.")
            if (res.isSuccess) {
                _bootstrapAvailable.value = false
                onSessionChanged(_currentUid.value)
            }
        }
    }

    fun saveClinicProfile(profile: PublicClinicProfile) {
        viewModelScope.launch { reportResult(publicAuthRepo.savePublicClinicProfile(profile), "Clinic profile updated.") }
    }

    fun saveDoctorProfile(profile: PublicDoctorProfile) {
        viewModelScope.launch { reportResult(publicAuthRepo.savePublicDoctorProfile(profile), "Doctor biography published.") }
    }

    fun savePublicService(service: PublicService) {
        viewModelScope.launch { reportResult(publicAuthRepo.savePublicService(service), "Service catalogue updated.") }
    }

    fun deletePublicService(serviceId: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.deletePublicService(serviceId), "Service removed.") }
    }

    fun saveInternalPricing(pricing: InternalServicePricing) {
        viewModelScope.launch { reportResult(clinicalRepo.saveInternalPricing(pricing), "Internal pricing saved.") }
    }

    fun saveStaffRole(uid: String, email: String, name: String, role: UserRole, active: Boolean) {
        viewModelScope.launch { reportResult(publicAuthRepo.saveStaffRole(uid, email, name, role, active), "Staff role updated.") }
    }

    fun savePatient(patient: PatientRecord) {
        viewModelScope.launch { reportResult(clinicalRepo.savePatient(patient), "Patient record saved.") }
    }

    fun archivePatient(patientId: String, archived: Boolean) {
        viewModelScope.launch { reportResult(clinicalRepo.setPatientArchived(patientId, archived), if (archived) "Patient archived." else "Patient restored.") }
    }

    fun saveTreatment(plan: TreatmentPlan) {
        viewModelScope.launch { reportResult(clinicalRepo.saveTreatment(plan), "Treatment plan saved.") }
    }

    fun deleteTreatment(id: String) {
        viewModelScope.launch { reportResult(clinicalRepo.deleteTreatment(id), "Treatment removed.") }
    }

    fun saveAppointment(appt: AppointmentRecord) {
        viewModelScope.launch { reportResult(clinicalRepo.saveAppointment(appt, _appointments.value), "Appointment saved.") }
    }

    fun cancelAppointment(id: String) {
        viewModelScope.launch { reportResult(clinicalRepo.cancelAppointmentByPatient(id), "Appointment cancelled.") }
    }

    fun submitSimpleMtuReceipt(patientId: String, patientName: String, appointmentId: String, invoiceId: String, imageBase64: String) {
        viewModelScope.launch {
            reportResult(
                clinicalRepo.submitSimpleMtuReceipt(patientId, patientName, appointmentId, invoiceId, imageBase64),
                "Receipt submitted — Pending Verification"
            )
        }
    }

    fun approveMtuReceipt(receipt: MtuReceiptSubmission, amountAfn: Double) {
        viewModelScope.launch {
            val res = clinicalRepo.approveMtuReceipt(receipt, amountAfn)
            if (res.isSuccess) {
                publicAuthRepo.logAudit("APPROVE_MTU", "mtu_receipts", receipt.id, "Approved $amountAfn AFN", _actualRole.value.name)
            }
            reportResult(res, "MTU payment approved and credited.")
        }
    }

    fun rejectMtuReceipt(receipt: MtuReceiptSubmission, reason: String) {
        viewModelScope.launch {
            val res = clinicalRepo.rejectMtuReceipt(receipt, reason)
            if (res.isSuccess) {
                publicAuthRepo.logAudit("REJECT_MTU", "mtu_receipts", receipt.id, reason, _actualRole.value.name)
            }
            reportResult(res, "MTU receipt rejected.")
        }
    }

    fun createInvoice(patientId: String, userId: String, patientName: String, subtotal: Double, discount: Double) {
        viewModelScope.launch { reportResult(clinicalRepo.createInvoice(patientId, userId, patientName, subtotal, discount), "Invoice created.") }
    }

    fun recordDirectPayment(patientId: String, userId: String, invoiceId: String, amount: Double, currency: String, rate: Double, method: String) {
        viewModelScope.launch { reportResult(clinicalRepo.recordDirectPayment(patientId, userId, invoiceId, amount, currency, rate, method), "Payment recorded.") }
    }

    fun addExpense(title: String, category: String, amount: Double, currency: String, rate: Double, notes: String) {
        viewModelScope.launch { reportResult(clinicalRepo.addExpense(title, category, amount, currency, rate, notes), "Expense recorded.") }
    }

    fun voidExpense(expenseId: String, reason: String) {
        viewModelScope.launch {
            val res = clinicalRepo.voidExpense(expenseId, reason)
            if (res.isSuccess) publicAuthRepo.logAudit("VOID_EXPENSE", "expenses", expenseId, reason, _actualRole.value.name)
            reportResult(res, "Expense voided and archived.")
        }
    }

    fun saveSupplier(supplier: SupplierRecord) {
        viewModelScope.launch { reportResult(clinicalRepo.saveSupplier(supplier), "Supplier record saved.") }
    }

    fun savePayroll(payroll: PayrollRecord) {
        viewModelScope.launch { reportResult(clinicalRepo.savePayroll(payroll), "Payroll saved.") }
    }

    fun saveMedicine(med: MedicineItem) {
        viewModelScope.launch { reportResult(clinicalRepo.saveMedicine(med), "Inventory updated.") }
    }

    fun saveOrthoCase(ortho: OrthodonticCase) {
        viewModelScope.launch { reportResult(clinicalRepo.saveOrthodonticCase(ortho), "Orthodontic case saved.") }
    }

    fun saveImplant(implant: ImplantRecord) {
        viewModelScope.launch { reportResult(clinicalRepo.saveImplantRecord(implant), "Implant record saved.") }
    }

    fun saveLabOrder(order: LabOrder) {
        viewModelScope.launch { reportResult(clinicalRepo.saveLabOrder(order), "Laboratory order saved.") }
    }

    fun savePrescription(rx: PrescriptionRecord) {
        viewModelScope.launch { reportResult(clinicalRepo.savePrescription(rx), "Prescription saved.") }
    }

    fun submitLinkRequest(name: String, phone: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.submitPatientLinkRequest(name, phone), "Link request sent to administration.") }
    }

    fun reviewLinkRequest(requestId: String, patientId: String?, approve: Boolean, note: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.reviewPatientLinkRequest(requestId, patientId, approve, note), "Link request updated.") }
    }

    fun invitePartner(email: String, shareAppts: Boolean, shareProgress: Boolean) {
        viewModelScope.launch { reportResult(publicAuthRepo.invitePartner(email, shareAppts, shareProgress), "Partner invitation sent.") }
    }

    fun revokePartnerLink(linkId: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.revokePartnerLink(linkId), "Partner link revoked.") }
    }

    fun submitFeedback(kind: String, rating: Long, message: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.submitFeedback(kind, rating, message), "Submitted successfully.") }
    }

    fun respondFeedback(itemId: String, response: String, status: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.respondToFeedback(itemId, response, status), "Response saved.") }
    }

    fun saveSettings(settings: ClinicSettings) {
        viewModelScope.launch { reportResult(publicAuthRepo.saveClinicSettings(settings), "System settings saved.") }
    }

    fun createBackup(label: String) {
        viewModelScope.launch { reportResult(publicAuthRepo.createBackupSnapshot(label), "Backup snapshot created.") }
    }

    fun flushOfflineSync() {
        viewModelScope.launch {
            val res = publicAuthRepo.flushOfflineQueue()
            if (res.isSuccess) {
                prefsStore.setLastSync(ClinicCalendar.todayIso())
            }
            reportResult(res, "Synchronization completed.")
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { publicAuthRepo.markNotificationRead(id) }
    }

    fun savePublicMedia(item: PublicMediaItem) {
        viewModelScope.launch { reportResult(publicAuthRepo.savePublicMedia(item), "Media gallery updated.") }
    }

    fun runAiClinicalAnalysis(modality: String, contextText: String, bitmap: Bitmap?) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            val res = aiAssistant.analyzeClinicalCase(
                modality = modality,
                clinicalContext = contextText,
                languageLabel = language.value.name,
                radiographBitmap = bitmap
            )
            _aiState.value = res.fold(
                onSuccess = { AiUiState.Success(it) },
                onFailure = { AiUiState.Error(it.localizedMessage ?: "AI analysis failed.") }
            )
        }
    }

    fun resetAiState() {
        _aiState.value = AiUiState.Idle
    }

    companion object {
        private const val TAG = "SakhaAppVM"
    }
}
