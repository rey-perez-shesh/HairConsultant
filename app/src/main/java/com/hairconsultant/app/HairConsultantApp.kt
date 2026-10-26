    package com.hairconsultant.app

import android.app.Application
import com.hairconsultant.app.di.AppContainer

class HairConsultantApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        AppCheckSetup.install()
        container = AppContainer(this)
    }
}
