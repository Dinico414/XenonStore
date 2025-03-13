package com.xenon.store

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xenon.store.R.id
import com.xenon.store.activities.SettingsActivity
import com.xenon.store.databinding.ActivityMainBinding
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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var sharedPreferences: SharedPreferences
    private val owner = "Dinico414"

    private val fileexplorerRepo = "FileExplorer"
    private val calculatorRepo = "Calculator"
    private val todoListRepo = "TodoList"
    private val xenonStoreRepo = "XenonStore"

    private val preReleaseKey = "pre_releases"
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var installPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferenceManager = SharedPreferenceManager(this)
        AppCompatDelegate.setDefaultNightMode(sharedPreferenceManager.themeFlag[sharedPreferenceManager.theme])
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        swipeRefreshLayout = findViewById(id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            checkAllUpdates()
        }
        installPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                if (checkInstallPermission()) {
                    Toast.makeText(this, "Install permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Install permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        setupToolbar()
        setupButtons()
        checkAllUpdates()
    }

    private fun checkInstallPermission(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    private fun launchInstallPrompt(uri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        installPermissionLauncher.launch(installIntent)
    }

    private fun checkAllUpdates() {
        val download1 = findViewById<Button>(id.download_1)
        val download1image = findViewById<ImageButton>(id.download_1_image)
        val download2 = findViewById<Button>(id.download_2)
        val download3 = findViewById<Button>(id.download_3)
        val download4 = findViewById<Button>(id.download_4)

        checkUpdates(download1, download1image, xenonStoreRepo)
        checkUpdates(download2, null, todoListRepo)
        checkUpdates(download3, null, calculatorRepo)
        checkUpdates(download4, null, fileexplorerRepo)
        swipeRefreshLayout.isRefreshing = false
    }

    private fun checkUpdates(button: Button, imageButton: ImageButton?, repo: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("UpdateCheck", "Error checking for updates", e)
                    val message = when (e) {
                        is SocketTimeoutException -> "Connection timed out"
                        is UnknownHostException -> "No internet connection"
                        else -> "Update check failed: ${e.message}"
                    }
                    showToast(message)
                    updateButton(button, getString(R.string.open))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread { updateButton(button, getString(R.string.open)) }
                    return
                }
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    runOnUiThread { showToast("Empty response body for $repo") }
                    return
                }
                try {
                    val releases = JSONArray(responseBody)
                    val latestRelease = findLatestRelease(releases)

                    if (latestRelease != null) {
                        val assets = latestRelease.getJSONArray("assets")
                        if (assets.length() > 0) {
                            val asset = assets.getJSONObject(0)
                            handleUpdate(
                                button,
                                imageButton,
                                repo,
                                asset.getString("browser_download_url"),
                                latestRelease.getString("tag_name")
                            )
                        } else {
                            runOnUiThread { showToast("No assets found for $repo") }
                        }
                    } else {
                        runOnUiThread { showToast("No suitable release found for $repo") }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showToast("Error parsing response for $repo: ${e.message}")
                        Log.e("UpdateCheck", "Error parsing response for $repo", e)
                    }
                }
            }
        })
    }

    private fun findLatestRelease(releases: JSONArray): JSONObject? {
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            val isPreRelease = release.getBoolean("prerelease")
            val showPreReleases = sharedPreferences.getBoolean(preReleaseKey, false)

            if (!isPreRelease || showPreReleases) {
                return release
            }
        }
        return null
    }


    @SuppressLint("SetTextI18n")
    private fun handleUpdate(
        button: Button,
        imageButton: ImageButton?,
        repo: String,
        downloadUrl: String,
        latestTag: String
    ) {
        val packageName = packageNameFromRepo(repo)
        val installedVersion = getInstalledAppVersion(packageName)
        val isInstalled = isAppInstalled(packageName)

        val cardId = when (repo) {
//            xenonStoreRepo -> id.card_1
            todoListRepo -> id.card_2
            calculatorRepo -> id.card_3
            fileexplorerRepo -> id.card_4
            else -> null
        }

        val cardView = cardId?.let { findViewById<CardView>(it) }
        val linearLayoutId = when (repo) {
//            xenonStoreRepo -> id.version_1
            todoListRepo -> id.version_2
            calculatorRepo -> id.version_3
//            fileexplorerRepo -> id.version_4
            else -> null
        }
        val linearLayout = linearLayoutId?.let { cardView?.findViewById<LinearLayout>(it) }
        runOnUiThread {
            val versionTextView1 = linearLayout?.findViewById<TextView>(
                when (repo) {
//                    xenonStoreRepo -> id.installed_version_1
                    todoListRepo -> id.installed_version_2
                    calculatorRepo -> id.installed_version_3
//                    fileexplorerRepo -> id.installed_version_4
                    else -> null
                }!!
            )
            val versionTextView2 = linearLayout?.findViewById<TextView>(
                when (repo) {
//                    xenonStoreRepo -> id.new_version_1
                    todoListRepo -> id.new_version_2
                    calculatorRepo -> id.new_version_3
//                    fileexplorerRepo -> id.new_version_4
                    else -> null
                }!!
            )

            when {
                !isInstalled -> {
                    setupButton(button, getString(R.string.install), repo, downloadUrl)
                    imageButton?.let { setupImageButton(it, repo, downloadUrl) }
                    linearLayout?.visibility = View.GONE
                }

                installedVersion != null && isNewerVersion(latestTag, installedVersion) -> {
                    setupButton(button, getString(R.string.update), repo, downloadUrl)
                    imageButton?.let { setupImageButton(it, repo, downloadUrl) }
                    linearLayout?.visibility = View.VISIBLE
                    versionTextView1?.apply {
                        text = "v.$installedVersion"
                        paintFlags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        alpha = 0.5f
                        visibility = View.VISIBLE
                    }
                    versionTextView2?.apply {
                        text = "v.$latestTag"
                        visibility = View.VISIBLE
                    }
                }

                else -> {
                    setupLaunchButton(button, repo)
                    imageButton?.let { setupLaunchImageButton(it, repo) }
                    linearLayout?.visibility = View.GONE
                }
            }
        }
    }

    private fun setupButton(button: Button, text: String, repo: String, downloadUrl: String) {
        button.text = text
        button.visibility = View.VISIBLE
        fadeIn(button)
        button.setOnClickListener {
            downloadFile(
                ProgressBarType.LINEAR.getProgressBarId(repo),
                button,
                null, // Pass null for imageButton
                repo,
                downloadUrl // Pass the downloadUrl
            )
        }
    }

    private fun setupImageButton(imageButton: ImageButton, repo: String, downloadUrl: String) {
        imageButton.visibility = View.VISIBLE
        fadeIn(imageButton)
        imageButton.setOnClickListener {
            downloadFile(
                ProgressBarType.CIRCULAR.getProgressBarId(repo), // Use CIRCULAR here
                null, // Pass null for button
                imageButton,
                repo,
                downloadUrl // Pass the downloadUrl
            )
        }
    }

    private fun setupLaunchButton(button: Button, repo: String) {
        button.text = getString(R.string.open)
        button.setOnClickListener {
            startActivity(packageManager.getLaunchIntentForPackage(packageNameFromRepo(repo)))
        }
    }

    private fun setupLaunchImageButton(imageButton: ImageButton, repo: String) {
        imageButton.setOnClickListener {
            startActivity(packageManager.getLaunchIntentForPackage(packageNameFromRepo(repo)))
        }
    }

    private fun updateButton(button: Button, text: String) {
        button.text = text
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonText(button: Button, repo: String) {
        val packageName = packageNameFromRepo(repo)
        val isInstalled = isAppInstalled(packageName)
        getInstalledAppVersion(packageName)

        if (isInstalled) {
            button.text = getString(R.string.open)
            button.setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                startActivity(launchIntent)
            }
        } else {
            button.text = getString(R.string.install)
            button.setOnClickListener {
            }
        }
    }

    private fun ProgressBarType.getProgressBarId(repo: String): Int {
        return when (repo) {
            xenonStoreRepo -> when (this) {
                ProgressBarType.LINEAR -> id.progressbar_1
                ProgressBarType.CIRCULAR -> id.progressbar_1_circle
            }

            todoListRepo -> id.progressbar_2
            calculatorRepo -> id.progressbar_3
            fileexplorerRepo -> id.progressbar_4
            else -> throw IllegalArgumentException("Unknown repository: $repo")
        }
    }

    private fun packageNameFromRepo(repo: String): String {
        return when (repo) {
            xenonStoreRepo -> "com.xenon.store"
            todoListRepo -> "com.xenon.todolist"
            calculatorRepo -> "com.xenon.calculator"
            fileexplorerRepo -> "com.xenon.fileexplorer"
            else -> throw IllegalArgumentException("Unknown repository: $repo")
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getInstalledAppVersion(packageName: String): String? {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isNewerVersion(latestVersion: String, installedVersion: String): Boolean {
        val latestParts = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val installedParts = installedVersion.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(latestParts.size, installedParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val installedPart = installedParts.getOrElse(i) { 0 }

            if (latestPart > installedPart) {
                return true
            } else if (latestPart < installedPart) {
                return false
            }
        }
        return false
    }

    private fun fadeIn(view: View) {
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 500
        view.startAnimation(fadeIn)
    }

    private fun downloadFile(
        progressBarId: Int,
        button: Button?,
        imageButton: ImageButton?,
        repo: String,
        downloadUrl: String
    ) {
        val linearProgressBar: LinearProgressIndicator? =
            if (progressBarId == id.progressbar_1 || progressBarId == id.progressbar_2 || progressBarId == id.progressbar_3 || progressBarId == id.progressbar_4) {
                findViewById<LinearProgressIndicator>(progressBarId).apply {
                    visibility = View.VISIBLE
                    progress = 0
                }
            } else {
                null
            }

        val circularProgressBar: CircularProgressIndicator? =
            if (progressBarId == id.progressbar_1_circle) {
                findViewById<CircularProgressIndicator>(progressBarId).apply {
                    visibility = View.VISIBLE
                    progress = 0
                }
            } else {
                null
            }

        // Store the original text and drawable for later restoration
        val originalButtonText = button?.text?.toString()
        val originalImageDrawable = imageButton?.drawable

        // Update button and imageButton during download
        runOnUiThread {
            button?.text = "" // Clear the text
            imageButton?.setImageDrawable(null) // Remove the image source
        }

        val request = Request.Builder().url(downloadUrl).build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    "Download failed".showError(
                        progressBarId,
                        button,
                        imageButton,
                        originalButtonText,
                        originalImageDrawable
                    )
                    return
                }

                val contentLength = response.body?.contentLength() ?: -1
                var downloadedBytes: Long = 0
                val buffer = ByteArray(8192) // 8KB buffer
                val inputStream = response.body?.byteStream()
                val tempFile = File(getExternalFilesDir(null), "$repo.apk")
                val outputStream = FileOutputStream(tempFile)

                try {
                    var bytesRead: Int
                    while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (contentLength > 0) {
                            (downloadedBytes * 100 / contentLength).toInt()
                        } else {
                            0
                        }

                        runOnUiThread {
                            linearProgressBar?.progress = progress
                            circularProgressBar?.progress = progress
                        }
                    }
                    runOnUiThread {
                        linearProgressBar?.visibility = View.GONE
                        circularProgressBar?.visibility = View.GONE
                        onDownloadComplete(
                            tempFile,
                            linearProgressBar ?: circularProgressBar!!, // Use the non-null progress bar
                            button,
                            imageButton,
                            repo,
                            originalImageDrawable
                        )
                    }
                } catch (e: Exception) {
                    "Download failed".showError(
                        progressBarId,
                        button,
                        imageButton,
                        originalButtonText,
                        originalImageDrawable
                    )
                } finally {
                    inputStream?.close()
                    outputStream.close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                "Download failed".showError(
                    progressBarId,
                    button,
                    imageButton,
                    originalButtonText,
                    originalImageDrawable
                )
            }
        })
    }
    private fun String.showError(
        progressBarId: Int,
        button: Button?,
        imageButton: ImageButton?,
        originalButtonText: String?,
        originalImageDrawable: Drawable?
    ) {
        runOnUiThread {
            Toast.makeText(applicationContext, this, Toast.LENGTH_SHORT).show()
            findViewById<LinearProgressIndicator>(progressBarId).visibility = View.GONE
            findViewById<CircularProgressIndicator>(progressBarId).visibility = View.GONE
            button?.apply {
                visibility = View.VISIBLE
                text = originalButtonText // Restore original text
            }
            imageButton?.apply {
                visibility = View.VISIBLE
                setImageDrawable(originalImageDrawable) // Restore original image
            }
        }
    }

    private fun onDownloadComplete(
        tempFile: File,
        progressBar: View, // Changed type to View
        button: Button?,
        imageButton: ImageButton?,
        repo: String,
        originalImageDrawable: Drawable?
    ) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            if (button != null) {
                button.visibility = View.VISIBLE
                updateButtonText(button, repo)
            }
            if (imageButton != null) {
                imageButton.visibility = View.VISIBLE
                imageButton.setImageDrawable(originalImageDrawable)
            }
            val uri = FileProvider.getUriForFile(
                this@MainActivity,
                "${packageName}.provider",
                tempFile
            )
            if (checkInstallPermission()) {
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(installIntent)
            } else {
                launchInstallPrompt(uri)
            }
        }
    }

    private fun setupButtons() {
        val binding = findViewById<View>(android.R.id.content)
        val download1 = binding.findViewById<Button>(id.download_1)
        val download2 = binding.findViewById<Button>(id.download_2)
        val download3 = binding.findViewById<Button>(id.download_3)
        val download4 = binding.findViewById<Button>(id.download_4)

        updateButtonText(download1, xenonStoreRepo)
        updateButtonText(download2, todoListRepo)
        updateButtonText(download3, calculatorRepo)
        updateButtonText(download4, fileexplorerRepo)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
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

    enum class ProgressBarType {
        LINEAR, CIRCULAR
    }


}