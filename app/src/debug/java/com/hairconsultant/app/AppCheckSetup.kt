package com.hairconsultant.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use App Check's debug provider: the first run logs a debug token to Logcat
 * ("DebugAppCheckProvider"), which has to be added under Firebase console -> App Check -> Apps ->
 * Manage debug tokens before enforcement is turned on.
 */
object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
