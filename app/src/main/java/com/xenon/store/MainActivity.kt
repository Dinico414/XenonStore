@file:Suppress("DEPRECATION")

package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
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
    private val amoledDarkKey = "amoled_dark"

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
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply the theme in case it was changed in settings while the app was in the background
        val oldTheme = getThemeFromPreferences() // Store the current theme
        applyTheme()
        val newTheme = getThemeFromPreferences() // Get the theme after applying changes

        if (oldTheme != newTheme) {
            recreate() // Only recreate if the theme actually changed
        }
    }
    private fun getThemeFromPreferences(): Int {
        return if (sharedPreferences.getBoolean(amoledDarkKey, false)) {
            R.style.Theme_Xenon_Amoled
        } else {
            R.style.Theme_Xenon
        }
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

        setupSelfUpdate()

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
    }

    private fun setupSelfUpdate() {
        val appItem = appListModel.storeAppItem
        if (appItem.state == AppEntryState.DOWNLOADING) {
            binding.download1.text = ""
            binding.download1.visibility = View.VISIBLE
            binding.progressbar1.visibility = View.VISIBLE
            bindingSmall?.download1Image?.visibility = View.VISIBLE
            bindingSmall?.progressbar1Circle?.visibility = View.VISIBLE
        } else if (appItem.state == AppEntryState.INSTALLED_AND_OUTDATED) {
            binding.download1.visibility = View.VISIBLE
            binding.progressbar1.visibility = View.GONE
            bindingSmall?.download1Image?.visibility = View.VISIBLE
            bindingSmall?.progressbar1Circle?.visibility = View.GONE
            bindingSmall?.download1Image?.setImageResource(R.drawable.download_24)
        }

        appListModel.storeAppItemLive.observe(this) { _ ->
            when (appItem.state) {
                AppEntryState.NOT_INSTALLED,
                AppEntryState.INSTALLED -> {
                    binding.download1.visibility = View.GONE
                    binding.progressbar1.visibility = View.GONE
                    bindingSmall?.download1Image?.visibility = View.GONE
                    bindingSmall?.progressbar1Circle?.visibility = View.GONE
                    bindingSmall?.download1Image?.setImageResource(0)
                }

                AppEntryState.DOWNLOADING -> {
                    binding.download1.visibility = View.VISIBLE
                    binding.progressbar1.progress = appItem.bytesDownloaded.toInt()
                    binding.progressbar1.max = appItem.fileSize.toInt()
                    binding.progressbar1.visibility = View.VISIBLE

                    bindingSmall?.download1Image?.visibility = View.VISIBLE
                    bindingSmall?.progressbar1Circle?.progress = appItem.bytesDownloaded.toInt()
                    bindingSmall?.progressbar1Circle?.max = appItem.fileSize.toInt()
                    bindingSmall?.progressbar1Circle?.visibility = View.VISIBLE
                    bindingSmall?.download1Image?.setImageResource(0)
                }

                AppEntryState.INSTALLED_AND_OUTDATED -> {
                    binding.download1.text = getString(R.string.update)
                    binding.download1.visibility = View.VISIBLE
                    binding.progressbar1.visibility = View.GONE

                    bindingSmall?.download1Image?.visibility = View.VISIBLE
                    bindingSmall?.progressbar1Circle?.visibility = View.GONE
                    bindingSmall?.download1Image?.setImageResource(R.drawable.download_24)
                }
            }
        }

        val onClickListener = object : View.OnClickListener {
            override fun onClick(p0: View?) {
                if (appItem.state == AppEntryState.INSTALLED_AND_OUTDATED) {
                    binding.download1.text = ""

                    if (appItem.downloadUrl == "") {
                        showSnackbar(getString(R.string.failed_to_find, appItem.name))
                        return
                    }
                    appItem.state = AppEntryState.DOWNLOADING
                    appListModel.update(appItem, AppListChangeType.STATE_CHANGE)

                    appListFragment.downloadAppItem(appItem)
                }
            }
        }

        binding.download1.setOnClickListener(onClickListener)
        bindingSmall?.download1Image?.setOnClickListener(onClickListener)
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

    private fun applyTheme() {
        val isAmoledDarkEnabled = sharedPreferences.getBoolean(amoledDarkKey, false)
        val theme = if (isAmoledDarkEnabled) R.style.Theme_Xenon_Amoled else R.style.Theme_Xenon
        setTheme(theme)
    }


    private fun showSnackbar(message: String) {
        runOnUiThread {
            val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            val backgroundDrawable =
                resources.getDrawable(com.xenon.commons.accesspoint.R.drawable.tile_popup, null)
            val snackbarView = snackbar.view

            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(
                params.leftMargin,
                params.topMargin,
                params.rightMargin,
                params.bottomMargin + resources.getDimensionPixelSize(R.dimen.snackBar_margin)
            )
            snackbarView.layoutParams = params

            // Set the background
            snackbar.view.background = backgroundDrawable
            // Customize text color
            snackbar.setTextColor(
                resources.getColor(
                    com.xenon.commons.accesspoint.R.color.onError,
                    null
                )
            )

            snackbar.setBackgroundTint(
                resources.getColor(
                    com.xenon.commons.accesspoint.R.color.error,
                    null
                )
            )
            snackbar.show()
        }
    }
}