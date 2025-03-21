package com.xenon.store

import android.util.Log
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import com.xenon.store.R.id
import com.xenon.store.activities.SettingsActivity
import com.xenon.store.databinding.ActionUpdateButtonBinding
import com.xenon.store.databinding.ActivityMainBinding
import com.xenon.store.fragments.AppListFragment
import com.xenon.store.viewmodel.AppListViewModel
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var bindingSmall: ActionUpdateButtonBinding? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appListFragment: AppListFragment
    private lateinit var appListModel: AppListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferenceManager = SharedPreferenceManager(this)
        AppCompatDelegate.setDefaultNightMode(sharedPreferenceManager.themeFlag[sharedPreferenceManager.theme])
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        setupAppListFragment()
        setupToolbar()
        setupCollapsingToolbar()
    }

    private fun setupAppListFragment() {
        appListFragment = binding.appListFragment.getFragment()
        appListModel = ViewModelProvider(appListFragment)[AppListViewModel::class.java]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val item: MenuItem = menu.findItem(id.action_custom_button)
        val actionView: View? = item.actionView
        bindingSmall = actionView?.let { ActionUpdateButtonBinding.bind(it) }

        val settingsItem = menu.findItem(id.settings)
        settingsItem.setOnMenuItemClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        val shareItem = menu.findItem(id.action_share)
        shareItem.setOnMenuItemClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "xenonware.com/store"
                )
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
            true
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val appItem = appListModel.storeAppItem
        if (appItem.state == AppEntryState.DOWNLOADING) {
            binding.downloadBtnStore.visibility = View.VISIBLE
            binding.progressbarStore.visibility = View.VISIBLE
        }
        else if (appItem.state == AppEntryState.INSTALLED_AND_OUTDATED) {
            binding.downloadBtnStore.visibility = View.VISIBLE
        }

        appListModel.storeAppItemLive.observe(this) { _ ->
            Log.d("ayy", appItem.state.toString())
            when (appItem.state) {
                AppEntryState.NOT_INSTALLED,
                AppEntryState.INSTALLED -> {
                    binding.downloadBtnStore.visibility = View.GONE
                    binding.progressbarStore.visibility = View.GONE
                }
                AppEntryState.DOWNLOADING -> {
                    binding.downloadBtnStore.visibility = View.VISIBLE
                    binding.progressbarStore.progress = appItem.bytesDownloaded.toInt()
                    binding.progressbarStore.max = appItem.fileSize.toInt()
                    binding.progressbarStore.visibility = View.VISIBLE
                }
                AppEntryState.INSTALLED_AND_OUTDATED -> {
                    binding.downloadBtnStore.text = getString(R.string.update)
                    binding.downloadBtnStore.visibility = View.VISIBLE
                    binding.progressbarStore.visibility = View.GONE
                }
            }
        }
        binding.downloadBtnStore.setOnClickListener {
            if (appItem.state == AppEntryState.INSTALLED_AND_OUTDATED) {
                binding.downloadBtnStore.text = ""

                if (appItem.downloadUrl == "") {
                    showToast("Failed to fetch download url of ${appItem.name}")
                    return@setOnClickListener
                }

                // Try downloading
                appItem.state = AppEntryState.DOWNLOADING
                appListModel.update(appItem, AppListChangeType.STATE_CHANGE)

                appListFragment.downloadAppItem(appItem)
            }
        }
    }

    private fun setupCollapsingToolbar() {
        val appBarLayout = binding.appbar
        val layoutParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = AppBarLayout.Behavior()
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                return true
            }
        })
        layoutParams.behavior = behavior

        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            val totalScrollRange = appBar.totalScrollRange
            val currentOffset = abs(verticalOffset)
            val percentage = (currentOffset.toFloat() / totalScrollRange) * 100

            updateFrameButtonVisibility(percentage)
            updateFrameButtonSmallVisibility(percentage)
        }
    }

    private fun updateFrameButtonVisibility(percentage: Float) {
        val collapsingToolbar = binding.collapsingToolbar
        val frameButton = binding.frameButton

        if (collapsingToolbar == null) {
            frameButton.visibility = View.GONE
        } else {
            when {
                percentage >= 10f -> {
                    val alphaPercentage = (percentage - 20f) / (100f - 60f)
                    frameButton.alpha = 1f - alphaPercentage
                    frameButton.visibility = View.VISIBLE
                    if (percentage == 100f) {
                        frameButton.visibility = View.GONE
                    }
                }
                percentage == 0f -> {
                    frameButton.alpha = 1f
                    frameButton.visibility = View.VISIBLE
                }
                else -> {
                    frameButton.alpha = 1f
                    frameButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateFrameButtonSmallVisibility(percentage: Float) {
        val collapsingToolbar = binding.collapsingToolbar
        val frameButtonSmall = bindingSmall?.frameButtonSmall

        if (collapsingToolbar == null) {
            frameButtonSmall?.visibility = View.VISIBLE
        } else {
            frameButtonSmall?.let {
                when {
                    percentage >= 0f -> {
                        val alphaPercentage = (percentage - 80f) / (100f - 80f)
                        it.alpha = alphaPercentage
                        it.visibility = View.VISIBLE
                        if (percentage == 100f) {
                            it.visibility = View.VISIBLE
                        }
                    }
                    percentage == 0f -> {
                        it.alpha = 0f
                        it.visibility = View.GONE
                    }
                    else -> {
                        it.alpha = 0f
                        it.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun applyAmoledDark(enable: Boolean) {
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            if (enable) {
                window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
            } else {
                window.decorView.setBackgroundColor(resources.getColor(com.xenon.commons.accesspoint.R.color.surfaceContainerLowest)) // Replace dark_background with your dark theme color
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}