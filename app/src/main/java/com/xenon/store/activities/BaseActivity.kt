package com.xenon.store.activities

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale


@Suppress("SameParameterValue")
open class BaseActivity : AppCompatActivity() {


    override fun attachBaseContext(newBase: Context) {
        val sharedPreferences = newBase.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val languageCode = sharedPreferences.getString("selected_language", null)
        val locale = if (languageCode != null) Locale(languageCode) else null

        val context = locale?.let {
            Locale.setDefault(it)
            val config = newBase.resources.configuration
            config.setLocale(it)
            newBase.createConfigurationContext(config)
        } ?: newBase

        super.attachBaseContext(context)
    }
}