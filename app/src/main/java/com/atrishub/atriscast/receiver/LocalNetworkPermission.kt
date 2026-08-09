package com.atrishub.atriscast.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object LocalNetworkPermission {
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean {
        if (!isRequired()) return true
        return context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }
}
