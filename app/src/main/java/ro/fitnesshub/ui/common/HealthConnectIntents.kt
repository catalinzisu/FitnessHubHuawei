package ro.fitnesshub.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.util.AppLog

/** Intents that leave the app for Health Connect or Google Play. */
object HealthConnectIntents {

    /** Opens Health Connect's own screen, where the user can change permissions. */
    fun openSettings(context: Context) {
        try {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            AppLog.w("Intents", "Health Connect settings screen not found")
        }
    }

    /** Opens the Play Store page to install or update Health Connect (Android 13 and lower). */
    fun openPlayStore(context: Context) {
        val pkg = HealthConnectSource.HEALTH_CONNECT_PACKAGE
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg&url=healthconnect%3A%2F%2Fonboarding".toUri())
            .setPackage("com.android.vending")
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri()))
        }
    }
}
