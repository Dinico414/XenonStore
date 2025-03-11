package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
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
        setupToolbar()
        setupButtons()
        checkAllUpdates()

        installPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (checkInstallPermission()) {
                Toast.makeText(this, "Install permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Install permission denied", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun handleUpdate(button: Button, repo: String, downloadUrl: String, latestTag: String) {
        val packageName = packageNameFromRepo(repo)
        val installedVersion = getInstalledAppVersion(packageName)
        val isInstalled = isAppInstalled(packageName)
        runOnUiThread {
            when {
                !isInstalled -> setupButton(button, getString(R.string.install), repo, downloadUrl)
                installedVersion != null && isNewerVersion(latestTag, installedVersion) ->
                    setupButton(button, getString(R.string.update), repo, downloadUrl)
                else -> setupLaunchButton(button, repo)
            }
        }
    }

    private fun setupButton(button: Button, text: String, repo: String, downloadUrl: String) {
        button.text = text
        button.visibility = View.VISIBLE
        fadeIn(button)
        button.setOnClickListener { downloadFile(getProgressBarId(repo), button, repo, downloadUrl) }
    }

    private fun setupLaunchButton(button: Button, repo: String) {
        button.text = getString(R.string.open)
        button.setOnClickListener {
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

    private fun downloadFile(progressBarId: Int, button: Button, repo: String, downloadUrl: String) {
        val progressBar: LinearProgressIndicator = findViewById<LinearProgressIndicator>(progressBarId).apply {
            visibility = View.VISIBLE
            progress = 0
        }
        val request = Request.Builder().url(downloadUrl).build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) return showError("Download failed", progressBarId)

                val tempFile = File(getExternalFilesDir(null), "$repo.apk")
                try {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(4096)
                            var totalBytesRead = 0L
                            val fileSize = response.body!!.contentLength()

                            while (true) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                updateProgress(progressBar, totalBytesRead, fileSize)
                            }
                        }
                    }
                    onDownloadComplete(tempFile, progressBar, button, repo)
                } catch (e: Exception) {
                    showError("Download failed: ${e.message}", progressBarId)
                }
            }
            override fun onFailure(call: Call, e: IOException) = showError("Download failed: ${e.message}", progressBarId)
        })
    }

    private fun showError(message: String, progressBarId: Int) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            findViewById<LinearProgressIndicator>(progressBarId).visibility = View.GONE
        }
    }

    private fun updateProgress(progressBar: LinearProgressIndicator, bytesRead: Long, fileSize: Long) {
        runOnUiThread { progressBar.progress = ((bytesRead * 100) / fileSize).toInt() }
    }

    private fun onDownloadComplete(tempFile: File, progressBar: LinearProgressIndicator, button: Button, repo: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, "Download completed", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            updateButtonText(button, repo)
            launchInstallPrompt(getUriForFile(tempFile))
            tempFile.deleteOnExit()
        }
    }

    private fun getUriForFile(file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )
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
}