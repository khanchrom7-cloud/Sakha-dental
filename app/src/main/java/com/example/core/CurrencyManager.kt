package com.example.core

import java.text.NumberFormat
import java.util.Locale

enum class ClinicCurrency(
    val code: String,
    val symbol: String,
    val defaultRateToAfn: Double
) {
    AFN("AFN", "AFN", 1.0),
    USD("USD", "$", 70.0),
    EUR("EUR", "€", 76.0),
    PKR("PKR", "PKR", 0.25),
    IRR("IRR", "IRR", 0.0017);

    companion object {
        fun fromCode(raw: String): ClinicCurrency =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: AFN
    }
}

object CurrencyManager {
    fun convertToAfn(amount: Double, rateToAfn: Double): Double {
        if (amount <= 0.0 || rateToAfn <= 0.0) return 0.0
        return kotlin.math.round(amount * rateToAfn * 100.0) / 100.0
    }

    fun convertFromAfn(amountAfn: Double, targetCurrency: ClinicCurrency, rateToAfn: Double): Double {
        if (amountAfn <= 0.0 || rateToAfn <= 0.0) return 0.0
        if (targetCurrency == ClinicCurrency.AFN) return amountAfn
        return kotlin.math.round((amountAfn / rateToAfn) * 100.0) / 100.0
    }

    fun formatAfn(amountAfn: Double, language: AppLanguage): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = if (amountAfn % 1.0 == 0.0) 0 else 2
        }
        val formatted = nf.format(amountAfn)
        return when (language) {
            AppLanguage.DARI -> "$formatted افغانی"
            AppLanguage.PASHTO -> "$formatted افغانۍ"
            AppLanguage.ENGLISH -> "$formatted AFN"
        }
    }

    fun formatCurrency(amount: Double, currency: ClinicCurrency): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        }
        return "${nf.format(amount)} ${currency.code}"
    }
}
