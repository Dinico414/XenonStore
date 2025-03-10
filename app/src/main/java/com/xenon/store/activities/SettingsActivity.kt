package com.xenon.store.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.xenon.store.R
import com.xenon.store.SharedPreferenceManager
import com.xenon.todolist.databinding.ActivitySettingsBinding
import java.util.Locale

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val themeTitleList = arrayOf("Light", "Dark", "System")
    private lateinit var sharedPreferences: SharedPreferences
    private val KEY_PRE_RELEASES = "pre_releases"

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
                val sharedPref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                sharedPref.edit().clear().commit()
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

        // Correctly use SwitchMaterial
        val preReleasesSwitch = findViewById<SwitchMaterial>(R.id.release_switch)

        preReleasesSwitch.isChecked = sharedPreferences.getBoolean(KEY_PRE_RELEASES, false)

        preReleasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_PRE_RELEASES, isChecked).apply()
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