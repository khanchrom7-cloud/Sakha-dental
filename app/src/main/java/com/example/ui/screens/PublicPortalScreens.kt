package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.AppStrings
import com.example.domain.PublicClinicProfile
import com.example.domain.PublicDoctorProfile
import com.example.domain.PublicMediaItem
import com.example.domain.PublicService
import com.example.ui.components.SectionHeaderCard
import com.example.ui.theme.*

@Composable
fun GuestHomeScreen(
    strings: AppStrings,
    language: AppLanguage,
    clinic: PublicClinicProfile,
    doctor: PublicDoctorProfile,
    servicesCount: Int,
    onOpenDoctorProfile: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenBookingOrSignIn: () -> Unit,
    onOpenWebsitePreview: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = PureWhite,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.sakha_logo),
                                contentDescription = clinic.nameEn,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (language) {
                                    AppLanguage.DARI -> clinic.nameFa
                                    AppLanguage.PASHTO -> clinic.namePs
                                    AppLanguage.ENGLISH -> clinic.nameEn
                                },
                                color = PureWhite,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = clinic.motto,
                                color = GoldAccent,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = RoyalBlueLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(clinic.announcementTitle, color = GoldAccentSoft, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(clinic.announcementBody, color = SoftWhite, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onOpenBookingOrSignIn,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = RoyalBlueDark),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("guest_book_appointment_button")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.bookAppointment, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenWebsitePreview,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("guest_website_preview_button")
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Web Portal")
                        }
                    }
                }
            }
        }

        // Featured Doctor Card (Quick entry to complete Doctor Profile)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.doctor_photo),
                        contentDescription = doctor.fullNameEn,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .border(2.dp, GoldAccent, CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) doctor.fullNameEn else doctor.fullNameFa,
                            style = MaterialTheme.typography.titleMedium,
                            color = RoyalBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (language == AppLanguage.ENGLISH) doctor.titleEn else doctor.titleFa,
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldDark
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = onOpenDoctorProfile,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.testTag("guest_open_doctor_profile_button")
                        ) {
                            Text("${strings.doctorProfileMenu} →", color = RoyalBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Public Services Entry Card (strictly NO prices)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.ourServicesMenu, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("$servicesCount specialised dental & implant treatments", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    }
                    FilledTonalButton(
                        onClick = onOpenServices,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldAccentSoft, contentColor = RoyalBlueDark),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("guest_open_services_button")
                    ) {
                        Text(strings.ourServicesMenu, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Contact & Working Hours Card
        item {
            ClinicContactAndHoursCard(strings = strings, language = language, clinic = clinic)
        }
    }
}

@Composable
fun ClinicContactAndHoursCard(
    strings: AppStrings,
    language: AppLanguage,
    clinic: PublicClinicProfile
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderCard(title = strings.contactUsTitle, icon = Icons.Default.LocationOn)
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Place, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (language == AppLanguage.ENGLISH) clinic.addressEn else clinic.addressFa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoyalBlueDark
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(clinic.phones.joinToString("  |  "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(clinic.email, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = SurfaceAlt)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(strings.workingHoursTitle, style = MaterialTheme.typography.labelLarge, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (language == AppLanguage.ENGLISH) "${clinic.workingHoursEn} • ${clinic.fridayStatusEn}"
                        else "${clinic.workingHoursFa} • ${clinic.fridayStatusFa}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                }
            }
        }
    }
}

/**
 * Shared centrally-managed Doctor Profile screen used identically in Guest Portal, Patient Portal, and Website.
 */
@Composable
fun DoctorProfileScreen(
    strings: AppStrings,
    language: AppLanguage,
    doctor: PublicDoctorProfile
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .testTag("doctor_profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.doctor_photo),
                        contentDescription = doctor.fullNameEn,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, GoldAccent, CircleShape)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = doctor.fullNameFa,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = doctor.fullNameEn,
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldAccentSoft
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = GoldAccent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) doctor.titleEn else doctor.titleFa,
                            style = MaterialTheme.typography.labelLarge,
                            color = RoyalBlueDark,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = when (language) {
                            AppLanguage.DARI -> doctor.bioFa
                            AppLanguage.PASHTO -> doctor.bioPs
                            AppLanguage.ENGLISH -> doctor.bioEn
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftWhite
                    )
                }
            }
        }

        item { DoctorListCard(title = strings.educationTitle, items = doctor.education, icon = Icons.Default.School) }
        item { DoctorListCard(title = strings.trainingTitle, items = doctor.training, icon = Icons.Default.WorkspacePremium) }
        item { DoctorListCard(title = strings.licensesTitle, items = doctor.licenses, icon = Icons.Default.VerifiedUser) }
        item { DoctorListCard(title = strings.experienceTitle, items = doctor.experience, icon = Icons.Default.MedicalServices) }
        item { DoctorListCard(title = strings.practiceAreasTitle, items = doctor.practiceAreas, icon = Icons.Default.LocalHospital) }
        if (doctor.achievements.isNotEmpty()) {
            item { DoctorListCard(title = strings.achievementsTitle, items = doctor.achievements, icon = Icons.Default.EmojiEvents) }
        }
    }
}

@Composable
private fun DoctorListCard(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeaderCard(title = title, icon = icon)
            items.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(entry, style = MaterialTheme.typography.bodyMedium, color = RoyalBlueDark)
                }
            }
        }
    }
}

/**
 * Shared Public Services Catalogue displayed identically in Guest Portal and Patient Portal.
 * STRICT RULE: Never displays prices, rates, price ranges, or estimates.
 */
@Composable
fun PublicServicesScreen(
    strings: AppStrings,
    language: AppLanguage,
    services: List<PublicService>,
    onRequestConsultation: (PublicService) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = remember(services) { listOf("ALL") + services.map { it.category }.distinct() }
    val filtered = remember(services, selectedCategory) {
        if (selectedCategory == "ALL") services else services.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .testTag("public_services_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeaderCard(
                title = strings.ourServicesMenu,
                subtitle = strings.noPricesNotice,
                icon = Icons.Default.MedicalInformation
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.take(4).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(if (cat == "ALL") "All" else cat, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { service ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("public_service_card_${service.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(color = GoldAccentSoft, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = service.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = RoyalBlueDark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = RoyalBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = service.localizedName(language.code),
                        style = MaterialTheme.typography.titleMedium,
                        color = RoyalBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = service.localizedDescription(language.code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalBlueDark
                    )
                    val info = service.localizedGeneralInfo(language.code)
                    if (info.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = info, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onRequestConsultation(service) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBlue),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(strings.contactClinicForQuote, color = RoyalBlue)
                    }
                }
            }
        }
    }
}

@Composable
fun WebsitePreviewAndMediaScreen(
    strings: AppStrings,
    language: AppLanguage,
    clinic: PublicClinicProfile,
    doctor: PublicDoctorProfile,
    services: List<PublicService>,
    media: List<PublicMediaItem>,
    isAdmin: Boolean,
    onAddMedia: (PublicMediaItem) -> Unit,
    onNavigateDoctor: () -> Unit,
    onNavigateServices: () -> Unit
) {
    var showAddMediaDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .testTag("website_preview_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlueDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Surface(color = if (clinic.websitePublished) SuccessEmerald else WarningAmber, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = if (clinic.websitePublished) "Website LIVE" else "Draft Mode",
                                color = PureWhite,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text("www.sakhadental.af", color = GoldAccent, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(clinic.websiteHeroTitle, color = PureWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(clinic.websiteHeroSubtitle, color = GoldAccentSoft, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onNavigateDoctor, colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = RoyalBlueDark)) {
                            Text(strings.doctorProfileMenu)
                        }
                        OutlinedButton(onClick = onNavigateServices, colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite)) {
                            Text("${services.size} ${strings.ourServicesMenu}")
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionHeaderCard(title = strings.mediaGalleryMenu, icon = Icons.Default.PhotoLibrary)
                if (isAdmin) {
                    FilledTonalButton(onClick = { showAddMediaDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addNew)
                    }
                }
            }
        }

        if (media.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Collections, contentDescription = null, tint = LightGray, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Approved clinic & smile design media (with patient consent) appears here.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    }
                }
            }
        } else {
            items(media, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = PureWhite), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                        Text("${item.category} • Consent Verified", style = MaterialTheme.typography.labelSmall, color = SuccessEmerald)
                        if (item.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(item.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item { ClinicContactAndHoursCard(strings = strings, language = language, clinic = clinic) }
    }

    if (showAddMediaDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Smile Design") }
        var desc by remember { mutableStateOf("") }
        var consent by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddMediaDialog = false },
            title = { Text("Publish Media Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (Implants, Smile Design...)") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text("Patient written consent verified", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank() && consent,
                    onClick = {
                        onAddMedia(PublicMediaItem(title = title, category = category, description = desc, consentApproved = consent, published = true))
                        showAddMediaDialog = false
                    }
                ) { Text(strings.saveChanges) }
            },
            dismissButton = { TextButton(onClick = { showAddMediaDialog = false }) { Text(strings.cancel) } }
        )
    }
}
