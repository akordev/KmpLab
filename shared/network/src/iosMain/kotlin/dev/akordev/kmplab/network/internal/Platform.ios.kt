package dev.akordev.kmplab.network.internal

import platform.UIKit.UIDevice

internal actual val platformName: String
    get() = with(UIDevice.currentDevice) { "$systemName $systemVersion" }
