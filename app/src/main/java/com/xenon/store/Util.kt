package com.xenon.store

import android.content.res.Resources
import android.os.Build

class Util {
    companion object {
        fun getCurrentLanguage(resources: Resources): String {
            return resources.configuration.locales.get(0).language
        }
    }
}