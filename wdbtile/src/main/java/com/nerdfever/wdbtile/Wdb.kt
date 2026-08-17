package com.nerdfever.wdbtile

import android.content.Context
import android.provider.Settings

/*
 * The one thing this app does: read, flip and TELL THE TRUTH ABOUT the global
 * setting behind Developer options > Wireless debugging.
 *
 * Writing it needs WRITE_SECURE_SETTINGS, which no on-device UI can grant -
 * see the manifest for the one-time adb grant. Reading it is free.
 *
 * The truth-telling part: the setting records INTENT, but adbd's wireless
 * service can die underneath it, leaving "on" in the database and nothing
 * listening on the network. adbd publishes its live TLS port in a system
 * property only while it is actually listening, so the service's REALITY is
 * readable too - and the app shows three states, not two:
 *
 *   OFF     the setting is off
 *   ON      the setting is on AND adbd is listening (the port is shown)
 *   WEDGED  the setting is on but nothing is listening - toggle to revive
 */

/** The global setting wireless debugging lives behind. 1 is on, 0 is off. */
private const val ADB_WIFI_SETTING = "adb_wifi_enabled"

/** The property adbd publishes its live TLS port in - absent or -1 when dead. */
private const val TLS_PORT_PROPERTY = "service.adb.tls.port"

/** What the display shows; see the header for what each state means. */
enum class WdbState { OFF, ON, WEDGED }

/** Whether wireless debugging is currently on - the SETTING, the intent. */
fun wdbIsOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, ADB_WIFI_SETTING, 0) == 1

/**
 * The port adbd is actually listening on, or -1 when it is not listening.
 *
 * Read by running getprop rather than via android.os.SystemProperties, which
 * is hidden API. A dead process, an unreadable property and a wedged adbd all
 * come back -1, which is the right answer for all three.
 */
fun wdbLivePort(): Int =
    try {
        Runtime.getRuntime().exec(arrayOf("getprop", TLS_PORT_PROPERTY))
            .inputStream.bufferedReader().use { it.readText() }
            .trim().toIntOrNull() ?: -1
    } catch (_: Exception) {
        -1
    }

/** The whole truth: intent AND reality, as one displayable state. */
fun wdbState(context: Context): WdbState =
    when {
        !wdbIsOn(context) -> WdbState.OFF
        wdbLivePort() > 0 -> WdbState.ON
        else -> WdbState.WEDGED
    }

/**
 * Turn wireless debugging on or off. True on success; false when the
 * one-time permission grant has not been done yet.
 */
fun wdbSet(context: Context, on: Boolean): Boolean =
    try {
        Settings.Global.putInt(context.contentResolver, ADB_WIFI_SETTING, if (on) 1 else 0)
        true
    } catch (_: SecurityException) {
        false
    }
