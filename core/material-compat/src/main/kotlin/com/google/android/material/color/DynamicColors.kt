package com.google.android.material.color

import app.exitune.core.ui.utils.isAtLeastAndroid12

@Suppress("unused")
object DynamicColors {
    @JvmStatic
    fun isDynamicColorAvailable() = isAtLeastAndroid12
}
