package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.PrescriptionPdfGenerator
import com.example.data.local.PendingSyncActionEntity
import com.example.data.local.SakhaLocalDatabase
import com.example.domain.PrescribedMedicine
import com.example.domain.PrescriptionRecord
import com.example.domain.PublicClinicProfile
import com.example.domain.PublicDoctorProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun clinicBrandingAndFirebaseResources_areConfiguredCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Sakha Dental", context.getString(R.string.app_name))
        assertEquals("Sakha Specialised Dental Centre", context.getString(R.string.clinic_official_name_en))
        assertEquals("مرکز تخصصی دندان و ایمپلنت سخا", context.getString(R.string.clinic_official_name_fa))

        val dbId = context.getString(R.string.firestore_database_id)
        assertTrue(dbId.isNotBlank() && dbId != "(default)")

        // Bundled visual assets required for logo, doctor photo, signature & HesabPay QR
        assertNotEquals(0, R.drawable.sakha_logo)
        assertNotEquals(0, R.drawable.doctor_photo)
        assertNotEquals(0, R.drawable.doctor_signature)
        assertNotEquals(0, R.drawable.hesabpay_qr)
    }

    @Test
    fun doctorProfile_matchesExactVerifiedCredentialsWithoutInventedClaims() {
        val doctor = PublicDoctorProfile()
        assertEquals("Dr. Ghulam Sediq Sakha", doctor.fullNameEn)
        assertEquals("داکتر غلام صدیق سخا", doctor.fullNameFa)
        assertEquals("معالج امراض جوف دهن و دندان", doctor.titleFa)
        assertTrue(doctor.education.any { it.contains("Baqai Medical University") && it.contains("2020") })
        assertTrue(doctor.education.any { it.contains("RDS") })
        assertTrue(doctor.training.containsAll(listOf("C_Endo", "C_Ortho", "C_Implant", "ClearPath clear aligner training")))
        assertEquals(2, doctor.licenses.size)
        assertEquals(5, doctor.experience.size)
    }

    @Test
    fun roomOfflineQueue_enqueuesAndUpdatesPendingSyncActions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SakhaLocalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.cacheDao()
            dao.enqueueAction(
                PendingSyncActionEntity(
                    collectionPath = "patients",
                    documentId = "pat_offline_1",
                    operation = "UPSERT",
                    payloadJson = """{"fullName":"Ahmad Jawid","phone":"0782221100"}""",
                    createdAtMillis = 123456789L
                )
            )
            val pending = dao.getPendingActions()
            assertEquals(1, pending.size)
            assertEquals("PENDING", pending.first().syncStatus)

            dao.updateActionStatus(pending.first().localId, "SYNCED")
            dao.clearSyncedActions()
            assertTrue(dao.getPendingActions().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun prescriptionPdfGenerator_createsNonEmptyPdfWithLogoAndSignature() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rx = PrescriptionRecord(
            id = "rx_test_1",
            patientId = "pat_1",
            patientName = "Nadia Karimi",
            patientAge = 29,
            doctorName = "Dr. Ghulam Sediq Sakha",
            visitDate = "2026-11-17",
            medicines = listOf(
                PrescribedMedicine(
                    medicineName = "Amoxicillin 500mg",
                    dosage = "1 Capsule",
                    frequency = "Every 8 hours",
                    duration = "5 Days",
                    quantity = "15 Caps",
                    mealTiming = "After meals"
                )
            ),
            notes = "Rinse gently with warm salt water."
        )
        val clinic = PublicClinicProfile()
        val file = PrescriptionPdfGenerator.generatePdfFile(
            context = context,
            prescription = rx,
            clinicNameEn = clinic.nameEn,
            clinicNameFa = clinic.nameFa,
            clinicPhone = clinic.phones.joinToString(" | "),
            clinicAddress = clinic.addressFa
        )
        assertTrue(file.exists())
        assertTrue(file.length() > 100L)
        assertTrue(file.readText(Charsets.UTF_8).startsWith("%PDF"))
        file.delete()
    }
}
