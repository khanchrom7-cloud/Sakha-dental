package com.example.domain

import com.example.core.ClinicCalendar
import kotlin.math.round

data class FdiTooth(
    val number: Int,
    val quadrant: Int,
    val nameEn: String
)

object DentalChartCatalog {
    val upperRight = listOf(18, 17, 16, 15, 14, 13, 12, 11)
    val upperLeft = listOf(21, 22, 23, 24, 25, 26, 27, 28)
    val lowerRight = listOf(48, 47, 46, 45, 44, 43, 42, 41)
    val lowerLeft = listOf(31, 32, 33, 34, 35, 36, 37, 38)

    val all32Teeth: List<Int> = upperRight + upperLeft + lowerRight + lowerLeft

    fun isValidFdiTooth(fdi: Int): Boolean = fdi in all32Teeth

    fun toothLabel(fdi: Int): String {
        val pos = fdi % 10
        val arch = if (fdi / 10 in 1..2) "Upper" else "Lower"
        val side = if (fdi / 10 == 1 || fdi / 10 == 4) "Right" else "Left"
        val type = when (pos) {
            1 -> "Central Incisor"
            2 -> "Lateral Incisor"
            3 -> "Canine"
            4 -> "1st Premolar"
            5 -> "2nd Premolar"
            6 -> "1st Molar"
            7 -> "2nd Molar"
            8 -> "3rd Molar"
            else -> "Tooth"
        }
        return "$arch $side $type (#$fdi)"
    }
}

object ClinicBusinessRules {

    /**
     * Pricing Formula: Total price = Number of selected teeth * Unit price.
     * Example: 4 root canal treatments * 2,000 AFN = 8,000 AFN.
     */
    fun calculateTreatmentTotal(selectedTeethCount: Int, unitPriceAfn: Double): Double {
        if (selectedTeethCount <= 0 || unitPriceAfn <= 0.0) return 0.0
        return round(selectedTeethCount * unitPriceAfn * 100.0) / 100.0
    }

    /**
     * Bridge Pricing Formula: (abutment teeth + pontic teeth) * configurable bridge unit price (default 10,000 AFN).
     */
    fun calculateBridgeTotal(
        abutmentTeeth: List<Int>,
        ponticTeeth: List<Int>,
        bridgeUnitPriceAfn: Double = 10000.0
    ): Pair<Int, Double> {
        val units = (abutmentTeeth + ponticTeeth).distinct().size.coerceAtLeast(1)
        val total = round(units * bridgeUnitPriceAfn * 100.0) / 100.0
        return units to total
    }

    /**
     * Progress Percentage calculated strictly from confirmed completed steps.
     */
    fun calculateProgressPercentage(steps: List<String>, completedSteps: List<String>): Int {
        if (steps.isEmpty()) return 0
        val confirmed = completedSteps.distinct().count { it in steps }
        return ((confirmed * 100.0) / steps.size).toInt().coerceIn(0, 100)
    }

    /**
     * Conflict detection for appointments: active appointments on same date + doctor + timeSlot collide.
     */
    fun hasAppointmentConflict(
        existingAppointments: List<AppointmentRecord>,
        dateIso: String,
        timeSlot: String,
        doctorName: String,
        ignoreAppointmentId: String? = null
    ): Boolean {
        return existingAppointments.any { appt ->
            appt.id != ignoreAppointmentId &&
                appt.dateIso == dateIso &&
                appt.timeSlot.equals(timeSlot, ignoreCase = true) &&
                appt.doctorName.equals(doctorName, ignoreCase = true) &&
                appt.status !in listOf("CANCELLED", "NO_SHOW")
        }
    }

    /**
     * Duplicate patient check before registration.
     */
    fun findPotentialDuplicates(
        patients: List<PatientRecord>,
        fullName: String,
        phone: String
    ): List<PatientRecord> {
        val normPhone = phone.filter { it.isDigit() }.takeLast(9)
        val normName = fullName.trim().lowercase()
        return patients.filter { p ->
            val pPhone = p.phone.filter { it.isDigit() }.takeLast(9)
            (normPhone.length >= 7 && pPhone == normPhone) ||
                (normName.length >= 3 && p.fullName.trim().lowercase() == normName)
        }
    }

    /**
     * Accounting rule:
     * Pending or Rejected MTU receipts NEVER count as income or reduce balances.
     * Only CONFIRMED payments count.
     */
    fun calculateConfirmedIncomeAfn(payments: List<PaymentRecord>): Double {
        return payments.filter { it.status == "CONFIRMED" }.sumOf { it.amountAfn }
    }

    /**
     * Voided expenses are excluded from active expense totals.
     */
    fun calculateActiveExpensesAfn(expenses: List<ExpenseRecord>): Double {
        return expenses.filter { !it.voided }.sumOf { it.amountAfn }
    }

    /**
     * Recompute invoice paid/balance/status cleanly without duplicate payments.
     */
    fun recomputeInvoice(
        invoice: InvoiceRecord,
        confirmedPaymentsForInvoice: List<PaymentRecord>
    ): InvoiceRecord {
        val validPayments = confirmedPaymentsForInvoice
            .filter { it.status == "CONFIRMED" && it.invoiceId == invoice.id }
            .distinctBy { it.id.ifBlank { "${it.mtuReceiptId}_${it.amountAfn}_${it.createdAt}" } }
        val paid = validPayments.sumOf { it.amountAfn }
        val netTotal = (invoice.subtotalAfn - invoice.discountAfn).coerceAtLeast(0.0)
        val balance = (netTotal - paid).coerceAtLeast(0.0)
        val newStatus = when {
            invoice.status == "VOID" -> "VOID"
            netTotal == 0.0 && paid == 0.0 -> "UNPAID"
            balance <= 0.01 && paid > 0.0 -> "PAID"
            paid > 0.0 -> "PARTIAL"
            else -> "UNPAID"
        }
        return invoice.copy(
            totalAfn = netTotal,
            paidAfn = paid,
            balanceAfn = balance,
            status = newStatus
        )
    }

    /**
     * Visit classification rule: Do not count an appointment as an actual clinic visit
     * unless visitType != APPOINTMENT / CANCELLED and status in [CHECKED_IN, COMPLETED].
     */
    fun countActualVisits(appointments: List<AppointmentRecord>): Int {
        return appointments.count {
            it.visitType in listOf("ACTUAL_VISIT", "CONSULTATION", "COMPLETED_TREATMENT", "FOLLOW_UP") &&
                it.status in listOf("CHECKED_IN", "COMPLETED")
        }
    }

    fun buildAppointmentDraft(
        userId: String,
        patientId: String,
        patientName: String,
        patientPhone: String,
        serviceName: String,
        dateIso: String,
        timeSlot: String,
        depositRequiredAfn: Double = 150.0,
        latePolicyMinutes: Long = 30L,
        notes: String = ""
    ): AppointmentRecord {
        return AppointmentRecord(
            patientId = patientId,
            userId = userId,
            patientName = patientName.trim(),
            patientPhone = patientPhone.trim(),
            doctorName = "Dr. Ghulam Sediq Sakha",
            serviceName = serviceName.trim(),
            dateIso = dateIso,
            solarHijriDate = ClinicCalendar.fromIsoDate(dateIso).toNumericString(),
            timeSlot = timeSlot,
            visitType = "APPOINTMENT",
            status = "REQUESTED",
            depositRequiredAfn = depositRequiredAfn,
            depositPaid = false,
            latePolicyMinutes = latePolicyMinutes,
            notes = notes.trim()
        )
    }
}
