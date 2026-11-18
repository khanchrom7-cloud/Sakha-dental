package com.example

import com.example.core.*
import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun solarHijriConversion_matchesKnownDates() {
        // 2024-03-20 is 1403-01-01 Hamal (Afghan Solar Hijri New Year)
        val nowruz = ClinicCalendar.gregorianToSolarHijri(2024, 3, 20)
        assertEquals(1403, nowruz.year)
        assertEquals(1, nowruz.month)
        assertEquals(1, nowruz.day)
        assertTrue(nowruz.formatLocalized(AppLanguage.DARI).contains("حمل"))
        assertTrue(nowruz.formatLocalized(AppLanguage.PASHTO).contains("وری"))
    }

    @Test
    fun format12HourTime_displaysAmPmCorrectly() {
        assertEquals("8:30 AM", ClinicCalendar.format12HourTime(8, 30, AppLanguage.ENGLISH))
        assertEquals("2:15 PM", ClinicCalendar.format12HourTime(14, 15, AppLanguage.ENGLISH))
        assertTrue(ClinicCalendar.format12HourTime(9, 0, AppLanguage.DARI).endsWith("ق.ظ"))
        assertTrue(ClinicCalendar.format12HourTime(16, 0, AppLanguage.DARI).endsWith("ب.ظ"))
    }

    @Test
    fun dentalChart_hasAll32ValidFdiTeethAndSixColors() {
        assertEquals(32, DentalChartCatalog.all32Teeth.size)
        assertTrue(DentalChartCatalog.isValidFdiTooth(18))
        assertTrue(DentalChartCatalog.isValidFdiTooth(36))
        assertFalse(DentalChartCatalog.isValidFdiTooth(99))
        assertEquals(6, DentalTreatmentType.entries.size)
        assertEquals(6, DentalTreatmentType.entries.map { it.colorHex }.distinct().size)
    }

    @Test
    fun treatmentPricing_multipliesSelectedTeethAndPreventsDuplicateCharges() {
        // 4 root canal teeth * 2,000 AFN = 8,000 AFN
        val total = ClinicBusinessRules.calculateTreatmentTotal(4, 2000.0)
        assertEquals(8000.0, total, 0.01)

        // Bridge calculation: 2 abutments + 1 pontic = 3 units * 10,000 = 30,000 AFN
        val (units, bridgeTotal) = ClinicBusinessRules.calculateBridgeTotal(listOf(14, 16), listOf(15), 10000.0)
        assertEquals(3, units)
        assertEquals(30000.0, bridgeTotal, 0.01)

        // Duplicate payment idempotency on invoice recompute
        val inv = InvoiceRecord(id = "inv1", subtotalAfn = 8000.0, discountAfn = 0.0, totalAfn = 8000.0)
        val dupPayments = listOf(
            PaymentRecord(id = "p1", invoiceId = "inv1", amountAfn = 2000.0, status = "CONFIRMED"),
            PaymentRecord(id = "p1", invoiceId = "inv1", amountAfn = 2000.0, status = "CONFIRMED"),
            PaymentRecord(id = "p2", invoiceId = "inv1", amountAfn = 6000.0, status = "CONFIRMED")
        )
        val recomputed = ClinicBusinessRules.recomputeInvoice(inv, dupPayments)
        assertEquals(8000.0, recomputed.paidAfn, 0.01)
        assertEquals(0.0, recomputed.balanceAfn, 0.01)
        assertEquals("PAID", recomputed.status)
    }

    @Test
    fun treatmentProgress_tracksCompletedVsRemainingSteps() {
        val steps = DentalTreatmentType.ROOT_CANAL.defaultSteps
        assertEquals(0, ClinicBusinessRules.calculateProgressPercentage(steps, emptyList()))
        assertEquals(50, ClinicBusinessRules.calculateProgressPercentage(steps, steps.take(3)))
        assertEquals(100, ClinicBusinessRules.calculateProgressPercentage(steps, steps))
    }

    @Test
    fun appointmentConflictAndVisitClassification_workAccurately() {
        val existing = listOf(
            AppointmentRecord(id = "a1", dateIso = "2026-11-18", timeSlot = "10:00 AM", doctorName = "Dr. Ghulam Sediq Sakha", status = "CONFIRMED", visitType = "APPOINTMENT"),
            AppointmentRecord(id = "a2", dateIso = "2026-11-18", timeSlot = "11:00 AM", doctorName = "Dr. Ghulam Sediq Sakha", status = "CANCELLED", visitType = "CANCELLED"),
            AppointmentRecord(id = "a3", dateIso = "2026-11-18", timeSlot = "1:00 PM", doctorName = "Dr. Ghulam Sediq Sakha", status = "COMPLETED", visitType = "ACTUAL_VISIT")
        )
        assertTrue(ClinicBusinessRules.hasAppointmentConflict(existing, "2026-11-18", "10:00 AM", "Dr. Ghulam Sediq Sakha"))
        assertFalse(ClinicBusinessRules.hasAppointmentConflict(existing, "2026-11-18", "11:00 AM", "Dr. Ghulam Sediq Sakha"))
        assertEquals(1, ClinicBusinessRules.countActualVisits(existing))
    }

    @Test
    fun accountingRules_excludePendingRejectedAndVoidedRecords() {
        val payments = listOf(
            PaymentRecord(id = "p1", amountAfn = 150.0, status = "CONFIRMED"),
            PaymentRecord(id = "p2", amountAfn = 5000.0, status = "PENDING_VERIFICATION"),
            PaymentRecord(id = "p3", amountAfn = 3000.0, status = "REJECTED")
        )
        assertEquals(150.0, ClinicBusinessRules.calculateConfirmedIncomeAfn(payments), 0.01)

        val expenses = listOf(
            ExpenseRecord(id = "e1", amountAfn = 1200.0, voided = false),
            ExpenseRecord(id = "e2", amountAfn = 4500.0, voided = true)
        )
        assertEquals(1200.0, ClinicBusinessRules.calculateActiveExpensesAfn(expenses), 0.01)
    }

    @Test
    fun multiCurrencyAndPricePrivacy_enforcedStrictly() {
        val afnFromUsd = CurrencyManager.convertToAfn(100.0, 70.0)
        assertEquals(7000.0, afnFromUsd, 0.01)
        assertEquals(100.0, CurrencyManager.convertFromAfn(7000.0, ClinicCurrency.USD, 70.0), 0.01)

        // Verify PublicService contains NO monetary rate/price properties
        val publicFieldNames = PublicService::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(publicFieldNames.any { "price" in it || "cost" in it || "fee" in it || "rate" in it })

        // Verify all 3 languages have complete non-blank strings
        AppLanguage.entries.forEach { lang ->
            val s = LocalizationProvider.getStrings(lang)
            assertTrue(s.clinicName.isNotBlank())
            assertTrue(s.clinicMotto.isNotBlank())
            assertTrue(s.doctorProfileMenu.isNotBlank())
            assertTrue(s.ourServicesMenu.isNotBlank())
        }
    }
}
