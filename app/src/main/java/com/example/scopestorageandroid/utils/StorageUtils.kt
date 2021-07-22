package com.example.scopestorageandroid.utils

import android.os.Build

fun MinSdk29() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

inline fun <T> sdk29AndUp(onSdk29: () -> T): T? {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onSdk29()
    } else null
}