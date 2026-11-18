package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val level: Int) {
    SUPER_ADMIN(6),
    SENIOR_ADMIN(5),
    ADMIN(4),
    DOCTOR(3),
    RECEPTIONIST(2),
    PATIENT(1),
    GUEST(0);

    val isStaff: Boolean get() = level >= RECEPTIONIST.level
    val isDoctorOrAbove: Boolean get() = level >= DOCTOR.level
    val isAdminOrAbove: Boolean get() = level >= ADMIN.level
    val isSeniorAdminOrAbove: Boolean get() = level >= SENIOR_ADMIN.level
    val isSuperAdmin: Boolean get() = this == SUPER_ADMIN

    companion object {
        fun fromString(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PATIENT
    }
}

enum class DentalTreatmentType(
    val colorHex: Long,
    val labelEn: String,
    val labelFa: String,
    val labelPs: String,
    val defaultSteps: List<String>
) {
    ROOT_CANAL(
        0xFF1E88E5,
        "Root Canal",
        "عصب‌کشی",
        "عصب کشي",
        listOf(
            "Initial examination",
            "Access and cleaning",
            "Dressing where required",
            "Canal preparation",
            "Obturation",
            "Final restoration"
        )
    ),
    CROWN(
        0xFF8E24AA,
        "Crown",
        "پوش (تاج دندان)",
        "پوښ (تاج)",
        listOf(
            "Tooth preparation",
            "Impression or scan",
            "Laboratory stage",
            "Trial",
            "Final installation"
        )
    ),
    EXTRACTION(
        0xFFE53935,
        "Extraction",
        "کشیدن دندان",
        "د غاښ ایستل",
        listOf(
            "Clinical assessment",
            "Local anesthesia",
            "Atraumatic extraction",
            "Hemostasis & post-op care"
        )
    ),
    SCALING(
        0xFFFB8C00,
        "Scaling & Polishing",
        "جرم‌گیری",
        "جرم پاکول",
        listOf(
            "Periodontal exam",
            "Ultrasonic scaling",
            "Polishing & fluoride"
        )
    ),
    IMPLANT(
        0xFFC9A24D,
        "Dental Implant",
        "ایمپلنت",
        "امپلانټ",
        listOf(
            "Assessment",
            "Surgical placement",
            "Healing",
            "Prosthetic stage",
            "Final restoration"
        )
    ),
    COMPOSITE(
        0xFF43A047,
        "Composite Restoration",
        "ترمیم کامپوزیت",
        "کامپوزیت ډکول",
        listOf(
            "Caries removal",
            "Etching & bonding",
            "Layered composite placement",
            "Occlusion & polishing"
        )
    );

    companion object {
        fun fromString(raw: String?): DentalTreatmentType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: COMPOSITE
    }
}

@Keep
@IgnoreExtraProperties
data class PublicClinicProfile(
    @DocumentId val id: String = "main",
    val nameEn: String = "Sakha Specialised Dental Centre",
    val nameFa: String = "مرکز تخصصی دندان و ایمپلنت سخا",
    val namePs: String = "د سخا د غاښونو او امپلانټ تخصصي مرکز",
    val motto: String = "سخا، نماد تخصص، زیبایی و اعتماد",
    val addressFa: String = "شهر شبرغان، عقب تانک تیل دولتی، کوچه جن محله، ولایت جوزجان، افغانستان",
    val addressEn: String = "Sheberghan City, Behind Government Fuel Station, Jan Mahala Alley, Jawzjan, Afghanistan",
    val phones: List<String> = listOf("0782221100", "0796331611"),
    val email: String = "sakha.dentalcenter26@gmail.com",
    val workingHoursEn: String = "Saturday to Thursday: 8:00 AM – 7:00 PM",
    val workingHoursFa: String = "شنبه الی پنجشنبه: ۸:۰۰ صبح الی ۷:۰۰ عصر",
    val fridayStatusEn: String = "Friday: Closed",
    val fridayStatusFa: String = "جمعه: رخصت",
    val announcementTitle: String = "تکنالوژی پیشرفته و محیط آرام درمانی",
    val announcementBody: String = "پذیرش نوبت‌های ایمپلنت، ارتودنسی، کلیر الاینر، وینیر و درمان‌های تخصصی دندان در شهر شبرغان.",
    val websiteHeroTitle: String = "Sakha Specialised Dental Centre",
    val websiteHeroSubtitle: String = "مرکز تخصصی دندان و ایمپلنت سخا — سخا، نماد تخصص، زیبایی و اعتماد",
    val websitePublished: Boolean = true,
    val socialFacebook: String = "https://facebook.com/SakhaDentalCentre",
    val socialInstagram: String = "@sakhadental.af",
    val socialWhatsapp: String = "0782221100",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicDoctorProfile(
    @DocumentId val id: String = "main",
    val fullNameEn: String = "Dr. Ghulam Sediq Sakha",
    val fullNameFa: String = "داکتر غلام صدیق سخا",
    val titleFa: String = "معالج امراض جوف دهن و دندان",
    val titleEn: String = "Oral & Dental Diseases Specialist",
    val bioFa: String = "داکتر غلام صدیق سخا، بنیان‌گذار و مسئول مرکز تخصصی دندان و ایمپلنت سخا، با بهره‌گیری از جدیدترین تکنالوژی‌ها و روش‌های پیشرفته دندانپزشکی، خدمات جامع و تخصصی را در محیطی آرام، مطمئن و بدون استرس برای سلامت و زیبایی لبخند مراجعین فراهم می‌سازد.",
    val bioEn: String = "Dr. Ghulam Sediq Sakha is the Founder and Responsible Director of Sakha Specialised Dental Centre, delivering modern, evidence-based dental and implant care in a calm, welcoming, and stress-free clinical environment.",
    val bioPs: String = "ډاکټر غلام صدیق سخا، د سخا د غاښونو او امپلانټ تخصصي مرکز بنسټګر او مسؤل مدیر دی چې په عصري وسایلو او آرامه فضا کې د غاښونو جامع او تخصصي درملنه وړاندې کوي.",
    val education: List<String> = listOf(
        "BDS, Baqai Medical University, Karachi, Pakistan, 2020",
        "RDS – Karachi, Pakistan"
    ),
    val training: List<String> = listOf(
        "C_Endo",
        "C_Ortho",
        "C_Implant",
        "ClearPath clear aligner training"
    ),
    val licenses: List<String> = listOf(
        "Afghanistan Medical Council professional activity license",
        "Pakistan professional activity license"
    ),
    val experience: List<String> = listOf(
        "Professor and House Job Manager at Noman Sadat Institute of Higher Education",
        "Dentist at Noman Sadat Hospital",
        "Dentist at Shifa Stomatology Hospital",
        "Dentist at Alshifa Dental Clinic",
        "Founder and responsible director of Sakha Specialised Dental Centre"
    ),
    val practiceAreas: List<String> = listOf(
        "Dental Implants & Oral Surgery",
        "Endodontics (Root Canal Therapy)",
        "Orthodontics & ClearPath Clear Aligners",
        "Cosmetic Dentistry, Veneers & Whitening",
        "Crowns, Bridges & Prosthodontics"
    ),
    val achievements: List<String> = listOf(
        "Founder of Sakha Specialised Dental Centre — Jawzjan",
        "ClearPath Certified Aligner Provider"
    ),
    val published: Boolean = true,
    val version: Long = 1L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * STRICT PRICE PRIVACY MODEL:
 * PublicService NEVER carries price, unitPrice, priceRange, or financial estimate fields.
 */
@Keep
@IgnoreExtraProperties
data class PublicService(
    @DocumentId val id: String = "",
    val nameEn: String = "",
    val nameFa: String = "",
    val namePs: String = "",
    val category: String = "",
    val descriptionEn: String = "",
    val descriptionFa: String = "",
    val descriptionPs: String = "",
    val generalInfoEn: String = "",
    val generalInfoFa: String = "",
    val generalInfoPs: String = "",
    val imageKey: String = "dental",
    val published: Boolean = true,
    val sortOrder: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedName(langCode: String): String = when (langCode) {
        "fa" -> nameFa.ifBlank { nameEn }
        "ps" -> namePs.ifBlank { nameFa.ifBlank { nameEn } }
        else -> nameEn.ifBlank { nameFa }
    }

    fun localizedDescription(langCode: String): String = when (langCode) {
        "fa" -> descriptionFa.ifBlank { descriptionEn }
        "ps" -> descriptionPs.ifBlank { descriptionFa.ifBlank { descriptionEn } }
        else -> descriptionEn.ifBlank { descriptionFa }
    }

    fun localizedGeneralInfo(langCode: String): String = when (langCode) {
        "fa" -> generalInfoFa.ifBlank { generalInfoEn }
        "ps" -> generalInfoPs.ifBlank { generalInfoFa.ifBlank { generalInfoEn } }
        else -> generalInfoEn.ifBlank { generalInfoFa }
    }
}

/**
 * Staff-only internal pricing table stored in `/internal_service_pricing`.
 * Never queried or exposed in Guest or Patient portals.
 */
@Keep
@IgnoreExtraProperties
data class InternalServicePricing(
    @DocumentId val id: String = "",
    val serviceId: String = "",
    val serviceName: String = "",
    val category: String = "",
    val unitPriceAfn: Double = 0.0,
    val bridgeUnitPriceAfn: Double = 10000.0,
    val active: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffRoleRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "RECEPTIONIST",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val preferredLanguage: String = "DARI",
    val calendarMode: String = "DUAL",
    val linkedPatientId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PatientLinkRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val requesterName: String = "",
    val requesterPhone: String = "",
    val status: String = "PENDING",
    val matchedPatientId: String? = null,
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PartnerLink(
    @DocumentId val id: String = "",
    val initiatorUid: String = "",
    val partnerUid: String = "",
    val partnerEmail: String = "",
    val status: String = "INVITED",
    val consentInitiator: Boolean = true,
    val consentPartner: Boolean = false,
    val shareAppointments: Boolean = true,
    val shareProgress: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PatientRecord(
    @DocumentId val id: String = "",
    val patientCode: String = "",
    val fullName: String = "",
    val phone: String = "",
    val age: Long = 0L,
    val gender: String = "",
    val address: String = "",
    val medicalHistory: String = "",
    val allergies: String = "",
    val linkedUserUid: String? = null,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ToothChartEntry(
    val toothFdi: Int = 11,
    val treatmentType: DentalTreatmentType = DentalTreatmentType.COMPOSITE,
    val notes: String = ""
)

@Keep
@IgnoreExtraProperties
data class TreatmentPlan(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val linkedUserUid: String = "",
    val patientName: String = "",
    val doctorName: String = "Dr. Ghulam Sediq Sakha",
    val treatmentName: String = "",
    val category: String = DentalTreatmentType.ROOT_CANAL.name,
    val teethFdi: List<Long> = emptyList(),
    val bridgeFlag: Boolean = false,
    val abutmentTeeth: List<Long> = emptyList(),
    val ponticTeeth: List<Long> = emptyList(),
    val unitCount: Long = 1L,
    val unitPriceAfn: Double = 0.0,
    val totalPriceAfn: Double = 0.0,
    val status: String = "PLANNED",
    val steps: List<String> = emptyList(),
    val completedSteps: List<String> = emptyList(),
    val clinicalNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val completedPercentage: Int
        get() = if (steps.isEmpty()) 0 else ((completedSteps.distinct().count { it in steps } * 100.0) / steps.size).toInt().coerceIn(0, 100)

    val remainingPercentage: Int
        get() = 100 - completedPercentage

    val pendingSteps: List<String>
        get() = steps.filterNot { it in completedSteps }

    val currentStage: String
        get() = pendingSteps.firstOrNull() ?: if (steps.isNotEmpty()) "Completed" else status
}

@Keep
@IgnoreExtraProperties
data class OrthodonticCase(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val linkedUserUid: String = "",
    val patientName: String = "",
    val caseType: String = "CLEARPATH_ALIGNER",
    val applianceInfo: String = "",
    val initialAssessment: String = "",
    val totalStages: Long = 12L,
    val currentStage: Long = 1L,
    val adjustmentHistory: List<String> = emptyList(),
    val clinicalNotes: String = "",
    val totalFeeAfn: Double = 0.0,
    val status: String = "ACTIVE",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ImplantRecord(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val linkedUserUid: String = "",
    val patientName: String = "",
    val toothFdi: Long = 36L,
    val siteDescription: String = "",
    val brandModel: String = "",
    val dimensions: String = "",
    val batchLotNumber: String = "",
    val surgeryDate: String = "",
    val healingStage: String = "Assessment",
    val prostheticStage: String = "Pending",
    val finalRestorationDone: Boolean = false,
    val followUpNotes: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LabOrder(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val workType: String = "CROWN",
    val laboratoryName: String = "",
    val orderDate: String = "",
    val expectedDelivery: String = "",
    val shade: String = "A2",
    val material: String = "Zirconia",
    val instructions: String = "",
    val costAfn: Double = 0.0,
    val status: String = "ORDERED",
    val fittingHistory: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrescribedMedicine(
    val medicineName: String = "",
    val dosage: String = "",
    val frequency: String = "",
    val duration: String = "",
    val quantity: String = "",
    val mealTiming: String = "After meals",
    val instructions: String = ""
)

@Keep
@IgnoreExtraProperties
data class PrescriptionRecord(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val linkedUserUid: String = "",
    val patientName: String = "",
    val patientAge: Long = 0L,
    val doctorName: String = "Dr. Ghulam Sediq Sakha",
    val visitDate: String = "",
    val medicines: List<PrescribedMedicine> = emptyList(),
    val notes: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MedicineItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "Antibiotic",
    val quantity: Long = 0L,
    val unit: String = "Box",
    val purchasePriceAfn: Double = 0.0,
    val sellingPriceAfn: Double = 0.0,
    val expiryDate: String = "",
    val batchNumber: String = "",
    val supplierName: String = "",
    val minStockLevel: Long = 5L,
    val movementHistory: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppointmentRecord(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val userId: String = "",
    val patientName: String = "",
    val patientPhone: String = "",
    val doctorName: String = "Dr. Ghulam Sediq Sakha",
    val serviceName: String = "",
    val dateIso: String = "",
    val solarHijriDate: String = "",
    val timeSlot: String = "",
    val visitType: String = "APPOINTMENT",
    val status: String = "REQUESTED",
    val depositRequiredAfn: Double = 150.0,
    val depositPaid: Boolean = false,
    val latePolicyMinutes: Long = 30L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MtuReceiptSubmission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val appointmentId: String = "",
    val invoiceId: String = "",
    val receiptImageBase64: String = "",
    val status: String = "PENDING_VERIFICATION",
    val approvedAmountAfn: Double = 0.0,
    val rejectionReason: String = "",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val userId: String = "",
    val invoiceId: String = "",
    val mtuReceiptId: String = "",
    val amountAfn: Double = 0.0,
    val currency: String = "AFN",
    val originalAmount: Double = 0.0,
    val exchangeRate: Double = 1.0,
    val method: String = "CASH",
    val status: String = "CONFIRMED",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InvoiceRecord(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val userId: String = "",
    val patientName: String = "",
    val subtotalAfn: Double = 0.0,
    val discountAfn: Double = 0.0,
    val totalAfn: Double = 0.0,
    val paidAfn: Double = 0.0,
    val balanceAfn: Double = 0.0,
    val status: String = "UNPAID",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExpenseRecord(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = "Clinic Supplies",
    val amountAfn: Double = 0.0,
    val currency: String = "AFN",
    val originalAmount: Double = 0.0,
    val exchangeRate: Double = 1.0,
    val notes: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupplierRecord(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val purchaseSummary: String = "",
    val totalDebtAfn: Double = 0.0,
    val paidAfn: Double = 0.0,
    val remainingBalanceAfn: Double = 0.0,
    val dueDate: String = "",
    val installmentsSummary: String = "",
    val overdue: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PayrollRecord(
    @DocumentId val id: String = "",
    val employeeName: String = "",
    val roleTitle: String = "",
    val baseSalaryAfn: Double = 0.0,
    val advancesAfn: Double = 0.0,
    val deductionsAfn: Double = 0.0,
    val netPaidAfn: Double = 0.0,
    val periodLabel: String = "",
    val status: String = "PENDING",
    val paidDate: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val targetUserId: String = "",
    val targetRole: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "GENERAL",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FeedbackComplaintItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val kind: String = "FEEDBACK",
    val rating: Long = 5L,
    val message: String = "",
    val adminResponse: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicMediaItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = "Smile Design",
    val description: String = "",
    val imageUri: String = "",
    val consentApproved: Boolean = true,
    val published: Boolean = true,
    val likesCount: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLogEntry(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val actorRole: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ClinicSettings(
    @DocumentId val id: String = "main",
    val bridgeDefaultPriceAfn: Double = 10000.0,
    val bookingDepositAfn: Double = 150.0,
    val lateNoShowMinutes: Long = 30L,
    val currencyRates: Map<String, Double> = mapOf(
        "AFN" to 1.0,
        "USD" to 70.0,
        "EUR" to 76.0,
        "PKR" to 0.25,
        "IRR" to 0.0017
    ),
    val enabledPaymentMethods: List<String> = listOf("CASH", "HESABPAY", "MTU"),
    val disabledModules: List<String> = emptyList(),
    val defaultLanguage: String = "DARI",
    val defaultCalendar: String = "DUAL",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BackupSnapshot(
    @DocumentId val id: String = "",
    val createdBy: String = "",
    val label: String = "",
    val summaryCount: Long = 0L,
    val payloadJson: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

object ClinicSeedData {
    val defaultServices: List<PublicService> = listOf(
        PublicService(
            id = "srv_implant",
            nameEn = "Dental Implants",
            nameFa = "ایمپلنت دندان",
            namePs = "د غاښونو امپلانټ",
            category = "Implantology",
            descriptionEn = "Permanent titanium implant placement and aesthetic prosthetic restoration for missing teeth.",
            descriptionFa = "جایگزینی دائمی دندان‌های از دست رفته با ایمپلنت‌های تایتانیوم و روکش‌های طبیعی.",
            descriptionPs = "د تیتانیوم امپلانټ په واسطه د تل پاتې غاښونو ایښودل.",
            generalInfoEn = "Includes 3D planning assessment, surgical fixture placement, healing phase, and crown restoration.",
            generalInfoFa = "شامل ارزیابی اولیه، جراحی کاشت، دوره ترمیم استخوان و نصب روکش نهایی.",
            generalInfoPs = "لومړنۍ ارزونه، جراحي ایښودل، د رغېدو پړاو او وروستنی تاج پکې شامل دي.",
            imageKey = "implant",
            published = true,
            sortOrder = 1
        ),
        PublicService(
            id = "srv_ortho",
            nameEn = "Orthodontics & ClearPath Aligners",
            nameFa = "ارتودنسی و الاینرهای شفاف ClearPath",
            namePs = "ارتودونسي او شفاف الاینرونه",
            category = "Orthodontics",
            descriptionEn = "Fixed braces and transparent ClearPath aligner systems for precise smile alignment.",
            descriptionFa = "منظم‌سازی دندان‌ها با سیستم‌های مدرن ارتودنسی ثابت و الاینرهای نامرئی ClearPath.",
            descriptionPs = "د غاښونو سمون په ثابت ارتودونسي او شفافو ClearPath الاینرونو سره.",
            generalInfoEn = "Digital bite analysis, periodic adjustments, and customized retention plans.",
            generalInfoFa = "بررسی تخصصی اکلوژن، تنظیمات دوره‌ای و نگهداری نتایج درمان.",
            generalInfoPs = "د غاښونو بشپړه ارزونه او منظم تعقیب.",
            imageKey = "ortho",
            published = true,
            sortOrder = 2
        ),
        PublicService(
            id = "srv_endo",
            nameEn = "Root Canal Treatment",
            nameFa = "عصب‌کشی تخصصی",
            namePs = "د غاښ عصب کشي",
            category = "Endodontics",
            descriptionEn = "Painless endodontic therapy using rotary instrumentation to preserve natural teeth.",
            descriptionFa = "درمان ریشه و عصب‌کشی بدون درد با وسایل روتاری برای حفظ دندان طبیعی.",
            descriptionPs = "په روټري وسایلو بې‌درده د ریښې درملنه.",
            generalInfoEn = "Multi-step canal disinfection, precision shaping, and hermetic obturation.",
            generalInfoFa = "پاکسازی کامل کانال، شکل‌دهی دقیق و پرکاری معیاری ریشه.",
            generalInfoPs = "د ریښې پاکول، برابرول او په معیاري ډول ډکول.",
            imageKey = "endo",
            published = true,
            sortOrder = 3
        ),
        PublicService(
            id = "srv_whitening",
            nameEn = "Teeth Whitening (Bleaching)",
            nameFa = "بلیچینگ و سفید کردن دندان",
            namePs = "د غاښونو سپینول",
            category = "Cosmetic",
            descriptionEn = "Enamel-safe clinical bleaching for brighter, natural smiles in a single session.",
            descriptionFa = "سفید کردن ایمن دندان‌ها با مواد استاندارد کلینیکی برای درخشش لبخند.",
            descriptionPs = "د غاښونو خوندي سپینول د کلینیکي معیاري موادو په واسطه.",
            generalInfoEn = "Gum protection barrier and sensitivity-controlled whitening protocol.",
            generalInfoFa = "همراه با محافظت کامل از لثه و کاهش حساسیت دندانی.",
            generalInfoPs = "د ورۍ بشپړ حفاظت او د حساسیت مخنیوی.",
            imageKey = "whitening",
            published = true,
            sortOrder = 4
        ),
        PublicService(
            id = "srv_veneer",
            nameEn = "Porcelain & Composite Veneers",
            nameFa = "وینیر و کامپوزیت وینیر",
            namePs = "وینیر او کامپوزیت وینیر",
            category = "Cosmetic",
            descriptionEn = "Custom smile design with high-gloss porcelain laminates and layered composite veneers.",
            descriptionFa = "طراحی لبخند با لمینت سرامیکی و کامپوزیت وینیر همرنگ دندان.",
            descriptionPs = "د سرامیک او کامپوزیت وینیر په واسطه د ښکلي مسکا ډیزاین.",
            generalInfoEn = "Tailored shade matching and minimally invasive tooth contouring.",
            generalInfoFa = "انتخاب رنگ طبیعی متناسب با چهره و تراش بسیار محافظه‌کارانه.",
            generalInfoPs = "د مخ سره سم طبیعی رنګ ټاکل.",
            imageKey = "veneer",
            published = true,
            sortOrder = 5
        ),
        PublicService(
            id = "srv_crowns",
            nameEn = "Zirconia, Porcelain & Metal Crowns & Bridges",
            nameFa = "پوش‌های زرکونیا، پورسلین، میتل و پل دندانی",
            namePs = "د زرکونیا، پورسلین او میتل پوښونه او پل",
            category = "Prosthodontics",
            descriptionEn = "Durable single crowns and multi-unit dental bridges engineered for strength and natural aesthetics.",
            descriptionFa = "ساخت روکش‌های مقاوم زرکونیا، پورسلین و پل‌های دندانی با دقت لابراتواری.",
            descriptionPs = "د لوړ کیفیت زرکونیا، پورسلین پوښونه او غاښي پلونه.",
            generalInfoEn = "Covers zirconia crowns, porcelain-fused crowns, and fixed bridges.",
            generalInfoFa = "شامل پوش زرکونیا، پورسلین، میتل و پل‌های ثابت.",
            generalInfoPs = "زرکونیا، پورسلین، میتل او ثابت پلونه پکې شامل دي.",
            imageKey = "crown",
            published = true,
            sortOrder = 6
        ),
        PublicService(
            id = "srv_denture",
            nameEn = "Partial & Complete Dentures",
            nameFa = "دینچرهای جزئی و کامل",
            namePs = "جزئي او بشپړ مصنوعي غاښونه",
            category = "Prosthodontics",
            descriptionEn = "Comfortable removable partial and full dentures restoring chewing function and facial support.",
            descriptionFa = "پروتزهای متحرک جزئی و کامل برای بازگرداندن قدرت جویدن و زیبایی چهره.",
            descriptionPs = "آرامه متحرک جزئي او بشپړ غاښونه د ژوولو د بېرته راوستلو لپاره.",
            generalInfoEn = "Precision impressions and balanced bite fitting appointments.",
            generalInfoFa = "قالب‌گیری دقیق و تنظیم راحت اکلوژن.",
            generalInfoPs = "دقیق قالب اخیستل او د غاښونو برابرول.",
            imageKey = "denture",
            published = true,
            sortOrder = 7
        ),
        PublicService(
            id = "srv_scaling",
            nameEn = "Scaling, Gum Care & Oral Surgery",
            nameFa = "جرم‌گیری، درمان لثه و کشیدن دندان",
            namePs = "جرم پاکول، د ورۍ درملنه او غاښ ایستل",
            category = "Periodontics & Surgery",
            descriptionEn = "Ultrasonic tartar removal, periodontal gum therapy, composite fillings, and gentle extractions.",
            descriptionFa = "جرم‌گیری اولتراسونیک، تداوی امراض لثه، پرکاری کامپوزیت و کشیدن بدون درد دندان.",
            descriptionPs = "الټراسونیک جرم پاکول، د ورۍ درملنه، کامپوزیت او بې‌درده غاښ ایستل.",
            generalInfoEn = "Preventive cleanings and minor oral surgical care in sterile conditions.",
            generalInfoFa = "رعایت کامل اصول استریل و مراقبت‌های پس از جراحی.",
            generalInfoPs = "د بشپړ تعقیم لاندې وقایوي او جراحي خدمات.",
            imageKey = "scaling",
            published = true,
            sortOrder = 8
        )
    )

    val defaultInternalPricing: List<InternalServicePricing> = listOf(
        InternalServicePricing("srv_endo", "srv_endo", "Root Canal Treatment", "Endodontics", 2000.0, 10000.0, true),
        InternalServicePricing("srv_crowns", "srv_crowns", "Zirconia / Porcelain Crown", "Prosthodontics", 4500.0, 10000.0, true),
        InternalServicePricing("srv_implant", "srv_implant", "Dental Implant", "Implantology", 25000.0, 10000.0, true),
        InternalServicePricing("srv_scaling", "srv_scaling", "Scaling & Polishing", "Periodontics", 1000.0, 10000.0, true),
        InternalServicePricing("srv_veneer", "srv_veneer", "Composite Veneer", "Cosmetic", 3500.0, 10000.0, true),
        InternalServicePricing("srv_ortho", "srv_ortho", "Orthodontics / ClearPath", "Orthodontics", 35000.0, 10000.0, true)
    )
}
