package com.xenon.store.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.util.Locale


@Suppress("SameParameterValue")
open class BaseActivity : AppCompatActivity() {

    fun adjustBottomMargin(layoutMain: View?) {
        adjustBottomMargin(layoutMain, null)
    }

    private fun adjustBottomMargin(layoutMain: View?, floatingButton: ExtendedFloatingActionButton?) {
        if (layoutMain == null)
            return
        val layoutParams = layoutMain.layoutParams as MarginLayoutParams
        val navigationBarHeight = getNavigationBarHeight()

        val targetMargin = if (navigationBarHeight == 0) {
            15.dpToPx()
        } else {
            0
        }

        layoutParams.bottomMargin = targetMargin
        layoutMain.layoutParams = layoutParams

        if (floatingButton != null) {
            if (navigationBarHeight == 0) {
                14.dpToPx()
            } else {
                14.dpToPx()
            }
        }
    }
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

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")

        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

}
