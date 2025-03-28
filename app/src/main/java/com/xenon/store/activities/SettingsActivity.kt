package com.xenon.store.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.xenon.store.R
import com.xenon.store.SharedPreferenceManager
import com.xenon.store.databinding.ActivitySettingsBinding
import java.util.Locale


class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val themeTitleList = arrayOf("Light", "Dark", "System")
    private lateinit var sharedPreferences: SharedPreferences
    private val preReleaseKey = "pre_releases"
    private val amoledDarkKey = "amoled_dark"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adjustBottomMargin(binding.layoutMain)
        setupViews()

        val themeSelectionValue = findViewById<TextView>(R.id.theme_selection_value)

        val sharedPreferenceManager = SharedPreferenceManager(this)
        var checkedTheme = sharedPreferenceManager.theme
        themeSelectionValue.text = themeTitleList[sharedPreferenceManager.theme]

        val themeDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Select Theme")
            .setPositiveButton("OK") { _, _ ->
                sharedPreferenceManager.theme = checkedTheme
                AppCompatDelegate.setDefaultNightMode(sharedPreferenceManager.themeFlag[checkedTheme])
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
            val builder = AlertDialog.Builder(this)
            builder.setPositiveButton(R.string.yes) { _, _ ->
                val sharedPref = getSharedPreferences(packageName, MODE_PRIVATE)
                sharedPref.edit().clear().apply()
                this.restartApplication()
            }
            builder.setNegativeButton(R.string.cancel, null)
            builder.setMessage(R.string.clear_data_dialog)
            builder.show()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)


        val preReleasesSwitch = findViewById<MaterialSwitch>(R.id.release_switch)

        preReleasesSwitch.isChecked = sharedPreferences.getBoolean(preReleaseKey, false)

        preReleasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(preReleaseKey, isChecked).apply()
        }

        val amoledDarkSwitch = findViewById<MaterialSwitch>(R.id.amoled_dark_switch)
        amoledDarkSwitch.isChecked = sharedPreferences.getBoolean(amoledDarkKey, false)
        amoledDarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(amoledDarkKey, isChecked).apply()
            applyAmoledDark(isChecked)
        }
        applyAmoledDark(sharedPreferences.getBoolean(amoledDarkKey, false))
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            binding.aboutText!!.text = getString(R.string.about_text, versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            binding.aboutText!!.text = getString(R.string.about_text, "Unknown")
        }
        // Add this block to handle the click on the "about" LinearLayout
        binding.aboutHolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://xenonware.com/impressum"))
            startActivity(intent)
        }
    }

    private fun applyAmoledDark(enable: Boolean) {
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            if (enable) {
                window.decorView.setBackgroundColor(Color.BLACK)
            } else {
                window.decorView.setBackgroundColor(resources.getColor(com.xenon.commons.accesspoint.R.color.surfaceContainerLowest))
            }
        }
    }


    private fun setupViews() {
        binding.languageSelectionValue.text = Locale.getDefault().displayLanguage
        binding.languageSelectionHolder.setOnClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        }


    }
}

fun Context.restartApplication() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}