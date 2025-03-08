package com.xenon.store.activities

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.xenon.store.R
import com.xenon.store.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val KEY_PRE_RELEASES = "pre_releases"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        // Correctly use SwitchMaterial
        val preReleasesSwitch = findViewById<SwitchMaterial>(R.id.release_switch)

        preReleasesSwitch.isChecked = sharedPreferences.getBoolean(KEY_PRE_RELEASES, false)

        preReleasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_PRE_RELEASES, isChecked).apply()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun showAboutDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("About")
            .setMessage("This is a simple app store for my apps.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Visit Website") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dinico414.github.io/"))
                startActivity(browserIntent)
            }
            .show()
    }
}