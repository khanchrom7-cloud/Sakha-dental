package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.core.*
import com.example.domain.*
import com.example.ui.components.ImageEncodingUtils
import com.example.ui.components.SectionHeaderCard
import com.example.ui.theme.*
import java.io.File

@Composable
fun PatientDashboardScreen(
    strings: AppStrings,
    language: AppLanguage,
    calendarMode: CalendarMode,
    userAccount: UserAccount?,
    linkedPatient: PatientRecord?,
    appointments: List<AppointmentRecord>,
    treatments: List<TreatmentPlan>,
    prescriptions: List<PrescriptionRecord>,
    invoices: List<InvoiceRecord>,
    payments: List<PaymentRecord>,
    mtuReceipts: List<MtuReceiptSubmission>,
    partnerLinks: List<PartnerLink>,
    linkRequests: List<PatientLinkRequest>,
    clinicProfile: PublicClinicProfile,
    services: List<PublicService>,
    onBookAppointment: (AppointmentRecord) -> Unit,
    onCancelAppointment: (String) -> Unit,
    onSubmitSimpleMtuReceipt: (patientId: String, patientName: String, apptId: String, invId: String, base64: String) -> Unit,
    onSubmitLinkRequest: (name: String, phone: String) -> Unit,
    onInvitePartner: (email: String, shareAppts: Boolean, shareProg: Boolean) -> Unit,
    onRevokePartner: (String) -> Unit,
    onSubmitFeedback: (kind: String, rating: Long, message: String) -> Unit,
    onOpenDoctorProfile: () -> Unit,
    onOpenServices: () -> Unit,
    bootstrapAvailable: Boolean,
    onBootstrapOwner: () -> Unit
) {
    val context = LocalContext.current
    var showBookingDialog by remember { mutableStateOf(false) }
    var showMtuDialog by remember { mutableStateOf(false) }
    var showHesabPayDialog by remember { mutableStateOf(false) }
    var showLinkRecordDialog by remember { mutableStateOf(false) }
    var showPartnerDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var selectedApptForMtu by remember { mutableStateOf("") }
    var selectedInvoiceForMtu by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .testTag("patient_portal_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (bootstrapAvailable) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GoldAccentSoft),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Initial Clinic Owner Setup", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                            Text("Protected one-time Super Admin initialization available.", style = MaterialTheme.typography.bodySmall, color = RoyalBlueDark)
                        }
                        Button(
                            onClick = onBootstrapOwner,
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                            modifier = Modifier.testTag("bootstrap_super_admin_button")
                        ) {
                            Text("Initialize")
                        }
                    }
                }
            }
        }

        // Welcome & Personal Profile Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${strings.patientPortal} — ${userAccount?.displayName ?: linkedPatient?.fullName ?: "Patient"}",
                        color = PureWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (linkedPatient != null) "Patient ID: ${linkedPatient.patientCode} • ${linkedPatient.phone}"
                        else "Not linked to an existing clinic chart yet",
                        color = GoldAccentSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showBookingDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = RoyalBlueDark),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("patient_book_appointment_button")
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.bookAppointment, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                selectedApptForMtu = appointments.firstOrNull { !it.depositPaid && it.status != "CANCELLED" }?.id.orEmpty()
                                selectedInvoiceForMtu = invoices.firstOrNull { it.balanceAfn > 0 }?.id.orEmpty()
                                showMtuDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("patient_mtu_upload_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("MTU Receipt")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onOpenDoctorProfile, modifier = Modifier.testTag("patient_open_doctor_bio")) {
                            Text("${strings.doctorProfileMenu} →", color = GoldAccentSoft)
                        }
                        TextButton(onClick = onOpenServices, modifier = Modifier.testTag("patient_open_services")) {
                            Text("${strings.ourServicesMenu} →", color = GoldAccentSoft)
                        }
                        TextButton(onClick = { showHesabPayDialog = true }, modifier = Modifier.testTag("patient_hesabpay_button")) {
                            Text("HesabPay QR", color = GoldAccent)
                        }
                    }
                }
            }
        }

        // Link Existing Patient Record & Partner Linking Row
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (linkedPatient == null) {
                            OutlinedButton(
                                onClick = { showLinkRecordDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.requestLinkLabel)
                            }
                        }
                        OutlinedButton(
                            onClick = { showPartnerDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.partnerLinkingLabel)
                        }
                    }
                    linkRequests.forEach { req ->
                        Text("Link Request (${req.requesterPhone}): ${req.status}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    }
                    partnerLinks.forEach { link ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Partner: ${link.partnerEmail} (${link.status})", style = MaterialTheme.typography.labelSmall)
                            if (link.status != "REVOKED") {
                                TextButton(onClick = { onRevokePartner(link.id) }) { Text("Revoke", color = ErrorCrimson) }
                            }
                        }
                    }
                }
            }
        }

        // Personal Appointments
        item { SectionHeaderCard(title = strings.appointmentsMenu, icon = Icons.Default.Event) }
        if (appointments.isEmpty()) {
            item { EmptyInfoCard(strings.noRecordsYet) }
        } else {
            items(appointments, key = { it.id }) { appt ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(appt.serviceName.ifBlank { "Dental Consultation" }, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                            StatusPill(appt.status)
                        }
                        Text(ClinicCalendar.formatDate(appt.dateIso, calendarMode, language) + " • " + appt.timeSlot, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                        Text(
                            text = if (appt.depositPaid) "Deposit: Confirmed (150 AFN)" else "Deposit Pending: 150 AFN",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (appt.depositPaid) SuccessEmerald else WarningAmber
                        )
                        if (appt.status in listOf("REQUESTED", "CONFIRMED")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!appt.depositPaid) {
                                    TextButton(onClick = {
                                        selectedApptForMtu = appt.id
                                        showMtuDialog = true
                                    }) { Text("Upload 150 AFN MTU Receipt") }
                                }
                                TextButton(onClick = { onCancelAppointment(appt.id) }) {
                                    Text(strings.cancel, color = ErrorCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Treatment Progress
        item { SectionHeaderCard(title = strings.treatmentProgressLabel, icon = Icons.Default.Healing) }
        if (treatments.isEmpty()) {
            item { EmptyInfoCard(strings.noRecordsYet) }
        } else {
            items(treatments, key = { it.id }) { tr ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr.treatmentName, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                            Text("${tr.completedPercentage}%", style = MaterialTheme.typography.titleMedium, color = SuccessEmerald, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { tr.completedPercentage / 100f },
                            color = RoyalBlue,
                            trackColor = SurfaceAlt,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text("${strings.currentStageLabel}: ${tr.currentStage}", style = MaterialTheme.typography.bodySmall, color = GoldDark)
                        Text("${strings.completedStepsLabel}: ${tr.completedSteps.size}/${tr.steps.size} • ${strings.pendingStepsLabel}: ${tr.pendingSteps.size}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    }
                }
            }
        }

        // Personal Prescriptions with PDF
        item { SectionHeaderCard(title = strings.prescriptionsMenu, icon = Icons.Default.Description) }
        if (prescriptions.isEmpty()) {
            item { EmptyInfoCard(strings.noRecordsYet) }
        } else {
            items(prescriptions, key = { it.id }) { rx ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${rx.doctorName} — ${rx.visitDate}", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                        rx.medicines.forEach { m ->
                            Text("• ${m.medicineName} (${m.dosage}) — ${m.frequency} • ${m.duration}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val file = PrescriptionPdfGenerator.generatePdfFile(
                                    context, rx, clinicProfile.nameEn, clinicProfile.nameFa,
                                    clinicProfile.phones.firstOrNull().orEmpty(), clinicProfile.addressEn
                                )
                                PrescriptionPdfGenerator.sharePdf(context, file)
                            }) { Text(strings.sharePdf) }
                            OutlinedButton(onClick = {
                                val file = PrescriptionPdfGenerator.generatePdfFile(
                                    context, rx, clinicProfile.nameEn, clinicProfile.nameFa,
                                    clinicProfile.phones.firstOrNull().orEmpty(), clinicProfile.addressEn
                                )
                                PrescriptionPdfGenerator.printPdf(context, file, "Sakha_Rx_${rx.id}")
                            }) { Text(strings.printPdf) }
                        }
                    }
                }
            }
        }

        // Personal MTU Submissions & Payment History
        item { SectionHeaderCard(title = strings.paymentsMenu, icon = Icons.Default.Payments) }
        if (mtuReceipts.isEmpty() && invoices.isEmpty() && payments.isEmpty()) {
            item { EmptyInfoCard(strings.noRecordsYet) }
        } else {
            items(mtuReceipts, key = { "mtu_${it.id}" }) { rcpt ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("MTU Receipt Submission", style = MaterialTheme.typography.titleSmall, color = RoyalBlue)
                            val statusText = when (rcpt.status) {
                                "PENDING_VERIFICATION" -> strings.pendingVerification
                                "APPROVED" -> "Approved (${CurrencyManager.formatAfn(rcpt.approvedAmountAfn, language)})"
                                else -> "Rejected: ${rcpt.rejectionReason}"
                            }
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (rcpt.status == "APPROVED") SuccessEmerald else if (rcpt.status == "REJECTED") ErrorCrimson else WarningAmber)
                        }
                        StatusPill(rcpt.status)
                    }
                }
            }
            items(invoices, key = { "inv_${it.id}" }) { inv ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Invoice (${inv.status})", style = MaterialTheme.typography.titleSmall, color = RoyalBlue)
                            Text("${strings.totalLabel}: ${CurrencyManager.formatAfn(inv.totalAfn, language)} • ${strings.paidLabel}: ${CurrencyManager.formatAfn(inv.paidAfn, language)} • ${strings.balanceLabel}: ${CurrencyManager.formatAfn(inv.balanceAfn, language)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showFeedbackDialog = true },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.RateReview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.feedbackMenu)
            }
        }
    }

    // --- DIALOGS ---
    if (showBookingDialog) {
        PatientBookingDialog(
            strings = strings,
            language = language,
            services = services,
            defaultName = userAccount?.displayName ?: linkedPatient?.fullName.orEmpty(),
            defaultPhone = userAccount?.phone ?: linkedPatient?.phone.orEmpty(),
            onDismiss = { showBookingDialog = false },
            onSubmit = { name, phone, srv, dateIso, slot, notes ->
                onBookAppointment(
                    ClinicBusinessRules.buildAppointmentDraft(
                        userId = userAccount?.userId.orEmpty(),
                        patientId = linkedPatient?.id.orEmpty(),
                        patientName = name,
                        patientPhone = phone,
                        serviceName = srv,
                        dateIso = dateIso,
                        timeSlot = slot,
                        notes = notes
                    )
                )
                showBookingDialog = false
            }
        )
    }

    if (showMtuDialog) {
        SimpleMtuUploadDialog(
            strings = strings,
            onDismiss = { showMtuDialog = false },
            onSubmitImage = { base64 ->
                onSubmitSimpleMtuReceipt(
                    linkedPatient?.id.orEmpty(),
                    userAccount?.displayName ?: linkedPatient?.fullName ?: "Patient",
                    selectedApptForMtu,
                    selectedInvoiceForMtu,
                    base64
                )
                showMtuDialog = false
            }
        )
    }

    if (showHesabPayDialog) {
        AlertDialog(
            onDismissRequest = { showHesabPayDialog = false },
            title = { Text(strings.hesabPayTitle, color = RoyalBlue, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(R.drawable.hesabpay_qr),
                        contentDescription = "HesabPay Clinic QR Code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, LightGray, RoundedCornerShape(14.dp))
                    )
                    Text("Scan with HesabPay app to pay your 150 AFN booking deposit or treatment invoice.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHesabPayDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showLinkRecordDialog) {
        var name by remember { mutableStateOf(userAccount?.displayName.orEmpty()) }
        var phone by remember { mutableStateOf(userAccount?.phone.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showLinkRecordDialog = false },
            title = { Text(strings.patientLinkingLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Administration will verify and approve linking your existing clinic file. Private data is never merged automatically.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) })
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && phone.isNotBlank(),
                    onClick = {
                        onSubmitLinkRequest(name, phone)
                        showLinkRecordDialog = false
                    }
                ) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showLinkRecordDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (showPartnerDialog) {
        var email by remember { mutableStateOf("") }
        var shareAppts by remember { mutableStateOf(true) }
        var shareProg by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPartnerDialog = false },
            title = { Text(strings.partnerLinkingLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Requires mutual consent from both accounts. You may revoke access at any time.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Partner Email") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareAppts, onCheckedChange = { shareAppts = it })
                        Text("Share appointment schedule")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareProg, onCheckedChange = { shareProg = it })
                        Text("Share treatment progress %")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = email.contains("@"),
                    onClick = {
                        onInvitePartner(email, shareAppts, shareProg)
                        showPartnerDialog = false
                    }
                ) { Text("Send Invitation") }
            },
            dismissButton = { TextButton(onClick = { showPartnerDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (showFeedbackDialog) {
        var kind by remember { mutableStateOf("FEEDBACK") }
        var msg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(strings.feedbackMenu) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("FEEDBACK", "COMPLAINT", "SUGGESTION").forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Message") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(enabled = msg.isNotBlank(), onClick = { onSubmitFeedback(kind, 5L, msg); showFeedbackDialog = false }) {
                    Text(strings.confirm)
                }
            },
            dismissButton = { TextButton(onClick = { showFeedbackDialog = false }) { Text(strings.cancel) } }
        )
    }
}

/**
 * ULTRA-SIMPLE MTU RECEIPT UPLOAD DIALOG (Section 19.1):
 * 1. One receipt upload option
 * 2. Pick one image from gallery (Photo Picker) or camera
 * 3. Submit image -> Status = Pending Verification
 * NO long form, NO sender name/phone/description inputs!
 */
@Composable
fun SimpleMtuUploadDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSubmitImage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedBase64 by remember { mutableStateOf<String?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedBase64 = ImageEncodingUtils.uriToCompressedBase64(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = tempCameraUri
        if (ok && uri != null) {
            selectedBase64 = ImageEncodingUtils.uriToCompressedBase64(context, uri)
        }
    }

    val previewBitmap = remember(selectedBase64) {
        selectedBase64?.let { ImageEncodingUtils.base64ToImageBitmap(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.mtuUploadTitle, color = RoyalBlue, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.mtuUploadInstruction, style = MaterialTheme.typography.bodySmall, color = SlateMuted)

                Button(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("mtu_gallery_picker_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.mtuSelectReceipt)
                }

                OutlinedButton(
                    onClick = {
                        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                        val file = File(dir, "mtu_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("mtu_camera_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.mtuTakePhoto)
                }

                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "Selected MTU receipt preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, GoldAccent, RoundedCornerShape(12.dp))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !selectedBase64.isNullOrBlank(),
                onClick = { selectedBase64?.let(onSubmitImage) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = RoyalBlueDark),
                modifier = Modifier.testTag("mtu_submit_receipt_button")
            ) {
                Text(strings.mtuSubmitReceipt, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun PatientBookingDialog(
    strings: AppStrings,
    language: AppLanguage,
    services: List<PublicService>,
    defaultName: String,
    defaultPhone: String,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String, service: String, dateIso: String, slot: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var phone by remember { mutableStateOf(defaultPhone) }
    var dateIso by remember { mutableStateOf(ClinicCalendar.todayIso()) }
    val slots = remember(language) { ClinicCalendar.generateDefaultTimeSlots(language) }
    var selectedSlot by remember { mutableStateOf(slots.firstOrNull() ?: "9:00 AM") }
    var selectedService by remember { mutableStateOf(services.firstOrNull()?.localizedName(language.code) ?: "Consultation") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.bookAppointment, color = RoyalBlue, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = GoldAccentSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(strings.bookingDepositBanner, style = MaterialTheme.typography.labelLarge, color = RoyalBlue, fontWeight = FontWeight.Bold)
                        Text(strings.latePolicyNotice, style = MaterialTheme.typography.labelSmall, color = RoyalBlueDark)
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Patient Name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) }, singleLine = true)
                OutlinedTextField(value = dateIso, onValueChange = { dateIso = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                Text("Solar Hijri: ${ClinicCalendar.fromIsoDate(dateIso).formatLocalized(language)}", style = MaterialTheme.typography.labelSmall, color = GoldDark)
                OutlinedTextField(value = selectedService, onValueChange = { selectedService = it }, label = { Text(strings.ourServicesMenu) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.take(3).forEach { s ->
                        FilterChip(selected = selectedSlot == s, onClick = { selectedSlot = s }, label = { Text(s, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                onClick = { onSubmit(name, phone, selectedService, dateIso, selectedSlot, notes) }
            ) { Text(strings.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@Composable
fun StatusPill(status: String) {
    val bg = when (status) {
        "CONFIRMED", "APPROVED", "PAID", "COMPLETED" -> SuccessContainer
        "PENDING_VERIFICATION", "REQUESTED", "PARTIAL", "IN_PROGRESS" -> WarningContainer
        "REJECTED", "CANCELLED", "NO_SHOW", "VOID" -> ErrorContainer
        else -> SurfaceAlt
    }
    val fg = when (status) {
        "CONFIRMED", "APPROVED", "PAID", "COMPLETED" -> SuccessEmerald
        "PENDING_VERIFICATION", "REQUESTED", "PARTIAL", "IN_PROGRESS" -> WarningAmber
        "REJECTED", "CANCELLED", "NO_SHOW", "VOID" -> ErrorCrimson
        else -> RoyalBlue
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = status,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyInfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = SlateMuted)
        }
    }
}
