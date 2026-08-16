package com.nerdfever.wdbtile

import android.content.Context
import android.provider.Settings

/*
 * The one thing this app does: read and flip the global setting behind
 * Developer options > Wireless debugging.
 *
 * Writing it needs WRITE_SECURE_SETTINGS, which no on-device UI can grant -
 * see the manifest for the one-time adb grant. Reading it is free.
 */

/** The global setting wireless debugging lives behind. 1 is on, 0 is off. */
private const val ADB_WIFI_SETTING = "adb_wifi_enabled"

/** Whether wireless debugging is currently on. */
fun wdbIsOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, ADB_WIFI_SETTING, 0) == 1

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
