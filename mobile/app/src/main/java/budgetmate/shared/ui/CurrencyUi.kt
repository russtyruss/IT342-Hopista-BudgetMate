package edu.cit.hopista.budgetmate.shared.ui

import java.util.Locale

object CurrencyUi {

    fun symbolFor(code: String?): String {
        return when ((code ?: "PHP").uppercase(Locale.ROOT)) {
            "PHP" -> "₱"
            "USD" -> "$"
            "EUR" -> "€"
            "JPY" -> "¥"
            "GBP" -> "£"
            "AUD" -> "$"
            "CAD" -> "$"
            "SGD" -> "$"
            "HKD" -> "$"
            else -> (code ?: "PHP").uppercase(Locale.ROOT)
        }
    }

    fun displayLabel(code: String): String {
        val normalized = code.uppercase(Locale.ROOT)
        return "${symbolFor(normalized)} $normalized"
    }

    fun codeFromDisplayLabel(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isBlank()) {
            return "PHP"
        }
        return trimmed.substringAfterLast(' ').uppercase(Locale.ROOT)
    }

    fun formatAmount(amount: Double, code: String?): String {
        return "${symbolFor(code)}%.2f".format(Locale.US, amount)
    }
}
