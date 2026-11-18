package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.*
import com.example.domain.*
import com.example.ui.components.ImageEncodingUtils
import com.example.ui.components.SectionHeaderCard
import com.example.ui.components.ZoomableReceiptDialog
import com.example.ui.theme.*

// ==================== STAFF APPOINTMENTS ====================

@Composable
fun AppointmentsStaffScreen(
    strings: AppStrings,
    language: AppLanguage,
    calendarMode: CalendarMode,
    appointments: List<AppointmentRecord>,
    patients: List<PatientRecord>,
    services: List<PublicService>,
    settings: ClinicSettings,
    onSaveAppointment: (AppointmentRecord) -> Unit
) {
    var filterMode by remember { mutableStateOf("ALL") } // ALL, TODAY, ACTUAL
    var showCreateDialog by remember { mutableStateOf(false) }
    val today = remember { ClinicCalendar.todayIso() }

    val filtered = remember(appointments, filterMode, today) {
        when (filterMode) {
            "TODAY" -> appointments.filter { it.dateIso == today }
            "ACTUAL" -> appointments.filter { it.status in listOf("CHECKED_IN", "COMPLETED") }
            else -> appointments
        }.sortedByDescending { it.dateIso }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeaderCard(
                title = strings.appointmentsMenu,
                subtitle = "Booked: ${appointments.size} • Actual Visits: ${ClinicBusinessRules.countActualVisits(appointments)}",
                icon = Icons.Default.EventAvailable
            )
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("staff_add_appointment_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.addNew)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = filterMode == "ALL", onClick = { filterMode = "ALL" }, label = { Text("All (${appointments.size})") })
            FilterChip(selected = filterMode == "TODAY", onClick = { filterMode = "TODAY" }, label = { Text("Today") })
            FilterChip(selected = filterMode == "ACTUAL", onClick = { filterMode = "ACTUAL" }, label = { Text("Actual Visits") })
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) {
                item { EmptyInfoCard(strings.noRecordsYet) }
            } else {
                items(filtered, key = { it.id }) { appt ->
                    Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${appt.patientName} • ${appt.serviceName}", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${ClinicCalendar.formatDate(appt.dateIso, calendarMode, language)} • ${appt.timeSlot}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                                StatusPill(appt.status)
                            }
                            Text(
                                text = "Visit Type: ${appt.visitType} • Deposit: ${if (appt.depositPaid) "Paid" else "${appt.depositRequiredAfn.toInt()} AFN Pending"} • Late policy: ${appt.latePolicyMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldDark
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                if (appt.status == "REQUESTED") {
                                    FilledTonalButton(onClick = { onSaveAppointment(appt.copy(status = "CONFIRMED")) }) { Text("Confirm") }
                                }
                                if (appt.status in listOf("REQUESTED", "CONFIRMED")) {
                                    Button(
                                        onClick = { onSaveAppointment(appt.copy(status = "CHECKED_IN", visitType = "ACTUAL_VISIT")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = PureWhite)
                                    ) { Text("Check In") }
                                }
                                if (appt.status == "CHECKED_IN") {
                                    Button(
                                        onClick = { onSaveAppointment(appt.copy(status = "COMPLETED", visitType = "COMPLETED_TREATMENT")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite)
                                    ) { Text("Complete") }
                                }
                                if (appt.status !in listOf("CANCELLED", "COMPLETED", "NO_SHOW")) {
                                    TextButton(onClick = { onSaveAppointment(appt.copy(status = "NO_SHOW", visitType = "CANCELLED")) }) {
                                        Text("No Show", color = ErrorCrimson)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var selectedPatient by remember { mutableStateOf(patients.firstOrNull { !it.archived }) }
        var patientName by remember { mutableStateOf(selectedPatient?.fullName ?: "") }
        var phone by remember { mutableStateOf(selectedPatient?.phone ?: "") }
        var selectedService by remember { mutableStateOf(services.firstOrNull()?.localizedName("en") ?: "Consultation") }
        var dateIso by remember { mutableStateOf(ClinicCalendar.todayIso()) }
        var timeSlot by remember { mutableStateOf(ClinicCalendar.generateDefaultTimeSlots(language).first()) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(strings.bookAppointment) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = patientName, onValueChange = { patientName = it }, label = { Text("Patient Name") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) }, singleLine = true)
                    OutlinedTextField(value = selectedService, onValueChange = { selectedService = it }, label = { Text(strings.ourServicesMenu) }, singleLine = true)
                    OutlinedTextField(value = dateIso, onValueChange = { dateIso = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(value = timeSlot, onValueChange = { timeSlot = it }, label = { Text("Time Slot (e.g. 10:00 AM)") }, singleLine = true)
                    Text(
                        "Solar Hijri: ${ClinicCalendar.fromIsoDate(dateIso).formatLocalized(language)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldDark
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (patientName.isNotBlank()) {
                            onSaveAppointment(
                                ClinicBusinessRules.buildAppointmentDraft(
                                    userId = selectedPatient?.linkedUserUid ?: "",
                                    patientId = selectedPatient?.id ?: "",
                                    patientName = patientName,
                                    patientPhone = phone,
                                    serviceName = selectedService,
                                    dateIso = dateIso,
                                    timeSlot = timeSlot,
                                    depositRequiredAfn = settings.bookingDepositAfn,
                                    latePolicyMinutes = settings.lateNoShowMinutes
                                ).copy(status = "CONFIRMED")
                            )
                            showCreateDialog = false
                        }
                    }
                ) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(strings.cancel) } }
        )
    }
}

// ==================== FINANCE, MTU VERIFICATION, SUPPLIERS & PAYROLL ====================

@Composable
fun FinanceAndMtuScreen(
    strings: AppStrings,
    language: AppLanguage,
    effectiveRole: UserRole,
    settings: ClinicSettings,
    patients: List<PatientRecord>,
    mtuReceipts: List<MtuReceiptSubmission>,
    payments: List<PaymentRecord>,
    invoices: List<InvoiceRecord>,
    expenses: List<ExpenseRecord>,
    suppliers: List<SupplierRecord>,
    payroll: List<PayrollRecord>,
    onApproveMtu: (MtuReceiptSubmission, Double) -> Unit,
    onRejectMtu: (MtuReceiptSubmission, String) -> Unit,
    onCreateInvoice: (patientId: String, userId: String, name: String, subtotal: Double, discount: Double) -> Unit,
    onRecordDirectPayment: (patientId: String, userId: String, invoiceId: String, amount: Double, currency: String, rate: Double, method: String) -> Unit,
    onAddExpense: (title: String, category: String, amount: Double, currency: String, rate: Double, notes: String) -> Unit,
    onVoidExpense: (expenseId: String, reason: String) -> Unit,
    onSaveSupplier: (SupplierRecord) -> Unit,
    onSavePayroll: (PayrollRecord) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add("MTU Verify (${mtuReceipts.count { it.status == "PENDING_VERIFICATION" }})")
        add("Payments & Invoices")
        if (effectiveRole.isAdminOrAbove) {
            add("Expenses")
            add("Suppliers")
        }
        if (effectiveRole.isSeniorAdminOrAbove) {
            add("Payroll")
        }
    }

    var zoomedReceipt by remember { mutableStateOf<MtuReceiptSubmission?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(SoftWhite)) {
        ScrollableTabRow(selectedTabIndex = tabIndex.coerceAtMost(tabs.lastIndex), containerColor = PureWhite, contentColor = RoyalBlue) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = tabIndex == idx, onClick = { tabIndex = idx }, text = { Text(label, fontWeight = FontWeight.SemiBold) })
            }
        }

        when (tabIndex) {
            0 -> MtuVerificationTab(
                strings = strings,
                effectiveRole = effectiveRole,
                receipts = mtuReceipts,
                onInspectReceipt = { zoomedReceipt = it },
                onApproveMtu = onApproveMtu,
                onRejectMtu = onRejectMtu
            )
            1 -> PaymentsAndInvoicesTab(
                strings = strings,
                language = language,
                settings = settings,
                patients = patients,
                invoices = invoices,
                payments = payments,
                onCreateInvoice = onCreateInvoice,
                onRecordDirectPayment = onRecordDirectPayment
            )
            2 -> ExpensesTab(
                strings = strings,
                language = language,
                canVoid = effectiveRole.isSeniorAdminOrAbove,
                settings = settings,
                expenses = expenses,
                onAddExpense = onAddExpense,
                onVoidExpense = onVoidExpense
            )
            3 -> SuppliersTab(strings, language, suppliers, onSaveSupplier)
            4 -> PayrollTab(strings, language, payroll, onSavePayroll)
        }
    }

    zoomedReceipt?.let { rec ->
        ZoomableReceiptDialog(
            imageBase64 = rec.receiptImageBase64,
            patientName = rec.patientName,
            onDismiss = { zoomedReceipt = null }
        )
    }
}

@Composable
private fun MtuVerificationTab(
    strings: AppStrings,
    effectiveRole: UserRole,
    receipts: List<MtuReceiptSubmission>,
    onInspectReceipt: (MtuReceiptSubmission) -> Unit,
    onApproveMtu: (MtuReceiptSubmission, Double) -> Unit,
    onRejectMtu: (MtuReceiptSubmission, String) -> Unit
) {
    var approving by remember { mutableStateOf<MtuReceiptSubmission?>(null) }
    var rejecting by remember { mutableStateOf<MtuReceiptSubmission?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = GoldAccentSoft), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Strict Accounting Policy: Pending or Rejected MTU receipts NEVER count as clinic income or reduce balances. Tap any receipt image to open full-size zoom for clinic phone verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoyalBlueDark,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (receipts.isEmpty()) {
            item { EmptyInfoCard(strings.noRecordsYet) }
        } else {
            items(receipts.sortedByDescending { it.status == "PENDING_VERIFICATION" }, key = { it.id }) { rec ->
                val bmp = remember(rec.receiptImageBase64) { ImageEncodingUtils.base64ToImageBitmap(rec.receiptImageBase64) }
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(rec.patientName.ifBlank { "Patient" }, style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                                Text("Receipt ID: ${rec.id.take(8)} • Invoice: ${rec.invoiceId.ifBlank { "Deposit" }}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            }
                            StatusPill(rec.status)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "MTU receipt thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onInspectReceipt(rec) }
                            )
                        }
                        if (rec.status == "APPROVED") {
                            Text("Verified Amount: ${rec.approvedAmountAfn.toInt()} AFN", color = SuccessEmerald, fontWeight = FontWeight.Bold)
                        } else if (rec.status == "REJECTED") {
                            Text("Rejection Reason: ${rec.rejectionReason}", color = ErrorCrimson, style = MaterialTheme.typography.bodySmall)
                        }
                        if (rec.status == "PENDING_VERIFICATION" && effectiveRole.isAdminOrAbove) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { approving = rec },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = PureWhite),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("approve_mtu_${rec.id}")
                                ) {
                                    Text(strings.approvePayment)
                                }
                                OutlinedButton(
                                    onClick = { rejecting = rec },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("reject_mtu_${rec.id}")
                                ) {
                                    Text(strings.rejectPayment, color = ErrorCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    approving?.let { rec ->
        var amountInput by remember { mutableStateOf("150") }
        AlertDialog(
            onDismissRequest = { approving = null },
            title = { Text(strings.approvePayment) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confirm verified AFN amount received on clinic phone for ${rec.patientName}:")
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Verified Amount (AFN)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountInput.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            onApproveMtu(rec, amt)
                            approving = null
                        }
                    }
                ) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { approving = null }) { Text(strings.cancel) } }
        )
    }

    rejecting?.let { rec ->
        var reasonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text(strings.rejectPayment) },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text(strings.rejectionReasonLabel) },
                    minLines = 2
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectMtu(rec, reasonInput)
                        rejecting = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson, contentColor = PureWhite)
                ) { Text(strings.rejectPayment) }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun PaymentsAndInvoicesTab(
    strings: AppStrings,
    language: AppLanguage,
    settings: ClinicSettings,
    patients: List<PatientRecord>,
    invoices: List<InvoiceRecord>,
    payments: List<PaymentRecord>,
    onCreateInvoice: (String, String, String, Double, Double) -> Unit,
    onRecordDirectPayment: (String, String, String, Double, String, Double, String) -> Unit
) {
    var showNewInvoice by remember { mutableStateOf(false) }
    var payingInvoice by remember { mutableStateOf<InvoiceRecord?>(null) }
    var showHesabQr by remember { mutableStateOf(false) }

    val confirmedTotal = remember(payments) { ClinicBusinessRules.calculateConfirmedIncomeAfn(payments) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = RoyalBlue), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confirmed Clinic Revenue: ${CurrencyManager.formatAfn(confirmedTotal, language)}", color = GoldAccent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showNewInvoice = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = RoyalBlueDark)
                        ) { Text("New Invoice") }
                        OutlinedButton(onClick = { showHesabQr = true }) {
                            Text(strings.hesabPayTitle, color = PureWhite)
                        }
                    }
                }
            }
        }
        if (invoices.isEmpty()) {
            item { EmptyInfoCard(strings.noRecordsYet) }
        } else {
            items(invoices, key = { it.id }) { inv ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(inv.patientName.ifBlank { "Patient" }, fontWeight = FontWeight.Bold, color = RoyalBlue)
                            StatusPill(inv.status)
                        }
                        Text(
                            "${strings.totalLabel}: ${CurrencyManager.formatAfn(inv.totalAfn, language)} • ${strings.paidLabel}: ${CurrencyManager.formatAfn(inv.paidAfn, language)} • ${strings.balanceLabel}: ${CurrencyManager.formatAfn(inv.balanceAfn, language)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                        if (inv.balanceAfn > 0.01) {
                            Button(
                                onClick = { payingInvoice = inv },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text("Record Cash / HesabPay")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewInvoice) {
        val firstPatient = patients.firstOrNull { !it.archived }
        var pName by remember { mutableStateOf(firstPatient?.fullName ?: "") }
        var subtotal by remember { mutableStateOf("") }
        var discount by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showNewInvoice = false },
            title = { Text("Create Invoice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Patient Name") }, singleLine = true)
                    OutlinedTextField(value = subtotal, onValueChange = { subtotal = it }, label = { Text("Subtotal (AFN)") }, singleLine = true)
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount (AFN)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sub = subtotal.toDoubleOrNull() ?: 0.0
                    val disc = discount.toDoubleOrNull() ?: 0.0
                    if (pName.isNotBlank() && sub > 0.0) {
                        onCreateInvoice(firstPatient?.id ?: "", firstPatient?.linkedUserUid ?: "", pName, sub, disc)
                        showNewInvoice = false
                    }
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showNewInvoice = false }) { Text(strings.cancel) } }
        )
    }

    payingInvoice?.let { inv ->
        var amount by remember { mutableStateOf(inv.balanceAfn.toInt().toString()) }
        var selectedCurrency by remember { mutableStateOf(ClinicCurrency.AFN) }
        var method by remember { mutableStateOf("CASH") }
        val rate = settings.currencyRates[selectedCurrency.code] ?: selectedCurrency.defaultRateToAfn
        AlertDialog(
            onDismissRequest = { payingInvoice = null },
            title = { Text("Record Payment (${inv.patientName})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH", "HESABPAY").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ClinicCurrency.entries.forEach { cur ->
                            FilterChip(selected = selectedCurrency == cur, onClick = { selectedCurrency = cur }, label = { Text(cur.code) })
                        }
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (${selectedCurrency.code})") }, singleLine = true)
                    val converted = CurrencyManager.convertToAfn(amount.toDoubleOrNull() ?: 0.0, rate)
                    Text("Rate: 1 ${selectedCurrency.code} = $rate AFN -> Credited: ${CurrencyManager.formatAfn(converted, language)}", style = MaterialTheme.typography.labelSmall, color = GoldDark)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onRecordDirectPayment(inv.patientId, inv.userId, inv.id, amt, selectedCurrency.code, rate, method)
                        payingInvoice = null
                    }
                }) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { payingInvoice = null }) { Text(strings.cancel) } }
        )
    }

    if (showHesabQr) {
        AlertDialog(
            onDismissRequest = { showHesabQr = false },
            title = { Text(strings.hesabPayTitle) },
            text = {
                Image(
                    painter = painterResource(R.drawable.hesabpay_qr),
                    contentDescription = "HesabPay QR Code",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            },
            confirmButton = { TextButton(onClick = { showHesabQr = false }) { Text(strings.close) } }
        )
    }
}

@Composable
private fun ExpensesTab(
    strings: AppStrings,
    language: AppLanguage,
    canVoid: Boolean,
    settings: ClinicSettings,
    expenses: List<ExpenseRecord>,
    onAddExpense: (String, String, Double, String, Double, String) -> Unit,
    onVoidExpense: (String, String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var voiding by remember { mutableStateOf<ExpenseRecord?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${strings.expensesLabel}: ${CurrencyManager.formatAfn(ClinicBusinessRules.calculateActiveExpensesAfn(expenses), language)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = RoyalBlue,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { showAdd = true }) { Text(strings.addNew) }
            }
        }
        items(expenses, key = { it.id }) { exp ->
            Card(colors = CardDefaults.cardColors(containerColor = if (exp.voided) SurfaceAlt else PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(exp.title, fontWeight = FontWeight.Bold, color = if (exp.voided) SlateMuted else RoyalBlue)
                        Text(CurrencyManager.formatAfn(exp.amountAfn, language), color = if (exp.voided) SlateMuted else ErrorCrimson, fontWeight = FontWeight.Bold)
                    }
                    Text("${exp.category} • ${exp.originalAmount} ${exp.currency} (rate ${exp.exchangeRate})", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    if (exp.voided) {
                        Text("VOIDED: ${exp.voidReason}", color = ErrorCrimson, style = MaterialTheme.typography.labelSmall)
                    } else if (canVoid) {
                        TextButton(onClick = { voiding = exp }) { Text("Soft-Void Expense", color = ErrorCrimson) }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Dental Materials") }
        var amount by remember { mutableStateOf("") }
        var selectedCurrency by remember { mutableStateOf(ClinicCurrency.AFN) }
        var notes by remember { mutableStateOf("") }
        val rate = settings.currencyRates[selectedCurrency.code] ?: selectedCurrency.defaultRateToAfn
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Clinic Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ClinicCurrency.entries.forEach { cur ->
                            FilterChip(selected = selectedCurrency == cur, onClick = { selectedCurrency = cur }, label = { Text(cur.code) })
                        }
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (${selectedCurrency.code})") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0.0) {
                        onAddExpense(title, category, amt, selectedCurrency.code, rate, notes)
                        showAdd = false
                    }
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(strings.cancel) } }
        )
    }

    voiding?.let { exp ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voiding = null },
            title = { Text("Confirm Expense Void & Audit") },
            text = {
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Audit Reason") })
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            onVoidExpense(exp.id, reason)
                            voiding = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson, contentColor = PureWhite)
                ) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { voiding = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun SuppliersTab(
    strings: AppStrings,
    language: AppLanguage,
    suppliers: List<SupplierRecord>,
    onSaveSupplier: (SupplierRecord) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.suppliersDebtLabel, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                Button(onClick = { showAdd = true }) { Text(strings.addNew) }
            }
        }
        items(suppliers, key = { it.id }) { s ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, fontWeight = FontWeight.Bold, color = RoyalBlue)
                        if (s.overdue) StatusPill("OVERDUE")
                    }
                    Text("📞 ${s.phone} • ${s.purchaseSummary}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    Text("Debt: ${CurrencyManager.formatAfn(s.totalDebtAfn, language)} • Remaining: ${CurrencyManager.formatAfn(s.remainingBalanceAfn, language)}", style = MaterialTheme.typography.labelMedium, color = GoldDark)
                }
            }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var debt by remember { mutableStateOf("") }
        var paid by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Supplier / Debt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) }, singleLine = true)
                    OutlinedTextField(value = debt, onValueChange = { debt = it }, label = { Text("Total Debt (AFN)") }, singleLine = true)
                    OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text("Paid (AFN)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val d = debt.toDoubleOrNull() ?: 0.0
                    val p = paid.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        onSaveSupplier(SupplierRecord(name = name, phone = phone, totalDebtAfn = d, paidAfn = p, remainingBalanceAfn = (d - p).coerceAtLeast(0.0)))
                        showAdd = false
                    }
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun PayrollTab(
    strings: AppStrings,
    language: AppLanguage,
    payroll: List<PayrollRecord>,
    onSavePayroll: (PayrollRecord) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.payrollLabel, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                Button(onClick = { showAdd = true }) { Text(strings.addNew) }
            }
        }
        items(payroll, key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${p.employeeName} (${p.roleTitle})", fontWeight = FontWeight.Bold, color = RoyalBlue)
                        StatusPill(p.status)
                    }
                    Text("Net Paid: ${CurrencyManager.formatAfn(p.netPaidAfn, language)} • ${p.periodLabel}", style = MaterialTheme.typography.bodySmall, color = GoldDark)
                }
            }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var roleTitle by remember { mutableStateOf("Receptionist") }
        var salary by remember { mutableStateOf("") }
        var period by remember { mutableStateOf("Current Month") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Payroll Record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Employee Name") }, singleLine = true)
                    OutlinedTextField(value = roleTitle, onValueChange = { roleTitle = it }, label = { Text("Role") }, singleLine = true)
                    OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("Base Salary (AFN)") }, singleLine = true)
                    OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sal = salary.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && sal > 0.0) {
                        onSavePayroll(PayrollRecord(employeeName = name, roleTitle = roleTitle, baseSalaryAfn = sal, netPaidAfn = sal, periodLabel = period, status = "PAID", paidDate = ClinicCalendar.todayIso()))
                        showAdd = false
                    }
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(strings.cancel) } }
        )
    }
}

// ==================== ANALYTICS & APP USAGE STATS ====================

@Composable
fun AnalyticsScreen(
    strings: AppStrings,
    language: AppLanguage,
    patients: List<PatientRecord>,
    appointments: List<AppointmentRecord>,
    treatments: List<TreatmentPlan>,
    payments: List<PaymentRecord>,
    expenses: List<ExpenseRecord>,
    invoices: List<InvoiceRecord>,
    suppliers: List<SupplierRecord>,
    medicines: List<MedicineItem>,
    staffRoles: List<StaffRoleRecord>,
    lastSyncIso: String
) {
    val income = remember(payments) { ClinicBusinessRules.calculateConfirmedIncomeAfn(payments) }
    val exp = remember(expenses) { ClinicBusinessRules.calculateActiveExpensesAfn(expenses) }
    val net = income - exp
    val actualVisits = remember(appointments) { ClinicBusinessRules.countActualVisits(appointments) }
    val supplierDebt = remember(suppliers) { suppliers.sumOf { it.remainingBalanceAfn } }
    val lowStock = remember(medicines) { medicines.count { it.quantity <= it.minStockLevel } }
    val avgProgress = remember(treatments) {
        if (treatments.isEmpty()) 0 else treatments.sumOf { it.completedPercentage } / treatments.size
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeaderCard(title = strings.analyticsMenu, subtitle = "Real-time clinic KPIs & accounting summary", icon = Icons.Default.Assessment)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard(Modifier.weight(1f), strings.incomeLabel, CurrencyManager.formatAfn(income, language), SuccessEmerald)
                KpiMetricCard(Modifier.weight(1f), strings.expensesLabel, CurrencyManager.formatAfn(exp, language), ErrorCrimson)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard(Modifier.weight(1f), strings.netIncomeLabel, CurrencyManager.formatAfn(net, language), RoyalBlue)
                KpiMetricCard(Modifier.weight(1f), "Supplier Debt", CurrencyManager.formatAfn(supplierDebt, language), GoldDark)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Financial Overview Chart (AFN)", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    val maxVal = maxOf(income, exp, kotlin.math.abs(net), 1.0).toFloat()
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        val barWidth = size.width / 6f
                        val spacing = size.width / 9f
                        val bars = listOf(
                            Triple(income.toFloat(), Color(0xFF1B8A5A), spacing),
                            Triple(exp.toFloat(), Color(0xFFC62828), spacing * 2f + barWidth),
                            Triple(net.coerceAtLeast(0.0).toFloat(), Color(0xFF0A2443), spacing * 3f + barWidth * 2f)
                        )
                        bars.forEach { (value, color, xOffset) ->
                            val barHeight = (value / maxVal) * (size.height - 20f)
                            drawRect(
                                color = color,
                                topLeft = Offset(xOffset, size.height - barHeight),
                                size = Size(barWidth, barHeight)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("Income", style = MaterialTheme.typography.labelSmall, color = SuccessEmerald)
                        Text("Expenses", style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                        Text("Net", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Clinical & Operations Metrics", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Text("• Active Patients: ${patients.count { !it.archived }} (Archived: ${patients.count { it.archived }})")
                    Text("• Booked Appointments: ${appointments.size} vs Actual Visits: $actualVisits")
                    Text("• Active Treatments: ${treatments.size} • Avg Progress: $avgProgress%")
                    Text("• Invoices: Paid ${invoices.count { it.status == "PAID" }} • Partial ${invoices.count { it.status == "PARTIAL" }} • Unpaid ${invoices.count { it.status == "UNPAID" }}")
                    Text("• Low-Stock Medicine Alerts: $lowStock")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.usageStatsLabel, style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Text("• Registered Staff Accounts: ${staffRoles.size} (Active: ${staffRoles.count { it.active }})")
                    Text("• Linked Patient Portal Accounts: ${patients.count { !it.linkedUserUid.isNullOrBlank() }}")
                    Text("• Last Sync Timestamp: ${lastSyncIso.ifBlank { "Realtime Cloud Sync Active" }}")
                    Text("Note: Verified from backend user & staff account records (excluding external app-store install counters).", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SlateMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ==================== SUPER ADMIN CONTROL PANEL & CMS ====================

@Composable
fun SuperAdminControlScreen(
    strings: AppStrings,
    actualRole: UserRole,
    previewRole: UserRole?,
    clinicProfile: PublicClinicProfile,
    doctorProfile: PublicDoctorProfile,
    services: List<PublicService>,
    internalPricing: List<InternalServicePricing>,
    staffRoles: List<StaffRoleRecord>,
    linkRequests: List<PatientLinkRequest>,
    patients: List<PatientRecord>,
    feedbackList: List<FeedbackComplaintItem>,
    settings: ClinicSettings,
    backups: List<BackupSnapshot>,
    auditLogs: List<AuditLogEntry>,
    onSetRolePreview: (UserRole?) -> Unit,
    onSaveClinicProfile: (PublicClinicProfile) -> Unit,
    onSaveDoctorProfile: (PublicDoctorProfile) -> Unit,
    onSavePublicService: (PublicService) -> Unit,
    onDeletePublicService: (String) -> Unit,
    onSaveInternalPricing: (InternalServicePricing) -> Unit,
    onSaveStaffRole: (uid: String, email: String, name: String, role: UserRole, active: Boolean) -> Unit,
    onReviewLinkRequest: (reqId: String, patientId: String?, approve: Boolean, note: String) -> Unit,
    onRespondFeedback: (itemId: String, response: String, status: String) -> Unit,
    onSaveSettings: (ClinicSettings) -> Unit,
    onCreateBackup: (String) -> Unit,
    onFlushOfflineSync: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview & Preview", "Clinic/Web CMS", "Doctor CMS", "Services & Pricing", "Users & Links", "Feedback", "Backup & Audit")

    Column(modifier = Modifier.fillMaxSize().background(SoftWhite).testTag("super_admin_screen")) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = PureWhite, contentColor = RoyalBlue) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = tab == idx, onClick = { tab = idx }, text = { Text(label, fontWeight = FontWeight.SemiBold) })
            }
        }

        when (tab) {
            0 -> SuperAdminOverviewTab(strings, actualRole, previewRole, settings, onSetRolePreview, onSaveSettings)
            1 -> ClinicWebsiteCmsTab(strings, clinicProfile, onSaveClinicProfile)
            2 -> DoctorProfileCmsTab(strings, doctorProfile, onSaveDoctorProfile)
            3 -> ServicesAndPricingCmsTab(strings, services, internalPricing, onSavePublicService, onDeletePublicService, onSaveInternalPricing)
            4 -> UsersAndLinksCmsTab(staffRoles, linkRequests, patients, onSaveStaffRole, onReviewLinkRequest)
            5 -> FeedbackAdminTab(feedbackList, onRespondFeedback)
            6 -> BackupAndAuditTab(strings, backups, auditLogs, onCreateBackup, onFlushOfflineSync)
        }
    }
}

@Composable
private fun SuperAdminOverviewTab(
    strings: AppStrings,
    actualRole: UserRole,
    previewRole: UserRole?,
    settings: ClinicSettings,
    onSetRolePreview: (UserRole?) -> Unit,
    onSaveSettings: (ClinicSettings) -> Unit
) {
    var bridgePrice by remember(settings) { mutableStateOf(settings.bridgeDefaultPriceAfn.toInt().toString()) }
    var depositAfn by remember(settings) { mutableStateOf(settings.bookingDepositAfn.toInt().toString()) }
    var lateMinutes by remember(settings) { mutableStateOf(settings.lateNoShowMinutes.toString()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (actualRole == UserRole.SUPER_ADMIN) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = RoyalBlue), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Role Preview Mode (Safe Inspection)", style = MaterialTheme.typography.titleMedium, color = PureWhite, fontWeight = FontWeight.Bold)
                        Text("Inspect any portal without changing your real Super Admin permissions.", style = MaterialTheme.typography.bodySmall, color = GoldAccentSoft)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(UserRole.GUEST, UserRole.PATIENT, UserRole.DOCTOR, UserRole.RECEPTIONIST, UserRole.ADMIN).forEach { r ->
                                FilterChip(
                                    selected = previewRole == r,
                                    onClick = { onSetRolePreview(if (previewRole == r) null else r) },
                                    label = { Text(r.name, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("preview_role_${r.name}")
                                )
                            }
                        }
                        if (previewRole != null) {
                            OutlinedButton(onClick = { onSetRolePreview(null) }) { Text(strings.exitPreview, color = GoldAccentSoft) }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.settingsMenu, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = bridgePrice, onValueChange = { bridgePrice = it }, label = { Text("Bridge Unit Price (AFN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = depositAfn, onValueChange = { depositAfn = it }, label = { Text("Booking Deposit (AFN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = lateMinutes, onValueChange = { lateMinutes = it }, label = { Text("Late Arrival Threshold (Minutes)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onSaveSettings(
                                settings.copy(
                                    bridgeDefaultPriceAfn = bridgePrice.toDoubleOrNull() ?: 10000.0,
                                    bookingDepositAfn = depositAfn.toDoubleOrNull() ?: 150.0,
                                    lateNoShowMinutes = lateMinutes.toLongOrNull() ?: 30L
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite)
                    ) { Text(strings.saveChanges) }
                }
            }
        }
    }
}

@Composable
private fun ClinicWebsiteCmsTab(
    strings: AppStrings,
    clinic: PublicClinicProfile,
    onSave: (PublicClinicProfile) -> Unit
) {
    var motto by remember(clinic) { mutableStateOf(clinic.motto) }
    var addressFa by remember(clinic) { mutableStateOf(clinic.addressFa) }
    var hoursFa by remember(clinic) { mutableStateOf(clinic.workingHoursFa) }
    var annTitle by remember(clinic) { mutableStateOf(clinic.announcementTitle) }
    var annBody by remember(clinic) { mutableStateOf(clinic.announcementBody) }
    var published by remember(clinic) { mutableStateOf(clinic.websitePublished) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.websiteMenu, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = motto, onValueChange = { motto = it }, label = { Text("Motto") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = addressFa, onValueChange = { addressFa = it }, label = { Text("Address (Dari)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = hoursFa, onValueChange = { hoursFa = it }, label = { Text("Working Hours (Dari)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = annTitle, onValueChange = { annTitle = it }, label = { Text("Announcement Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = annBody, onValueChange = { annBody = it }, label = { Text("Announcement Body") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Website Published Live")
                        Switch(checked = published, onCheckedChange = { published = it })
                    }
                    Button(
                        onClick = {
                            onSave(
                                clinic.copy(
                                    motto = motto,
                                    addressFa = addressFa,
                                    workingHoursFa = hoursFa,
                                    announcementTitle = annTitle,
                                    announcementBody = annBody,
                                    websitePublished = published
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite)
                    ) { Text(strings.saveChanges) }
                }
            }
        }
    }
}

@Composable
private fun DoctorProfileCmsTab(
    strings: AppStrings,
    doctor: PublicDoctorProfile,
    onSave: (PublicDoctorProfile) -> Unit
) {
    var bioFa by remember(doctor) { mutableStateOf(doctor.bioFa) }
    var bioEn by remember(doctor) { mutableStateOf(doctor.bioEn) }
    var bioPs by remember(doctor) { mutableStateOf(doctor.bioPs) }
    var newAchievement by remember { mutableStateOf("") }
    var achievements by remember(doctor) { mutableStateOf(doctor.achievements) }
    var published by remember(doctor) { mutableStateOf(doctor.published) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${doctor.fullNameFa} (v${doctor.version})", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = bioFa, onValueChange = { bioFa = it }, label = { Text("Biography (Dari)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bioPs, onValueChange = { bioPs = it }, label = { Text("Biography (Pashto)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bioEn, onValueChange = { bioEn = it }, label = { Text("Biography (English)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = newAchievement, onValueChange = { newAchievement = it }, label = { Text("Add Achievement") }, modifier = Modifier.weight(1f), singleLine = true)
                        Button(onClick = {
                            if (newAchievement.isNotBlank()) {
                                achievements = achievements + newAchievement.trim()
                                newAchievement = ""
                            }
                        }) { Text("+") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Published to Guest & Patient Portals")
                        Switch(checked = published, onCheckedChange = { published = it })
                    }
                    Button(
                        onClick = {
                            onSave(doctor.copy(bioFa = bioFa, bioPs = bioPs, bioEn = bioEn, achievements = achievements, published = published))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite)
                    ) { Text(strings.saveChanges) }
                }
            }
        }
    }
}

@Composable
private fun ServicesAndPricingCmsTab(
    strings: AppStrings,
    services: List<PublicService>,
    pricing: List<InternalServicePricing>,
    onSavePublicService: (PublicService) -> Unit,
    onDeletePublicService: (String) -> Unit,
    onSaveInternalPricing: (InternalServicePricing) -> Unit
) {
    var showAddService by remember { mutableStateOf(false) }
    var showEditPrice by remember { mutableStateOf<InternalServicePricing?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Public Services (ZERO Prices)", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                Button(onClick = { showAddService = true }) { Text(strings.addNew) }
            }
        }
        items(services, key = { it.id }) { srv ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(srv.nameFa, fontWeight = FontWeight.Bold, color = RoyalBlue)
                        Text("${srv.nameEn} • ${srv.category}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    }
                    IconButton(onClick = { onDeletePublicService(srv.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = ErrorCrimson)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text("Internal Service Pricing (Staff-Only Collection)", style = MaterialTheme.typography.titleMedium, color = GoldDark, fontWeight = FontWeight.Bold)
        }
        items(pricing, key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = GoldAccentSoft), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.serviceName, fontWeight = FontWeight.Bold, color = RoyalBlueDark)
                        Text("Unit Price: ${p.unitPriceAfn.toInt()} AFN • Bridge: ${p.bridgeUnitPriceAfn.toInt()} AFN", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                    }
                    TextButton(onClick = { showEditPrice = p }) { Text(strings.edit) }
                }
            }
        }
    }

    if (showAddService) {
        var nameEn by remember { mutableStateOf("") }
        var nameFa by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Cosmetic") }
        var descFa by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddService = false },
            title = { Text("Add Public Service (No Price Allowed)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nameFa, onValueChange = { nameFa = it }, label = { Text("Name (Dari)") }, singleLine = true)
                    OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("Name (English)") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                    OutlinedTextField(value = descFa, onValueChange = { descFa = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameEn.isNotBlank() || nameFa.isNotBlank()) {
                        onSavePublicService(PublicService(nameEn = nameEn, nameFa = nameFa, namePs = nameFa, category = category, descriptionFa = descFa, descriptionEn = descFa))
                        showAddService = false
                    }
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAddService = false }) { Text(strings.cancel) } }
        )
    }

    showEditPrice?.let { p ->
        var unitPrice by remember { mutableStateOf(p.unitPriceAfn.toInt().toString()) }
        var bridgePrice by remember { mutableStateOf(p.bridgeUnitPriceAfn.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showEditPrice = null },
            title = { Text("Edit Internal Tariff: ${p.serviceName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text("Unit Price (AFN)") }, singleLine = true)
                    OutlinedTextField(value = bridgePrice, onValueChange = { bridgePrice = it }, label = { Text("Bridge Unit Price (AFN)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveInternalPricing(
                        p.copy(
                            unitPriceAfn = unitPrice.toDoubleOrNull() ?: p.unitPriceAfn,
                            bridgeUnitPriceAfn = bridgePrice.toDoubleOrNull() ?: p.bridgeUnitPriceAfn
                        )
                    )
                    showEditPrice = null
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showEditPrice = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun UsersAndLinksCmsTab(
    staffRoles: List<StaffRoleRecord>,
    linkRequests: List<PatientLinkRequest>,
    patients: List<PatientRecord>,
    onSaveStaffRole: (String, String, String, UserRole, Boolean) -> Unit,
    onReviewLink: (String, String?, Boolean, String) -> Unit
) {
    var showAddStaff by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Staff Role Assignments", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                Button(onClick = { showAddStaff = true }) { Text("Assign Staff") }
            }
        }
        items(staffRoles, key = { it.id }) { st ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${st.displayName} (${st.role})", fontWeight = FontWeight.Bold, color = RoyalBlue)
                        Text(st.email, style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    }
                    Switch(checked = st.active, onCheckedChange = { onSaveStaffRole(st.uid, st.email, st.displayName, UserRole.fromString(st.role), it) })
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text("Patient Record Link Requests (${linkRequests.count { it.status == "PENDING" }})", style = MaterialTheme.typography.titleMedium, color = GoldDark, fontWeight = FontWeight.Bold)
        }
        items(linkRequests, key = { it.id }) { req ->
            val suggested = patients.firstOrNull {
                it.phone.contains(req.requesterPhone.takeLast(7)) || it.fullName.equals(req.requesterName, ignoreCase = true)
            }
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${req.requesterName} (${req.requesterPhone})", fontWeight = FontWeight.Bold, color = RoyalBlue)
                        StatusPill(req.status)
                    }
                    if (req.status == "PENDING") {
                        Text("Suggested match: ${suggested?.fullName ?: "None found (create patient first)"}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onReviewLink(req.id, suggested?.id, suggested != null, "Verified by Admin") },
                                enabled = suggested != null
                            ) { Text("Approve Link") }
                            OutlinedButton(onClick = { onReviewLink(req.id, null, false, "Could not match record") }) { Text("Reject", color = ErrorCrimson) }
                        }
                    }
                }
            }
        }
    }

    if (showAddStaff) {
        var uid by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.RECEPTIONIST) }
        AlertDialog(
            onDismissRequest = { showAddStaff = false },
            title = { Text("Assign Staff Role") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uid, onValueChange = { uid = it }, label = { Text("Firebase User UID") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Google Email") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(UserRole.RECEPTIONIST, UserRole.DOCTOR, UserRole.ADMIN, UserRole.SENIOR_ADMIN).forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.name, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (uid.isNotBlank() && email.isNotBlank()) {
                        onSaveStaffRole(uid, email, name, role, true)
                        showAddStaff = false
                    }
                }) { Text("Save Role") }
            },
            dismissButton = { TextButton(onClick = { showAddStaff = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FeedbackAdminTab(
    feedbackList: List<FeedbackComplaintItem>,
    onRespond: (String, String, String) -> Unit
) {
    var replying by remember { mutableStateOf<FeedbackComplaintItem?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(feedbackList, key = { it.id }) { fb ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${fb.authorName} • ${fb.kind} (${fb.rating}★)", fontWeight = FontWeight.Bold, color = RoyalBlue)
                        StatusPill(fb.status)
                    }
                    Text(fb.message, style = MaterialTheme.typography.bodyMedium)
                    if (fb.adminResponse.isNotBlank()) {
                        Text("Response: ${fb.adminResponse}", style = MaterialTheme.typography.bodySmall, color = SuccessEmerald)
                    }
                    TextButton(onClick = { replying = fb }) { Text("Reply / Resolve") }
                }
            }
        }
    }
    replying?.let { fb ->
        var reply by remember { mutableStateOf(fb.adminResponse) }
        AlertDialog(
            onDismissRequest = { replying = null },
            title = { Text("Respond to ${fb.authorName}") },
            text = { OutlinedTextField(value = reply, onValueChange = { reply = it }, label = { Text("Clinic Response") }, minLines = 3) },
            confirmButton = {
                Button(onClick = { onRespond(fb.id, reply, "RESOLVED"); replying = null }) { Text("Resolve") }
            },
            dismissButton = { TextButton(onClick = { replying = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BackupAndAuditTab(
    strings: AppStrings,
    backups: List<BackupSnapshot>,
    auditLogs: List<AuditLogEntry>,
    onCreateBackup: (String) -> Unit,
    onFlushSync: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = RoyalBlue), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupRestoreTitle, style = MaterialTheme.typography.titleMedium, color = PureWhite, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onCreateBackup("Full Snapshot ${ClinicCalendar.todayIso()}") },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = RoyalBlueDark)
                        ) { Text(strings.createBackup) }
                        OutlinedButton(onClick = onFlushSync) { Text(strings.syncNow, color = PureWhite) }
                    }
                }
            }
        }
        items(backups, key = { it.id }) { b ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(b.label, fontWeight = FontWeight.Bold, color = RoyalBlue)
                    Text("Records: ${b.summaryCount} • Created by ${b.createdBy}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                }
            }
        }
        item {
            Text(strings.auditLogsLabel, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
        }
        items(auditLogs.take(25), key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${log.action} (${log.entityType})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = RoyalBlueDark)
                    Text("${log.actorEmail} [${log.actorRole}] — ${log.details}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                }
            }
        }
    }
}

@Composable
fun NotificationsDialog(
    strings: AppStrings,
    notifications: List<NotificationItem>,
    onMarkRead: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.notificationsMenu) },
        text = {
            if (notifications.isEmpty()) {
                Text(strings.noRecordsYet)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notifications, key = { it.id }) { n ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (n.readFlag) SurfaceAlt else GoldAccentSoft),
                            modifier = Modifier.fillMaxWidth().clickable { onMarkRead(n.id) }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(n.title, fontWeight = FontWeight.Bold, color = RoyalBlue)
                                Text(n.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } }
    )
}
