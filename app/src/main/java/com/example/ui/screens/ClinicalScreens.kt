package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.*
import com.example.data.remote.AiUiState
import com.example.domain.*
import com.example.ui.components.ImageEncodingUtils
import com.example.ui.components.SectionHeaderCard
import com.example.ui.theme.*

@Composable
fun PatientsStaffScreen(
    strings: AppStrings,
    patients: List<PatientRecord>,
    onSavePatient: (PatientRecord) -> Unit,
    onArchivePatient: (String, Boolean) -> Unit,
    onOpenChartForPatient: (PatientRecord) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editingPatient by remember { mutableStateOf<PatientRecord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(patients, search, showArchived) {
        patients.filter { it.archived == showArchived }.filter { p ->
            search.isBlank() ||
                p.fullName.contains(search, ignoreCase = true) ||
                p.phone.contains(search, ignoreCase = true) ||
                p.patientCode.contains(search, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(SoftWhite).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionHeaderCard(title = strings.patientsMenu, icon = Icons.Default.People)
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_patient_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.addNew)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(strings.searchHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("patient_search_input")
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active (${patients.count { !it.archived }})") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived (${patients.count { it.archived }})") })
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) {
                item { EmptyInfoCard(strings.noRecordsYet) }
            } else {
                items(filtered, key = { it.id }) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.fullName} (${p.patientCode})", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                                Text("${p.age} yrs", style = MaterialTheme.typography.labelMedium, color = GoldDark)
                            }
                            Text("📞 ${p.phone} • ${p.address}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            if (p.allergies.isNotBlank()) {
                                Text("Allergies: ${p.allergies}", style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(onClick = { onOpenChartForPatient(p) }) { Text(strings.dentalChartMenu) }
                                OutlinedButton(onClick = { editingPatient = p }) { Text(strings.edit) }
                                TextButton(onClick = { onArchivePatient(p.id, !p.archived) }) {
                                    Text(if (p.archived) strings.restore else strings.archive)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingPatient != null) {
        PatientEditorDialog(
            strings = strings,
            initial = editingPatient,
            existingPatients = patients,
            onDismiss = { showAddDialog = false; editingPatient = null },
            onSave = {
                onSavePatient(it)
                showAddDialog = false
                editingPatient = null
            }
        )
    }
}

@Composable
private fun PatientEditorDialog(
    strings: AppStrings,
    initial: PatientRecord?,
    existingPatients: List<PatientRecord>,
    onDismiss: () -> Unit,
    onSave: (PatientRecord) -> Unit
) {
    var fullName by remember { mutableStateOf(initial?.fullName.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var ageText by remember { mutableStateOf(initial?.age?.toString() ?: "25") }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var medicalHistory by remember { mutableStateOf(initial?.medicalHistory.orEmpty()) }
    var allergies by remember { mutableStateOf(initial?.allergies.orEmpty()) }

    val duplicates = remember(fullName, phone, existingPatients) {
        if (initial != null) emptyList() else ClinicBusinessRules.findPotentialDuplicates(existingPatients, fullName, phone)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) strings.addNew else strings.edit, color = RoyalBlue, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (duplicates.isNotEmpty()) {
                    Surface(color = WarningContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "Possible duplicate patient found: ${duplicates.first().fullName} (${duplicates.first().patientCode})",
                            color = WarningAmber,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) })
                OutlinedTextField(value = ageText, onValueChange = { ageText = it }, label = { Text("Age") })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(strings.addressLabel) })
                OutlinedTextField(value = medicalHistory, onValueChange = { medicalHistory = it }, label = { Text("Medical History") })
                OutlinedTextField(value = allergies, onValueChange = { allergies = it }, label = { Text("Allergies") })
            }
        },
        confirmButton = {
            Button(
                enabled = fullName.isNotBlank() && phone.isNotBlank(),
                onClick = {
                    val age = ageText.toLongOrNull()?.coerceIn(0, 130) ?: 0L
                    onSave(
                        (initial ?: PatientRecord()).copy(
                            fullName = fullName,
                            phone = phone,
                            age = age,
                            address = address,
                            medicalHistory = medicalHistory,
                            allergies = allergies
                        )
                    )
                }
            ) { Text(strings.saveChanges) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

/**
 * Interactive 32-Tooth FDI Dental Chart + Treatment Planning + Bridge + Step Progress Confirmation.
 */
@Composable
fun DentalChartAndTreatmentScreen(
    strings: AppStrings,
    language: AppLanguage,
    patients: List<PatientRecord>,
    selectedPatient: PatientRecord?,
    treatments: List<TreatmentPlan>,
    internalPricing: List<InternalServicePricing>,
    bridgeDefaultPriceAfn: Double,
    onSelectPatient: (PatientRecord) -> Unit,
    onSaveTreatment: (TreatmentPlan) -> Unit,
    onDeleteTreatment: (String) -> Unit
) {
    val activePatient = selectedPatient ?: patients.firstOrNull { !it.archived }
    var selectedTeeth by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedType by remember { mutableStateOf(DentalTreatmentType.ROOT_CANAL) }
    var isBridgeMode by remember { mutableStateOf(false) }
    var abutmentTeeth by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var ponticTeeth by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var unitPriceInput by remember(selectedType, isBridgeMode, bridgeDefaultPriceAfn) {
        val defaultUnit = when {
            isBridgeMode -> bridgeDefaultPriceAfn
            selectedType == DentalTreatmentType.ROOT_CANAL -> internalPricing.firstOrNull { it.category.contains("Endo", true) }?.unitPriceAfn ?: 2000.0
            selectedType == DentalTreatmentType.CROWN -> internalPricing.firstOrNull { it.category.contains("Prostho", true) }?.unitPriceAfn ?: 4500.0
            selectedType == DentalTreatmentType.IMPLANT -> internalPricing.firstOrNull { it.category.contains("Implant", true) }?.unitPriceAfn ?: 25000.0
            selectedType == DentalTreatmentType.SCALING -> 1000.0
            selectedType == DentalTreatmentType.COMPOSITE -> 1500.0
            else -> 1200.0
        }
        mutableStateOf(defaultUnit.toInt().toString())
    }
    var notesInput by remember { mutableStateOf("") }

    val patientTreatments = remember(treatments, activePatient) {
        if (activePatient == null) emptyList() else treatments.filter { it.patientId == activePatient.id }
    }

    // Map each tooth FDI to its latest active treatment color
    val toothColorMap = remember(patientTreatments) {
        val map = mutableMapOf<Int, DentalTreatmentType>()
        patientTreatments.filter { it.status != "CANCELLED" }.forEach { tr ->
            val type = DentalTreatmentType.fromString(tr.category)
            tr.teethFdi.forEach { t -> map[t.toInt()] = type }
        }
        map
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SoftWhite).testTag("dental_chart_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeaderCard(
                title = strings.dentalChartMenu,
                subtitle = activePatient?.let { "${it.fullName} (${it.patientCode})" } ?: "Select or register a patient",
                icon = Icons.Default.GridView
            )
            if (patients.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    patients.filter { !it.archived }.take(4).forEach { p ->
                        FilterChip(
                            selected = activePatient?.id == p.id,
                            onClick = { onSelectPatient(p) },
                            label = { Text(p.fullName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Treatment Color Legend & Mode Selector
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Treatment Legend & Selection", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DentalTreatmentType.entries.take(3).forEach { t ->
                            FilterChip(
                                selected = selectedType == t && !isBridgeMode,
                                onClick = { selectedType = t; isBridgeMode = false },
                                leadingIcon = { Box(Modifier.size(10.dp).background(Color(t.colorHex), RoundedCornerShape(3.dp))) },
                                label = { Text(t.labelEn, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DentalTreatmentType.entries.drop(3).forEach { t ->
                            FilterChip(
                                selected = selectedType == t && !isBridgeMode,
                                onClick = { selectedType = t; isBridgeMode = false },
                                leadingIcon = { Box(Modifier.size(10.dp).background(Color(t.colorHex), RoundedCornerShape(3.dp))) },
                                label = { Text(t.labelEn, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    FilterChip(
                        selected = isBridgeMode,
                        onClick = { isBridgeMode = !isBridgeMode; selectedType = DentalTreatmentType.CROWN },
                        label = { Text("${strings.bridgeLabel} (${bridgeDefaultPriceAfn.toInt()} AFN/unit)") }
                    )

                    HorizontalDivider(color = SurfaceAlt)
                    Text("Upper Arch (Q1: 18–11 | Q2: 21–28)", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    FdiArchRow(
                        teeth = DentalChartCatalog.upperRight + DentalChartCatalog.upperLeft,
                        selectedTeeth = selectedTeeth,
                        toothColors = toothColorMap,
                        onToggleTooth = { fdi ->
                            selectedTeeth = if (fdi in selectedTeeth) selectedTeeth - fdi else selectedTeeth + fdi
                        }
                    )
                    Text("Lower Arch (Q4: 48–41 | Q3: 31–38)", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    FdiArchRow(
                        teeth = DentalChartCatalog.lowerRight + DentalChartCatalog.lowerLeft,
                        selectedTeeth = selectedTeeth,
                        toothColors = toothColorMap,
                        onToggleTooth = { fdi ->
                            selectedTeeth = if (fdi in selectedTeeth) selectedTeeth - fdi else selectedTeeth + fdi
                        }
                    )

                    if (isBridgeMode && selectedTeeth.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { abutmentTeeth = selectedTeeth }) {
                                Text("Set Abutments (${abutmentTeeth.size})")
                            }
                            OutlinedButton(onClick = { ponticTeeth = selectedTeeth }) {
                                Text("Set Pontics (${ponticTeeth.size})")
                            }
                        }
                    }

                    val unitPrice = unitPriceInput.toDoubleOrNull() ?: 0.0
                    val unitCount = if (isBridgeMode) (abutmentTeeth + ponticTeeth).distinct().size.coerceAtLeast(selectedTeeth.size) else selectedTeeth.size
                    val previewTotal = ClinicBusinessRules.calculateTreatmentTotal(unitCount, unitPrice)

                    OutlinedTextField(
                        value = unitPriceInput,
                        onValueChange = { unitPriceInput = it },
                        label = { Text("Unit Price (AFN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Clinical Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Formula: $unitCount teeth × ${unitPrice.toInt()} AFN = ${CurrencyManager.formatAfn(previewTotal, language)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = RoyalBlue,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        enabled = activePatient != null && selectedTeeth.isNotEmpty() && unitCount > 0,
                        onClick = {
                            val pat = activePatient ?: return@Button
                            val finalAbutments = if (isBridgeMode) abutmentTeeth.map { it.toLong() } else emptyList()
                            val finalPontics = if (isBridgeMode) ponticTeeth.map { it.toLong() } else emptyList()
                            onSaveTreatment(
                                TreatmentPlan(
                                    patientId = pat.id,
                                    linkedUserUid = pat.linkedUserUid.orEmpty(),
                                    patientName = pat.fullName,
                                    treatmentName = if (isBridgeMode) strings.bridgeLabel else selectedType.labelEn,
                                    category = selectedType.name,
                                    teethFdi = selectedTeeth.sorted().map { it.toLong() },
                                    bridgeFlag = isBridgeMode,
                                    abutmentTeeth = finalAbutments,
                                    ponticTeeth = finalPontics,
                                    unitCount = unitCount.toLong(),
                                    unitPriceAfn = unitPrice,
                                    totalPriceAfn = previewTotal,
                                    status = "IN_PROGRESS",
                                    steps = selectedType.defaultSteps,
                                    completedSteps = emptyList(),
                                    clinicalNotes = notesInput
                                )
                            )
                            selectedTeeth = emptySet()
                            notesInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("create_treatment_plan_button")
                    ) {
                        Text("${strings.addNew} ${strings.treatmentsMenu}")
                    }
                }
            }
        }

        // Active Patient Treatment Plans with Doctor Step Confirmation
        item { SectionHeaderCard(title = strings.treatmentsMenu, icon = Icons.Default.Checklist) }
        items(patientTreatments, key = { it.id }) { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${plan.treatmentName} — Teeth: ${plan.teethFdi.joinToString(", ")}", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                            Text("${CurrencyManager.formatAfn(plan.totalPriceAfn, language)} • ${plan.status}", style = MaterialTheme.typography.bodySmall, color = GoldDark)
                        }
                        Text("${plan.completedPercentage}%", style = MaterialTheme.typography.titleMedium, color = SuccessEmerald, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { plan.completedPercentage / 100f },
                        color = Color(DentalTreatmentType.fromString(plan.category).colorHex),
                        trackColor = SurfaceAlt,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    plan.steps.forEach { step ->
                        val done = step in plan.completedSteps
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updatedCompleted = if (done) plan.completedSteps - step else plan.completedSteps + step
                                    val newStatus = if (updatedCompleted.size == plan.steps.size && plan.steps.isNotEmpty()) "COMPLETED" else "IN_PROGRESS"
                                    onSaveTreatment(plan.copy(completedSteps = updatedCompleted, status = newStatus))
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(checked = done, onCheckedChange = null)
                            Spacer(Modifier.width(6.dp))
                            Text(step, style = MaterialTheme.typography.bodySmall, color = if (done) SuccessEmerald else RoyalBlueDark)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSaveTreatment(plan.copy(status = "CANCELLED")) }) { Text("Cancel Declined") }
                        TextButton(onClick = { onDeleteTreatment(plan.id) }) { Text(strings.delete, color = ErrorCrimson) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FdiArchRow(
    teeth: List<Int>,
    selectedTeeth: Set<Int>,
    toothColors: Map<Int, DentalTreatmentType>,
    onToggleTooth: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        teeth.chunked(8).forEach { half ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                half.forEach { fdi ->
                    val isSelected = fdi in selectedTeeth
                    val trColor = toothColors[fdi]?.let { Color(it.colorHex) }
                    Surface(
                        color = when {
                            isSelected -> RoyalBlue
                            trColor != null -> trColor.copy(alpha = 0.22f)
                            else -> SurfaceAlt
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = trColor ?: if (isSelected) GoldAccent else LightGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleTooth(fdi) }
                            .testTag("tooth_fdi_$fdi")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = fdi.toString(),
                                color = if (isSelected) PureWhite else RoyalBlueDark,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Specialized Clinical Modules: Orthodontics (ClearPath/Conventional), Implants, Lab Orders,
 * Prescriptions (with PDF + genuine signature), Medicine Inventory, and Genuine Firebase AI Logic Assistant.
 */
@Composable
fun SpecializedClinicalScreen(
    strings: AppStrings,
    language: AppLanguage,
    patients: List<PatientRecord>,
    orthoCases: List<OrthodonticCase>,
    implants: List<ImplantRecord>,
    labOrders: List<LabOrder>,
    prescriptions: List<PrescriptionRecord>,
    medicines: List<MedicineItem>,
    clinicProfile: PublicClinicProfile,
    aiState: AiUiState,
    onSaveOrtho: (OrthodonticCase) -> Unit,
    onSaveImplant: (ImplantRecord) -> Unit,
    onSaveLabOrder: (LabOrder) -> Unit,
    onSavePrescription: (PrescriptionRecord) -> Unit,
    onSaveMedicine: (MedicineItem) -> Unit,
    onRunAiAnalysis: (modality: String, notes: String, bitmap: android.graphics.Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ortho", "Implants", "Lab", "Rx / PDF", "Inventory", "AI Assist")

    Column(modifier = Modifier.fillMaxSize().background(SoftWhite)) {
        ScrollableTabRow(selectedTabIndex = tabIndex, containerColor = PureWhite, contentColor = RoyalBlue) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = tabIndex == idx, onClick = { tabIndex = idx }, text = { Text(title, fontWeight = FontWeight.SemiBold) })
            }
        }

        when (tabIndex) {
            0 -> OrthoTab(patients, orthoCases, onSaveOrtho)
            1 -> ImplantsTab(patients, implants, onSaveImplant)
            2 -> LabOrdersTab(patients, labOrders, onSaveLabOrder)
            3 -> PrescriptionsTab(strings, patients, prescriptions, medicines, clinicProfile, onSavePrescription)
            4 -> InventoryTab(strings, medicines, onSaveMedicine)
            5 -> AiClinicalTab(strings, aiState, onRunAiAnalysis)
        }
    }
}

@Composable
private fun OrthoTab(patients: List<PatientRecord>, cases: List<OrthodonticCase>, onSave: (OrthodonticCase) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)) {
                Text("Add Orthodontic / ClearPath Case")
            }
        }
        items(cases, key = { it.id }) { c ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("${c.patientName} — ${c.caseType}", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Text("Appliance: ${c.applianceInfo} • Stage ${c.currentStage}/${c.totalStages}", style = MaterialTheme.typography.bodySmall)
                    Text(c.initialAssessment, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                }
            }
        }
    }
    if (showAdd) {
        val pat = patients.firstOrNull()
        var name by remember { mutableStateOf(pat?.fullName.orEmpty()) }
        var caseType by remember { mutableStateOf("CLEARPATH_ALIGNER") }
        var appliance by remember { mutableStateOf("ClearPath Upper & Lower Trays") }
        var assessment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Orthodontic Case") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Patient Name") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CLEARPATH_ALIGNER", "CONVENTIONAL").forEach { t ->
                            FilterChip(selected = caseType == t, onClick = { caseType = t }, label = { Text(t, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = appliance, onValueChange = { appliance = it }, label = { Text("Appliance Details") })
                    OutlinedTextField(value = assessment, onValueChange = { assessment = it }, label = { Text("Initial Assessment") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(OrthodonticCase(patientId = pat?.id ?: "manual", linkedUserUid = pat?.linkedUserUid.orEmpty(), patientName = name, caseType = caseType, applianceInfo = appliance, initialAssessment = assessment))
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ImplantsTab(patients: List<PatientRecord>, implants: List<ImplantRecord>, onSave: (ImplantRecord) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)) { Text("Add Implant Surgical Record") } }
        items(implants, key = { it.id }) { imp ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("${imp.patientName} — Tooth #${imp.toothFdi}", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Text("${imp.brandModel} (${imp.dimensions}) • Lot: ${imp.batchLotNumber}", style = MaterialTheme.typography.bodySmall)
                    Text("Healing: ${imp.healingStage} • Prosthetic: ${imp.prostheticStage}", style = MaterialTheme.typography.labelSmall, color = GoldDark)
                }
            }
        }
    }
    if (showAdd) {
        val pat = patients.firstOrNull()
        var name by remember { mutableStateOf(pat?.fullName.orEmpty()) }
        var tooth by remember { mutableStateOf("36") }
        var brand by remember { mutableStateOf("") }
        var dims by remember { mutableStateOf("4.1 × 10 mm") }
        var lot by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Implant Record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Patient Name") })
                    OutlinedTextField(value = tooth, onValueChange = { tooth = it }, label = { Text("Tooth FDI #") })
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand & Model") })
                    OutlinedTextField(value = dims, onValueChange = { dims = it }, label = { Text("Dimensions") })
                    OutlinedTextField(value = lot, onValueChange = { lot = it }, label = { Text("Batch / Lot Number") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(ImplantRecord(patientId = pat?.id ?: "manual", linkedUserUid = pat?.linkedUserUid.orEmpty(), patientName = name, toothFdi = tooth.toLongOrNull() ?: 36L, brandModel = brand, dimensions = dims, batchLotNumber = lot, surgeryDate = ClinicCalendar.todayIso()))
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LabOrdersTab(patients: List<PatientRecord>, orders: List<LabOrder>, onSave: (LabOrder) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)) { Text("New Prosthetic / Lab Order") } }
        items(orders, key = { it.id }) { o ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("${o.patientName} — ${o.workType} (${o.material}, Shade ${o.shade})", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Text("Lab: ${o.laboratoryName} • Delivery: ${o.expectedDelivery} • Status: ${o.status}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showAdd) {
        val pat = patients.firstOrNull()
        var name by remember { mutableStateOf(pat?.fullName.orEmpty()) }
        var lab by remember { mutableStateOf("Sakha Partner Dental Lab") }
        var material by remember { mutableStateOf("Zirconia") }
        var shade by remember { mutableStateOf("A2") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Lab Order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Patient Name") })
                    OutlinedTextField(value = lab, onValueChange = { lab = it }, label = { Text("Laboratory Name") })
                    OutlinedTextField(value = material, onValueChange = { material = it }, label = { Text("Material (Zirconia/Porcelain/Metal)") })
                    OutlinedTextField(value = shade, onValueChange = { shade = it }, label = { Text("Shade") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(LabOrder(patientId = pat?.id ?: "manual", patientName = name, laboratoryName = lab, material = material, shade = shade, orderDate = ClinicCalendar.todayIso()))
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PrescriptionsTab(
    strings: AppStrings,
    patients: List<PatientRecord>,
    prescriptions: List<PrescriptionRecord>,
    medicines: List<MedicineItem>,
    clinicProfile: PublicClinicProfile,
    onSave: (PrescriptionRecord) -> Unit
) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Genuine Doctor Signature Active", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                        Text("Automatically embedded on exported & printed PDF prescriptions.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    }
                    Image(painter = painterResource(R.drawable.doctor_signature), contentDescription = "Dr Sakha Signature", modifier = Modifier.height(42.dp).width(96.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        item {
            Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue), modifier = Modifier.testTag("create_prescription_button")) {
                Text("${strings.addNew} Prescription")
            }
        }
        items(prescriptions, key = { it.id }) { rx ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${rx.patientName} (${rx.patientAge}y) — ${rx.visitDate}", style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    rx.medicines.forEach { m ->
                        Text("• ${m.medicineName} ${m.dosage} • ${m.frequency} • ${m.duration} (${m.mealTiming})", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val file = PrescriptionPdfGenerator.generatePdfFile(context, rx, clinicProfile.nameEn, clinicProfile.nameFa, clinicProfile.phones.firstOrNull().orEmpty(), clinicProfile.addressEn)
                            PrescriptionPdfGenerator.sharePdf(context, file)
                        }) { Text(strings.sharePdf) }
                        OutlinedButton(onClick = {
                            val file = PrescriptionPdfGenerator.generatePdfFile(context, rx, clinicProfile.nameEn, clinicProfile.nameFa, clinicProfile.phones.firstOrNull().orEmpty(), clinicProfile.addressEn)
                            PrescriptionPdfGenerator.printPdf(context, file, "Sakha_Rx_${rx.id}")
                        }) { Text(strings.printPdf) }
                    }
                }
            }
        }
    }

    if (showAdd) {
        val pat = patients.firstOrNull()
        var patName by remember { mutableStateOf(pat?.fullName.orEmpty()) }
        var medName by remember { mutableStateOf(medicines.firstOrNull()?.name.orEmpty()) }
        var dosage by remember { mutableStateOf("500mg") }
        var freq by remember { mutableStateOf("Every 8 hours") }
        var duration by remember { mutableStateOf("5 days") }
        var qty by remember { mutableStateOf("15") }
        var meal by remember { mutableStateOf("After meals") }
        var notes by remember { mutableStateOf("") }
        val medList = remember { mutableStateListOf<PrescribedMedicine>() }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Create Prescription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = patName, onValueChange = { patName = it }, label = { Text("Patient Name") })
                    OutlinedTextField(value = medName, onValueChange = { medName = it }, label = { Text("Medicine Name (Optional)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text("Dosage") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = freq, onValueChange = { freq = it }, label = { Text("Frequency") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Before meals", "After meals").forEach { m ->
                            FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    if (medName.isNotBlank()) {
                        TextButton(onClick = {
                            medList.add(PrescribedMedicine(medName, dosage, freq, duration, qty, meal))
                            medName = ""
                        }) { Text("+ Add medicine to list (${medList.size})") }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Additional Notes") })
                }
            },
            confirmButton = {
                Button(
                    enabled = patName.isNotBlank(),
                    onClick = {
                        val finalMeds = medList.toMutableList()
                        if (medName.isNotBlank()) {
                            finalMeds.add(PrescribedMedicine(medName, dosage, freq, duration, qty, meal))
                        }
                        onSave(
                            PrescriptionRecord(
                                patientId = pat?.id ?: "manual",
                                linkedUserUid = pat?.linkedUserUid.orEmpty(),
                                patientName = patName,
                                patientAge = pat?.age ?: 0L,
                                visitDate = ClinicCalendar.todayIso(),
                                medicines = finalMeds,
                                notes = notes
                            )
                        )
                        showAdd = false
                    }
                ) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun InventoryTab(strings: AppStrings, medicines: List<MedicineItem>, onSave: (MedicineItem) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    val lowStock = medicines.filter { it.quantity <= it.minStockLevel }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (lowStock.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WarningContainer), modifier = Modifier.fillMaxWidth()) {
                    Text("⚠️ Low Stock Alert: ${lowStock.joinToString { "${it.name} (${it.quantity})" }}", color = WarningAmber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item { Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)) { Text("${strings.addNew} Medicine") } }
        items(medicines, key = { it.id }) { m ->
            Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.name} (${m.category})", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                        Text("Batch: ${m.batchNumber} • Expiry: ${m.expiryDate}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    }
                    Text("${m.quantity} ${m.unit}", style = MaterialTheme.typography.titleMedium, color = if (m.quantity <= m.minStockLevel) ErrorCrimson else SuccessEmerald, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("20") }
        var expiry by remember { mutableStateOf("2027-12-31") }
        var batch by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Medicine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine Name") })
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") })
                    OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Expiry Date (YYYY-MM-DD)") })
                    OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("Batch Number") })
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    onSave(MedicineItem(name = name, quantity = qty.toLongOrNull() ?: 0L, expiryDate = expiry, batchNumber = batch))
                    showAdd = false
                }) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun AiClinicalTab(
    strings: AppStrings,
    aiState: AiUiState,
    onRunAi: (modality: String, notes: String, bitmap: android.graphics.Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var modality by remember { mutableStateOf("Dental Radiograph Analysis") }
    var notes by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var clinicianVerified by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedBitmap = ImageEncodingUtils.uriToBitmap(context, uri)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = WarningContainer), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.aiClinicianDisclaimer, style = MaterialTheme.typography.bodySmall, color = RoyalBlueDark)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Dental Radiograph Analysis", "CBCT / Implant Planning", "Orthodontic Analysis").forEach { m ->
                    FilterChip(selected = modality == m, onClick = { modality = m }, label = { Text(m, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("De-identified clinical observations (do not enter patient name)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (selectedBitmap != null) "Image Attached ✓" else "Attach Radiograph / Photo")
                }
                Button(
                    enabled = (notes.isNotBlank() || selectedBitmap != null) && aiState !is AiUiState.Loading,
                    onClick = {
                        clinicianVerified = false
                        onRunAi(modality, notes, selectedBitmap)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                    modifier = Modifier.testTag("run_ai_analysis_button")
                ) {
                    Text("Run AI Analysis")
                }
            }
        }

        item {
            when (aiState) {
                is AiUiState.Idle -> EmptyInfoCard("Attach a radiograph or enter de-identified clinical context to request AI-assisted dental analysis.")
                is AiUiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RoyalBlue) }
                is AiUiState.Error -> Card(colors = CardDefaults.cardColors(containerColor = ErrorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(aiState.message, color = ErrorCrimson, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
                is AiUiState.Success -> {
                    val res = aiState.result
                    Card(colors = CardDefaults.cardColors(containerColor = PureWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = GoldAccentSoft, shape = RoundedCornerShape(8.dp)) {
                                Text("AI-ASSISTED FINDING — REQUIRES CLINICIAN REVIEW", color = RoyalBlueDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Text(res.summary, style = MaterialTheme.typography.bodyMedium, color = RoyalBlueDark)
                            if (res.observations.isNotEmpty()) {
                                Text("Observations:", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                                res.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (res.suggestedNextSteps.isNotEmpty()) {
                                Text("Suggested Considerations:", style = MaterialTheme.typography.titleSmall, color = RoyalBlue, fontWeight = FontWeight.Bold)
                                res.suggestedNextSteps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (res.riskFlags.isNotEmpty()) {
                                Text("Risk Flags:", style = MaterialTheme.typography.titleSmall, color = ErrorCrimson, fontWeight = FontWeight.Bold)
                                res.riskFlags.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = ErrorCrimson) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = clinicianVerified, onCheckedChange = { clinicianVerified = it })
                                Text("Reviewed and clinically verified by treating dentist", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
