package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.*
import com.example.data.local.ClinicPreferencesStore
import com.example.data.local.SakhaLocalDatabase
import com.example.data.remote.DentalAiAssistant
import com.example.data.remote.SakhaClinicalFinanceRepository
import com.example.data.remote.SakhaPublicAndAuthRepository
import com.example.domain.*
import com.example.ui.components.SakhaTopHeader
import com.example.ui.screens.*
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SakhaDentalTheme
import com.example.ui.theme.SoftWhite
import com.example.ui.viewmodel.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

enum class AppDestination(val icon: ImageVector) {
    HOME(Icons.Default.Home),
    DOCTOR(Icons.Default.MedicalServices),
    SERVICES(Icons.Default.LocalHospital),
    WEBSITE(Icons.Default.Public),
    APPOINTMENTS(Icons.Default.EventAvailable),
    PATIENTS(Icons.Default.People),
    DENTAL_CHART(Icons.Default.GridOn),
    SPECIALIZED(Icons.Default.Biotech),
    FINANCE_MTU(Icons.Default.AccountBalanceWallet),
    ANALYTICS(Icons.Default.Assessment),
    SUPER_ADMIN(Icons.Default.AdminPanelSettings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory App Check installation BEFORE any Firebase service / ViewModel access
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val credentialManager = remember { CredentialManager.create(context) }

            val vm: SakhaAppViewModel = viewModel(
                factory = remember {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val app = context.applicationContext
                            val cacheDao = SakhaLocalDatabase.getInstance(app).cacheDao()
                            val publicRepo = SakhaPublicAndAuthRepository(app, cacheDao)
                            val clinicalRepo = SakhaClinicalFinanceRepository(app)
                            val prefsStore = ClinicPreferencesStore(app)
                            return SakhaAppViewModel(
                                publicAuthRepo = publicRepo,
                                clinicalRepo = clinicalRepo,
                                prefsStore = prefsStore,
                                aiAssistant = DentalAiAssistant()
                            ) as T
                        }
                    }
                }
            )

            // Sync FirebaseAuth state cleanly & attempt non-blocking auto sign-in for returning users
            val authUser by remember { Firebase.auth.authStateFlow() }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
            LaunchedEffect(Unit) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = { vm.onSessionChanged(Firebase.auth.currentUser?.uid) },
                    onUnauthenticated = { vm.onSessionChanged(Firebase.auth.currentUser?.uid) },
                    scope = scope
                )
            }
            LaunchedEffect(authUser?.uid) {
                vm.onSessionChanged(authUser?.uid)
            }

            key(authUser?.uid) {
                SakhaEcosystemRoot(
                    viewModel = vm,
                    isSignedIn = authUser != null,
                    onGoogleSignIn = {
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { vm.onSessionChanged(Firebase.auth.currentUser?.uid) },
                            onAuthError = { msg -> vm.showBanner(msg) },
                            scope = scope
                        )
                    },
                    onSignOut = {
                        signOutWithCleanup(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeFirebaseSignOut = { vm.clearAuthenticatedListeners() },
                            onSignOutComplete = { vm.onSessionChanged(null) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SakhaEcosystemRoot(
    viewModel: SakhaAppViewModel,
    isSignedIn: Boolean,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val calendarMode by viewModel.calendarMode.collectAsStateWithLifecycle()
    val strings = remember(language) { LocalizationProvider.getStrings(language) }
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val actualRole by viewModel.actualRole.collectAsStateWithLifecycle()
    val previewRole by viewModel.previewRole.collectAsStateWithLifecycle()
    val effectiveRole by viewModel.effectiveRole.collectAsStateWithLifecycle()
    val userAccount by viewModel.userAccount.collectAsStateWithLifecycle()
    val bootstrapAvailable by viewModel.bootstrapAvailable.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val clinicProfile by viewModel.clinicProfile.collectAsStateWithLifecycle()
    val doctorProfile by viewModel.doctorProfile.collectAsStateWithLifecycle()
    val publicServices by viewModel.publicServices.collectAsStateWithLifecycle()
    val publicMedia by viewModel.publicMedia.collectAsStateWithLifecycle()

    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val treatments by viewModel.treatments.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val mtuReceipts by viewModel.mtuReceipts.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val payroll by viewModel.payroll.collectAsStateWithLifecycle()
    val orthoCases by viewModel.orthoCases.collectAsStateWithLifecycle()
    val implants by viewModel.implants.collectAsStateWithLifecycle()
    val labOrders by viewModel.labOrders.collectAsStateWithLifecycle()
    val prescriptions by viewModel.prescriptions.collectAsStateWithLifecycle()
    val medicines by viewModel.medicines.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val feedbackList by viewModel.feedback.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val clinicSettings by viewModel.clinicSettings.collectAsStateWithLifecycle()
    val internalPricing by viewModel.internalPricing.collectAsStateWithLifecycle()
    val staffRoles by viewModel.staffRoles.collectAsStateWithLifecycle()
    val linkRequests by viewModel.linkRequests.collectAsStateWithLifecycle()
    val partnerLinks by viewModel.partnerLinks.collectAsStateWithLifecycle()
    val lastSyncIso by viewModel.lastSyncIso.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    val destinations = remember(effectiveRole) {
        when (effectiveRole) {
            UserRole.GUEST -> listOf(
                AppDestination.HOME,
                AppDestination.DOCTOR,
                AppDestination.SERVICES,
                AppDestination.WEBSITE
            )
            UserRole.PATIENT -> listOf(
                AppDestination.HOME,
                AppDestination.DOCTOR,
                AppDestination.SERVICES,
                AppDestination.WEBSITE
            )
            UserRole.RECEPTIONIST -> listOf(
                AppDestination.APPOINTMENTS,
                AppDestination.PATIENTS,
                AppDestination.FINANCE_MTU,
                AppDestination.DOCTOR,
                AppDestination.SERVICES
            )
            UserRole.DOCTOR -> listOf(
                AppDestination.PATIENTS,
                AppDestination.DENTAL_CHART,
                AppDestination.SPECIALIZED,
                AppDestination.APPOINTMENTS,
                AppDestination.DOCTOR
            )
            UserRole.ADMIN, UserRole.SENIOR_ADMIN -> listOf(
                AppDestination.FINANCE_MTU,
                AppDestination.APPOINTMENTS,
                AppDestination.PATIENTS,
                AppDestination.ANALYTICS,
                AppDestination.DENTAL_CHART
            )
            UserRole.SUPER_ADMIN -> listOf(
                AppDestination.SUPER_ADMIN,
                AppDestination.FINANCE_MTU,
                AppDestination.PATIENTS,
                AppDestination.DENTAL_CHART,
                AppDestination.SPECIALIZED,
                AppDestination.ANALYTICS,
                AppDestination.APPOINTMENTS
            )
        }
    }

    var selectedDestination by remember(effectiveRole) { mutableStateOf(destinations.first()) }
    var selectedChartPatient by remember { mutableStateOf<PatientRecord?>(null) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (selectedDestination !in destinations) {
        selectedDestination = destinations.first()
    }

    // Return to primary home destination cleanly on back press
    BackHandler(enabled = selectedDestination != destinations.first()) {
        selectedDestination = destinations.first()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        SakhaDentalTheme(isRtlLanguage = language.isRtl) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 700.dp

                Scaffold(
                    containerColor = SoftWhite,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        SakhaTopHeader(
                            strings = strings,
                            currentLanguage = language,
                            calendarMode = calendarMode,
                            effectiveRole = effectiveRole,
                            actualRole = actualRole,
                            isSignedIn = isSignedIn,
                            onLanguageSelected = viewModel::setLanguage,
                            onCycleCalendar = {
                                val next = when (calendarMode) {
                                    CalendarMode.DUAL -> CalendarMode.SOLAR_HIJRI
                                    CalendarMode.SOLAR_HIJRI -> CalendarMode.GREGORIAN
                                    CalendarMode.GREGORIAN -> CalendarMode.DUAL
                                }
                                viewModel.setCalendarMode(next)
                            },
                            onGoogleSignIn = onGoogleSignIn,
                            onSignOut = onSignOut,
                            onExitPreview = { viewModel.setRolePreview(null) },
                            onOpenNotifications = { showNotificationsDialog = true },
                            unreadCount = notifications.count { !it.readFlag }
                        )
                    },
                    bottomBar = {
                        if (!useNavigationRail) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                destinations.take(5).forEach { dest ->
                                    NavigationBarItem(
                                        selected = selectedDestination == dest,
                                        onClick = { selectedDestination = dest },
                                        icon = { Icon(dest.icon, contentDescription = destinationTitle(dest, strings)) },
                                        label = {
                                            Text(
                                                text = destinationShortLabel(dest, strings),
                                                maxLines = 1
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = RoyalBlue,
                                            selectedTextColor = RoyalBlue
                                        ),
                                        modifier = Modifier.testTag("nav_${dest.name}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                                destinations.forEach { dest ->
                                    NavigationRailItem(
                                        selected = selectedDestination == dest,
                                        onClick = { selectedDestination = dest },
                                        icon = { Icon(dest.icon, contentDescription = destinationTitle(dest, strings)) },
                                        label = { Text(destinationShortLabel(dest, strings), maxLines = 1) },
                                        modifier = Modifier.testTag("rail_${dest.name}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when (selectedDestination) {
                                AppDestination.HOME -> {
                                    if (effectiveRole == UserRole.PATIENT && isSignedIn) {
                                        val linkedPatient = patients.firstOrNull { !it.archived }
                                        PatientDashboardScreen(
                                            strings = strings,
                                            language = language,
                                            calendarMode = calendarMode,
                                            userAccount = userAccount,
                                            linkedPatient = linkedPatient,
                                            appointments = appointments,
                                            treatments = treatments,
                                            prescriptions = prescriptions,
                                            invoices = invoices,
                                            payments = payments,
                                            mtuReceipts = mtuReceipts,
                                            partnerLinks = partnerLinks,
                                            linkRequests = linkRequests,
                                            clinicProfile = clinicProfile,
                                            services = publicServices,
                                            onBookAppointment = viewModel::saveAppointment,
                                            onCancelAppointment = viewModel::cancelAppointment,
                                            onSubmitSimpleMtuReceipt = viewModel::submitSimpleMtuReceipt,
                                            onSubmitLinkRequest = viewModel::submitLinkRequest,
                                            onInvitePartner = viewModel::invitePartner,
                                            onRevokePartner = viewModel::revokePartnerLink,
                                            onSubmitFeedback = viewModel::submitFeedback,
                                            onOpenDoctorProfile = { selectedDestination = AppDestination.DOCTOR },
                                            onOpenServices = { selectedDestination = AppDestination.SERVICES },
                                            bootstrapAvailable = bootstrapAvailable,
                                            onBootstrapOwner = viewModel::bootstrapOwner
                                        )
                                    } else {
                                        GuestHomeScreen(
                                            strings = strings,
                                            language = language,
                                            clinic = clinicProfile,
                                            doctor = doctorProfile,
                                            servicesCount = publicServices.size,
                                            onOpenDoctorProfile = { selectedDestination = AppDestination.DOCTOR },
                                            onOpenServices = { selectedDestination = AppDestination.SERVICES },
                                            onOpenBookingOrSignIn = {
                                                if (!isSignedIn) onGoogleSignIn() else selectedDestination = AppDestination.APPOINTMENTS
                                            },
                                            onOpenWebsitePreview = { selectedDestination = AppDestination.WEBSITE }
                                        )
                                    }
                                }
                                AppDestination.DOCTOR -> DoctorProfileScreen(strings, language, doctorProfile)
                                AppDestination.SERVICES -> PublicServicesScreen(
                                    strings = strings,
                                    language = language,
                                    services = publicServices,
                                    onRequestConsultation = {
                                        if (!isSignedIn) onGoogleSignIn() else selectedDestination = if (effectiveRole == UserRole.PATIENT) AppDestination.HOME else AppDestination.APPOINTMENTS
                                    }
                                )
                                AppDestination.WEBSITE -> WebsitePreviewAndMediaScreen(
                                    strings = strings,
                                    language = language,
                                    clinic = clinicProfile,
                                    doctor = doctorProfile,
                                    services = publicServices,
                                    media = publicMedia,
                                    isAdmin = effectiveRole.isAdminOrAbove,
                                    onAddMedia = viewModel::savePublicMedia,
                                    onNavigateDoctor = { selectedDestination = AppDestination.DOCTOR },
                                    onNavigateServices = { selectedDestination = AppDestination.SERVICES }
                                )
                                AppDestination.APPOINTMENTS -> AppointmentsStaffScreen(
                                    strings = strings,
                                    language = language,
                                    calendarMode = calendarMode,
                                    appointments = appointments,
                                    patients = patients,
                                    services = publicServices,
                                    settings = clinicSettings,
                                    onSaveAppointment = viewModel::saveAppointment
                                )
                                AppDestination.PATIENTS -> PatientsStaffScreen(
                                    strings = strings,
                                    patients = patients,
                                    onSavePatient = viewModel::savePatient,
                                    onArchivePatient = viewModel::archivePatient,
                                    onOpenChartForPatient = { p ->
                                        selectedChartPatient = p
                                        selectedDestination = AppDestination.DENTAL_CHART
                                    }
                                )
                                AppDestination.DENTAL_CHART -> DentalChartAndTreatmentScreen(
                                    strings = strings,
                                    language = language,
                                    patients = patients,
                                    selectedPatient = selectedChartPatient,
                                    treatments = treatments,
                                    internalPricing = internalPricing,
                                    bridgeDefaultPriceAfn = clinicSettings.bridgeDefaultPriceAfn,
                                    onSelectPatient = { selectedChartPatient = it },
                                    onSaveTreatment = viewModel::saveTreatment,
                                    onDeleteTreatment = viewModel::deleteTreatment
                                )
                                AppDestination.SPECIALIZED -> SpecializedClinicalScreen(
                                    strings = strings,
                                    language = language,
                                    patients = patients,
                                    orthoCases = orthoCases,
                                    implants = implants,
                                    labOrders = labOrders,
                                    prescriptions = prescriptions,
                                    medicines = medicines,
                                    clinicProfile = clinicProfile,
                                    aiState = aiState,
                                    onSaveOrtho = viewModel::saveOrthoCase,
                                    onSaveImplant = viewModel::saveImplant,
                                    onSaveLabOrder = viewModel::saveLabOrder,
                                    onSavePrescription = viewModel::savePrescription,
                                    onSaveMedicine = viewModel::saveMedicine,
                                    onRunAiAnalysis = viewModel::runAiClinicalAnalysis
                                )
                                AppDestination.FINANCE_MTU -> FinanceAndMtuScreen(
                                    strings = strings,
                                    language = language,
                                    effectiveRole = effectiveRole,
                                    settings = clinicSettings,
                                    patients = patients,
                                    mtuReceipts = mtuReceipts,
                                    payments = payments,
                                    invoices = invoices,
                                    expenses = expenses,
                                    suppliers = suppliers,
                                    payroll = payroll,
                                    onApproveMtu = viewModel::approveMtuReceipt,
                                    onRejectMtu = viewModel::rejectMtuReceipt,
                                    onCreateInvoice = viewModel::createInvoice,
                                    onRecordDirectPayment = viewModel::recordDirectPayment,
                                    onAddExpense = viewModel::addExpense,
                                    onVoidExpense = viewModel::voidExpense,
                                    onSaveSupplier = viewModel::saveSupplier,
                                    onSavePayroll = viewModel::savePayroll
                                )
                                AppDestination.ANALYTICS -> AnalyticsScreen(
                                    strings = strings,
                                    language = language,
                                    patients = patients,
                                    appointments = appointments,
                                    treatments = treatments,
                                    payments = payments,
                                    expenses = expenses,
                                    invoices = invoices,
                                    suppliers = suppliers,
                                    medicines = medicines,
                                    staffRoles = staffRoles,
                                    lastSyncIso = lastSyncIso
                                )
                                AppDestination.SUPER_ADMIN -> SuperAdminControlScreen(
                                    strings = strings,
                                    actualRole = actualRole,
                                    previewRole = previewRole,
                                    clinicProfile = clinicProfile,
                                    doctorProfile = doctorProfile,
                                    services = publicServices,
                                    internalPricing = internalPricing,
                                    staffRoles = staffRoles,
                                    linkRequests = linkRequests,
                                    patients = patients,
                                    feedbackList = feedbackList,
                                    settings = clinicSettings,
                                    backups = backups,
                                    auditLogs = auditLogs,
                                    onSetRolePreview = viewModel::setRolePreview,
                                    onSaveClinicProfile = viewModel::saveClinicProfile,
                                    onSaveDoctorProfile = viewModel::saveDoctorProfile,
                                    onSavePublicService = viewModel::savePublicService,
                                    onDeletePublicService = viewModel::deletePublicService,
                                    onSaveInternalPricing = viewModel::saveInternalPricing,
                                    onSaveStaffRole = viewModel::saveStaffRole,
                                    onReviewLinkRequest = viewModel::reviewLinkRequest,
                                    onRespondFeedback = viewModel::respondFeedback,
                                    onSaveSettings = viewModel::saveSettings,
                                    onCreateBackup = viewModel::createBackup,
                                    onFlushOfflineSync = viewModel::flushOfflineSync
                                )
                            }
                        }
                    }
                }
            }

            if (showNotificationsDialog) {
                NotificationsDialog(
                    strings = strings,
                    notifications = notifications,
                    onMarkRead = viewModel::markNotificationRead,
                    onDismiss = { showNotificationsDialog = false }
                )
            }
        }
    }
}

private fun destinationTitle(dest: AppDestination, strings: AppStrings): String = when (dest) {
    AppDestination.HOME -> strings.patientPortal
    AppDestination.DOCTOR -> strings.doctorProfileMenu
    AppDestination.SERVICES -> strings.ourServicesMenu
    AppDestination.WEBSITE -> strings.websiteMenu
    AppDestination.APPOINTMENTS -> strings.appointmentsMenu
    AppDestination.PATIENTS -> strings.patientsMenu
    AppDestination.DENTAL_CHART -> strings.dentalChartMenu
    AppDestination.SPECIALIZED -> strings.clinicalModulesMenu
    AppDestination.FINANCE_MTU -> strings.paymentsMenu
    AppDestination.ANALYTICS -> strings.analyticsMenu
    AppDestination.SUPER_ADMIN -> strings.superAdminPanel
}

private fun destinationShortLabel(dest: AppDestination, strings: AppStrings): String = when (dest) {
    AppDestination.HOME -> strings.guestPortal.split(" ").firstOrNull() ?: "Home"
    AppDestination.DOCTOR -> strings.doctorProfileMenu.split(" ").firstOrNull() ?: "Doctor"
    AppDestination.SERVICES -> strings.ourServicesMenu.split(" ").firstOrNull() ?: "Services"
    AppDestination.WEBSITE -> "Web"
    AppDestination.APPOINTMENTS -> strings.appointmentsMenu.split(" ").firstOrNull() ?: "Appts"
    AppDestination.PATIENTS -> strings.patientsMenu
    AppDestination.DENTAL_CHART -> "FDI"
    AppDestination.SPECIALIZED -> "Clinical"
    AppDestination.FINANCE_MTU -> "Finance"
    AppDestination.ANALYTICS -> "Stats"
    AppDestination.SUPER_ADMIN -> "Admin"
}
