package com.atrishub.atriscast

import android.app.Application
import com.atrishub.atriscast.receiver.ReceiverRuntime

class AtrisCastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReceiverRuntime.initialize(this)
    }
}
