package com.industri.fleettrack.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Format nominal COD gaya Indonesia: Rp 125.000 / Rp 0 / Rp 75.000.
 */
object CurrencyFormatter {

    private val symbols = DecimalFormatSymbols(Locale("in", "ID"))
    private val formatter = DecimalFormat("#,##0", symbols)

    fun formatRupiah(amount: Double): String {
        val safeAmount = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        return "Rp ${formatter.format(safeAmount)}"
    }
}