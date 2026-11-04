package com.example.ratecalc

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ALL CALCULATION RULES LIVE HERE (matched to the original app's screenshots).
 *  - Total      = rate * quantity            (always)
 *  - CC count   = quantity / tablets-per-CC  (if the product is "sold as CC", quantity already is CC)
 *  - Pot count  = quantity / tablets-per-Pot (if "sold as Pot", quantity already is Pot)
 */
object Calc {
    val units = listOf("Normal", "CC", "Pot")

    fun lineTotal(p: Product): Double = p.rate * p.quantity

    fun ccCount(p: Product): Double = when {
        p.soldUnit == "CC" -> p.quantity.toDouble()
        p.cc > 0 -> p.quantity / p.cc
        else -> 0.0
    }

    fun potCount(p: Product): Double = when {
        p.soldUnit == "Pot" -> p.quantity.toDouble()
        p.pot > 0 -> p.quantity / p.pot
        else -> 0.0
    }

    /** "20", "25 CC", "3 Pot" */
    fun qtyLabel(p: Product): String = when (p.soldUnit) {
        "CC" -> "${p.quantity} CC"
        "Pot" -> "${p.quantity} Pot"
        else -> "${p.quantity}"
    }

    fun invoiceText(items: List<Product>): String {
        val sb = StringBuilder("INVOICE\n\n")
        items.forEach { p ->
            sb.append(p.name).append("  ").append(money(p.rate)).append(" x ").append(qtyLabel(p))
                .append(" = ").append(money(lineTotal(p))).append('\n')
        }
        sb.append("\nGrand Total: ").append(money(items.sumOf { lineTotal(it) }))
        return sb.toString()
    }
}

fun money(d: Double): String = "৳" + String.format(Locale.US, "%.2f", d)

fun num(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.2f", d)

fun formatDate(t: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(t))

data class CsvRow(val name: String, val rate: Double, val cc: Double, val pot: Double, val unit: String, val qty: Int)

object Csv {
    const val HEADER = "name,rate,cc,pot,soldUnit,quantity"

    fun export(list: List<Product>): String {
        val sb = StringBuilder(HEADER).append('\n')
        list.forEach { p ->
            sb.append(esc(p.name)).append(',').append(p.rate).append(',').append(p.cc).append(',')
                .append(p.pot).append(',').append(p.soldUnit).append(',').append(p.quantity).append('\n')
        }
        return sb.toString()
    }

    private fun esc(s: String) = if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun parse(textIn: String): List<CsvRow> {
        val text = textIn.removePrefix("\uFEFF")
        val rows = ArrayList<CsvRow>()
        text.lines().forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val c = splitLine(line)
            if (i == 0 && c[0].trim().equals("name", ignoreCase = true)) return@forEachIndexed
            val name = c[0].trim()
            if (name.isEmpty()) return@forEachIndexed
            val unit = c.getOrNull(4)?.trim().orEmpty()
            rows.add(
                CsvRow(
                    name = name,
                    rate = c.getOrNull(1)?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    cc = c.getOrNull(2)?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    pot = c.getOrNull(3)?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    unit = Calc.units.firstOrNull { it.equals(unit, true) } ?: "Normal",
                    qty = c.getOrNull(5)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                )
            )
        }
        return rows
    }

    private fun splitLine(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
