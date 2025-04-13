package com.xenon.store

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.appcompat.app.AppCompatDelegate

class SharedPreferenceManager(context: Context) {
    private val preference = context.getSharedPreferences(
        "MyPrefs",
        MODE_PRIVATE
    )
    private val editor = preference.edit()

    private val keyTheme = "theme"
    private val keyAmoledDark = "amoled_dark"

    var theme
        get() = preference.getInt(keyTheme, 2)
        set(value){
            editor.putInt(keyTheme,value)
            editor.commit()
        }

    var amoledDark
        get() = preference.getBoolean(keyAmoledDark, false)
        set(value) {
            editor.putBoolean(keyAmoledDark, value)
            editor.apply()
        }

    val themeFlag = arrayOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )
}