package dev.akordev.kmplab.network.internal

import android.os.Build

internal actual val platformName: String
    get() = "Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}"
