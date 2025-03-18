package com.xenon.store

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xenon.store.R.id
import com.xenon.store.activities.SettingsActivity
import com.xenon.store.databinding.ActionUpdateButtonBinding
import com.xenon.store.databinding.ActivityMainBinding
import com.xenon.store.fragments.AppListFragment
import com.xenon.store.viewmodel.AppListViewModel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var bindingSmall: ActionUpdateButtonBinding? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appListModel: AppListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferenceManager = SharedPreferenceManager(this)
        AppCompatDelegate.setDefaultNightMode(sharedPreferenceManager.themeFlag[sharedPreferenceManager.theme])
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        setupAppList()
        setupToolbar()
        setupCollapsingToolbar()
    }

    fun setupAppList() {
        val fragment = binding.appListFragment.getFragment<AppListFragment>()
        appListModel = fragment.getViewModel()
    }

    fun loadAppList() {

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
}