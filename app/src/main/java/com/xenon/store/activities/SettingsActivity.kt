package com.xenon.store.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.xenon.store.R
import com.xenon.store.SharedPreferenceManager
import com.xenon.store.databinding.ActivitySettingsBinding
import java.util.Locale
import androidx.core.content.edit
import androidx.core.net.toUri


class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val themeTitleList = arrayOf("Light", "Dark", "System")
    private lateinit var sharedPreferences: SharedPreferences
    private val preReleaseKey = "pre_releases"
    private val amoledDarkKey = "amoled_dark"
    private val languageKey = "selected_language"
    lateinit var manager: SharedPreferenceManager
    private var currentTheme: Int = 0

    private val supportedLocales = listOf(
        Locale("en"),
        Locale("de")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        manager = SharedPreferenceManager(this)
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adjustBottomMargin(binding.layoutMain)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        setupViews()

        val themeSelectionValue = findViewById<TextView>(R.id.theme_selection_value)

        var checkedTheme = manager.theme
        themeSelectionValue.text = themeTitleList[manager.theme]

        val themeDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Select Theme")
            .setPositiveButton("OK") { _, _ ->
                manager.theme = checkedTheme
                AppCompatDelegate.setDefaultNightMode(manager.themeFlag[checkedTheme])
                themeSelectionValue.text = themeTitleList[checkedTheme]
            }
            .setSingleChoiceItems(themeTitleList, checkedTheme) { _, which ->
                checkedTheme = which
            }
            .setCancelable(false)

        binding.themeSelectionHolder.setOnClickListener {
            themeDialog.show()
        }

        binding.clearButtonHolder.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val sharedPref = getSharedPreferences(packageName, MODE_PRIVATE)
                    sharedPref.edit() { clear() }
                    this.restartApplication()
                }
                .setNegativeButton(R.string.cancel, null)
                .setMessage(R.string.clear_data_dialog)
                .show()
        }
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val preReleasesSwitch = findViewById<MaterialSwitch>(R.id.release_switch)

        preReleasesSwitch.isChecked = sharedPreferences.getBoolean(preReleaseKey, false)

        preReleasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit() { putBoolean(preReleaseKey, isChecked) }
        }

        val amoledDarkSwitch = findViewById<MaterialSwitch>(R.id.amoled_dark_switch)
        amoledDarkSwitch.isChecked = sharedPreferences.getBoolean(amoledDarkKey, false)
        amoledDarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            manager.amoledDark = isChecked
            applyTheme(true)
        }

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            binding.aboutText.text = getString(R.string.about_text, versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            binding.aboutText.text = getString(R.string.about_text, "Unknown")
        }
        // Add this block to handle the click on the "about" LinearLayout
        binding.aboutHolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://xenonware.com/impressum".toUri())
            startActivity(intent)
        }
    }

    private fun applyTheme(recreateActivity: Boolean = false) {
        AppCompatDelegate.setDefaultNightMode(manager.themeFlag[manager.theme])

        if (currentTheme == 0) currentTheme = manager.theme
        val newTheme = if (manager.amoledDark) R.style.Theme_Xenon_Amoled else manager.theme

        if (currentTheme != newTheme) {
            currentTheme = newTheme
            setTheme(newTheme)
            if (recreateActivity) recreate()
        }
    }

    private fun setupViews() {
        binding.languageSelectionValue.text = Locale.getDefault().displayLanguage
        val currentLocale = getSavedLocale() ?: Locale.getDefault()
        binding.languageSelectionValue.text = currentLocale.displayLanguage

        binding.languageSelectionHolder.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                showLanguageDialog()
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
        }
    }

    private fun updateLocale(locale: Locale) {
        Locale.setDefault(locale)
        saveLocale(locale) // Save the selected locale
        val resources = this.resources
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Restart the activity to apply the new locale
        recreate()
    }

    private fun saveLocale(locale: Locale) {
        sharedPreferences.edit() { putString(languageKey, locale.language) }
    }

    private fun getSavedLocale(): Locale? {
        val languageCode = sharedPreferences.getString(languageKey, null)
        return if (languageCode != null) {
            Locale(languageCode)
        } else {
            null
        }
    }


    private fun showLanguageDialog() {
        val languageList = supportedLocales.map { it.getDisplayName(it) }.toTypedArray()
        val currentLocale = Locale.getDefault()
        val currentLanguageIndex = supportedLocales.indexOfFirst { it == currentLocale }

        var selectedLanguageIndex = currentLanguageIndex
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(languageList, currentLanguageIndex) { _, which ->
                selectedLanguageIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                if (selectedLanguageIndex != -1) {
                    val selectedLocale = supportedLocales[selectedLanguageIndex]
                    updateLocale(selectedLocale)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

fun Context.restartApplication() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}