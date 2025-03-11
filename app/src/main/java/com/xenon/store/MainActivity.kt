package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var sharedPreferences: SharedPreferences
    private val owner = "Dinico414"

    private val fileexplorerRepo = "FileExplorer"
    private val calculatorRepo = "Calculator"
    private val todoListRepo = "TodoList"
    private val xenonStoreRepo = "XenonStore"

    private val KEY_PRE_RELEASES = "pre_releases"
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val INSTALL_PERMISSION_CODE = 102
    private var hasCheckedForUpdates = false

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
        setupToolbar()
        setupButtons()
        checkAllUpdates()
    }

    private fun requestPermissionsIfNeeded() {
        if (!checkInstallPermission()) {
            requestInstallPermission()
        }
    }

    private fun checkInstallPermission(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    private fun requestInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivityForResult(intent, INSTALL_PERMISSION_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSTALL_PERMISSION_CODE) {
            if (checkInstallPermission()) {
                Toast.makeText(this, "Install permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Install permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAllUpdates() {
        val download1 = findViewById<Button>(id.download_1)
        val download2 = findViewById<Button>(id.download_2)
        val download3 = findViewById<Button>(id.download_3)
        val download4 = findViewById<Button>(id.download_4)

        checkUpdates(download1, xenonStoreRepo)
        checkUpdates(download2, todoListRepo)
        checkUpdates(download3, calculatorRepo)
        checkUpdates(download4, fileexplorerRepo)
        swipeRefreshLayout.isRefreshing = false
    }

    private fun checkUpdates(button: Button, repo: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()

        val isPreReleaseEnabled = sharedPreferences.getBoolean(KEY_PRE_RELEASES, false)
        val releasesUrl = "https://api.github.com/repos/$owner/$repo/releases"

        val request = Request.Builder().url(releasesUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("UpdateCheck", "Error checking for updates", e)
                    val errorMessage = when (e) {
                        is SocketTimeoutException -> "Connection timed out"
                        is UnknownHostException -> "No internet connection"
                        else -> "Update check failed: ${e.message}"
                    }
                    Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                    button.text = getString(R.string.open)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonArray = responseBody?.let { JSONArray(it) }

                    if (jsonArray != null && jsonArray.length() > 0) {
                        var latestRelease: JSONObject? = null
                        for (i in 0 until jsonArray.length()) {
                            val release = jsonArray.getJSONObject(i)
                            val isPreRelease = release.getBoolean("prerelease")

                            if (isPreReleaseEnabled || !isPreRelease) {
                                latestRelease = release
                                break // Found the first matching release
                            }
                        }

                        if (latestRelease != null) {
                            val assets = latestRelease.getJSONArray("assets")
                            if (assets.length() > 0) {
                                val asset = assets.getJSONObject(0)
                                val downloadUrl = asset.getString("browser_download_url")
                                val latestReleaseTag = latestRelease.getString("tag_name")
                                val packageName = packageNameFromRepo(repo)
                                val isInstalled = isAppInstalled(packageName)
                                val installedAppVersion = getInstalledAppVersion(packageName)

                                runOnUiThread {
                                    if (!isInstalled) {
                                        button.text = getString(R.string.install)
                                        button.visibility = View.VISIBLE
                                        fadeIn(button)
                                        button.setOnClickListener {
                                            downloadFile(
                                                getProgressBarId(repo),
                                                button,
                                                repo,
                                                downloadUrl
                                            )
                                        }
                                    } else if (installedAppVersion != null && isNewerVersion(
                                            latestReleaseTag,
                                            installedAppVersion
                                        )
                                    ) {
                                        button.text = getString(R.string.update)
                                        button.visibility = View.VISIBLE
                                        fadeIn(button)

                                        // Set click listener to download the update
                                        button.setOnClickListener {
                                            downloadFile(
                                                getProgressBarId(repo),
                                                button,
                                                repo,
                                                downloadUrl
                                            )
                                        }
                                    } else {
                                        if (button == findViewById(id.download_1)) {
                                            button.visibility = View.GONE
                                        } else {
                                            button.text = getString(R.string.open)

                                            button.setOnClickListener {
                                                val launchIntent =
                                                    packageManager.getLaunchIntentForPackage(
                                                        packageNameFromRepo(repo)
                                                    )
                                                startActivity(launchIntent)
                                            }
                                        }
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        applicationContext,
                                        "No assets found for $repo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (button == findViewById(id.download_1)) {
                                        button.visibility = View.GONE
                                    } else {
                                        button.text = getString(R.string.open)

                                        button.setOnClickListener {
                                            val launchIntent =
                                                packageManager.getLaunchIntentForPackage(
                                                    packageNameFromRepo(repo)
                                                )
                                            startActivity(launchIntent)
                                        }
                                    }
                                }
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "No suitable release found for $repo",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (button == findViewById(id.download_1)) {
                                    button.visibility = View.GONE
                                } else {
                                    button.text = getString(R.string.open)

                                    button.setOnClickListener {
                                        val launchIntent =
                                            packageManager.getLaunchIntentForPackage(
                                                packageNameFromRepo(repo)
                                            )
                                        startActivity(launchIntent)
                                    }
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "No releases found for $repo",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (button == findViewById(id.download_1)) {
                                button.visibility = View.GONE
                            } else {
                                button.text = getString(R.string.open)

                                button.setOnClickListener {
                                    val launchIntent =
                                        packageManager.getLaunchIntentForPackage(
                                            packageNameFromRepo(repo)
                                        )
                                    startActivity(launchIntent)
                                }
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Log.d("Update check", "Error on request: $response")
                        button.text = getString(R.string.open)
                    }
                }
            }
        })
    }

    private fun launchInstallPrompt(uri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(installIntent)
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
                //downloadFile(getProgressBarId(repo), button, repo)
            }
        }
    }

    private fun getProgressBarId(repo: String): Int {
        return when (repo) {
            xenonStoreRepo -> id.progressbar_1
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

    private fun String.getLatestReleaseDownloadUrl(
        repo: String,
        callback: (String?) -> Unit
    ) {
        val client = OkHttpClient()
        val url = "https://api.github.com/repos/${this}/$repo/releases"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Failed to get release info: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        try {
                            val jsonObject = JSONObject(responseBody)
                            val assets = jsonObject.getJSONArray("assets")
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.getString("name").endsWith(".apk")) {
                                    val downloadUrl = asset.getString("browser_download_url")
                                    callback(downloadUrl)
                                    return
                                }
                            }
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "No APK asset found for $repo",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            callback(null)
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "Error parsing release info: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            callback(null)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "Empty response from GitHub API",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        callback(null)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Failed to get release info: ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    callback(null)
                }
            }
        })
    }

    private fun downloadFile(
        progressBarId: Int,
        button: Button,
        repo: String,
        downloadUrl: String
    ) {
        val progressBar: LinearProgressIndicator = findViewById(progressBarId)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(downloadUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val fileName = "$repo.apk"
                    val tempFile = File(getExternalFilesDir(null), fileName)

                    try {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(4096)
                                var bytesRead: Int
                                var totalBytesRead: Long = 0
                                val fileSize: Long = response.body!!.contentLength()

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    val progress = ((totalBytesRead * 100) / fileSize).toInt()
                                    runOnUiThread {
                                        progressBar.progress = progress
                                    }
                                }
                            }
                        }

                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "Download completed",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                            progressBar.visibility = View.GONE
                            updateButtonText(button, repo)
                            val uri = getUriForFile(tempFile)
                            launchInstallPrompt(uri)
                            // Delete the file after installation
                            tempFile.deleteOnExit()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "Download failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            progressBar.visibility = View.GONE
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download failed", Toast.LENGTH_SHORT)
                            .show()
                        progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Download failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar.visibility = View.GONE
                }
            }
        })
    }

    private fun getUriForFile(file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Removed the logic that was here
        }



    private fun setupButtons() {
        val binding = findViewById<View>(android.R.id.content)
        val download1 = binding.findViewById<Button>(id.download_1)
        val download2 = binding.findViewById<Button>(id.download_2)
        val download3 = binding.findViewById<Button>(id.download_3)
        val download4 = binding.findViewById<Button>(id.download_4)

        // Set initial text and click listeners for each button
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
        val settingsItem = menu.findItem(R.id.settings)
        settingsItem.setOnMenuItemClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        val shareItem = menu.findItem(R.id.action_share)
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
}