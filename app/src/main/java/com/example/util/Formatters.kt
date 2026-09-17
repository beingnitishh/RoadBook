package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object Formatters {
    private val numberFormatThreadLocal = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    private val sdfDateThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    private val sdfDateWithDayThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    }

    private val sdfShortDateThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM", Locale.getDefault())
    }

    // Cache formatted date strings by timestamp to avoid SimpleDateFormat allocations during fast list scrolling
    private val dateCache = ConcurrentHashMap<Long, String>(128)
    private val dateWithDayCache = ConcurrentHashMap<Long, String>(64)

    fun formatCurrency(amount: Double, symbol: String = "₹"): String {
        val formatter = numberFormatThreadLocal.get() ?: return "$symbol ${amount.toLong()}"
        return "$symbol ${formatter.format(amount)}"
    }

    fun formatDate(timestamp: Long): String {
        return dateCache.computeIfAbsent(timestamp) { ts ->
            sdfDateThreadLocal.get()?.format(Date(ts)) ?: ""
        }
    }

    fun formatDateWithDay(timestamp: Long): String {
        return dateWithDayCache.computeIfAbsent(timestamp) { ts ->
            sdfDateWithDayThreadLocal.get()?.format(Date(ts)) ?: ""
        }
    }

    fun formatRelativeDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val oneDayMs = 24 * 60 * 60 * 1000L

        if (diffMs in 0 until oneDayMs) {
            val calNow = Calendar.getInstance()
            val calTarget = Calendar.getInstance().apply { timeInMillis = timestamp }
            if (calNow.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR) &&
                calNow.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR)
            ) {
                return "Today"
            }
        }

        if (diffMs in oneDayMs until (2 * oneDayMs)) {
            val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val calTarget = Calendar.getInstance().apply { timeInMillis = timestamp }
            if (calYesterday.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR) &&
                calYesterday.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR)
            ) {
                return "Yesterday"
            }
        }

        return sdfShortDateThreadLocal.get()?.format(Date(timestamp)) ?: formatDate(timestamp)
    }
}
