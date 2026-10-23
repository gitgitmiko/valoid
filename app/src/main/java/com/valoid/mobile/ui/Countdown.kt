package com.valoid.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Sisa detik menuju [deadlineMillis], diperbarui tiap pergantian detik. */
@Composable
fun rememberSecondsLeft(deadlineMillis: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadlineMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    return ((deadlineMillis - now) / 1000).coerceAtLeast(0)
}

fun formatCountdown(seconds: Long): String {
    val days = seconds / 86_400
    val hms = "%02d:%02d:%02d".format(seconds % 86_400 / 3600, seconds % 3600 / 60, seconds % 60)
    return if (days > 0) "$days hari $hms" else hms
}
