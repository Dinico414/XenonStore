package com.xenon.store

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var hasCheckedForUpdates = false
    private var owner = "Dinico414"
    private var filePath = "app-release.apk"
    private val xenonStoreRepo = "Xenon-Store"
    private val todoRepo = "To-Do-List"
    private val calculatorRepo = "Calculator"
    private val fileexplorerRepo = "FileExplorer"
    private val KEY_PRE_RELEASES = "pre_releases"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        setupToolbar()
        setupButtons()
    }

    private fun setupButtons() {
        binding.download1.setOnClickListener {
            setRepositoryDetails(owner, xenonStoreRepo, filePath)
            downloadFile(id.progressbar_1, binding.download1, xenonStoreRepo)
        }
        binding.download2.setOnClickListener {
            setRepositoryDetails(owner, todoRepo, filePath)
            downloadFile(id.progressbar_2, binding.download2, todoRepo)
        }
        binding.download3.setOnClickListener {
            setRepositoryDetails(owner, calculatorRepo, filePath)
            downloadFile(id.progressbar_3, binding.download3, calculatorRepo)
        }
        binding.download4.setOnClickListener {
            setRepositoryDetails(owner, fileexplorerRepo, filePath)
            downloadFile(id.progressbar_4, binding.download4, fileexplorerRepo)
        }

        if (!hasCheckedForUpdates) {
            updateButtonText(binding.download1, xenonStoreRepo)
            updateButtonText(binding.download2, todoRepo)
            updateButtonText(binding.download3, calculatorRepo)
            updateButtonText(binding.download4, fileexplorerRepo)
            hasCheckedForUpdates = true
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                id.action_share -> {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "https://www.dropbox.com/scl/fi/xopqi9tvgr1vphukyozr5/app-release.apk?rlkey=z5qeobkr3wxuhby588ix8n0o9&st=bl0e41h0&dl=1"
                        )
                        type = "text/plain"
                    }

                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                }

                id.settings -> openSettingsActivity()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }


    private fun openSettingsActivity() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }


    private fun setRepositoryDetails(
        owner: String,
        repo: String,
        filePath: String
    ) {
        this.owner = owner
        this.filePath = filePath
    }

    private val STORAGE_PERMISSION_REQUEST_CODE = 1001

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
                        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                // Permission granted, proceed with download
                                // ...
                            } else {
                                // Permission denied, handle accordingly
                                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                            }
                    }
            }

    private fun downloadFile(progressBarId: Int, button: Button, repo: String) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
                               return
                            }
                    }

        val progressBar: LinearProgressIndicator = findViewById(progressBarId)
        if (progressBarId == id.progressbar_1) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.VISIBLE
        }
        progressBar.progress = 0

        val client = OkHttpClient.Builder().build()

        val request =
            Request.Builder().url("https://raw.githubusercontent.com/$owner/$repo/master/$filePath")
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val fileName = "$repo.apk"
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )

                    val inputStream = response.body?.byteStream()
                    val outputStream = FileOutputStream(file)
                    inputStream?.use { input ->
                        outputStream.use { output ->
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
                        Toast.makeText(applicationContext, "Download completed", Toast.LENGTH_SHORT)
                            .show()
                        if (progressBarId == id.progressbar_1) {
                            progressBar.visibility = View.INVISIBLE
                        } else {
                            progressBar.visibility = View.GONE
                        }
                        updateButtonText(button, repo)
                        launchInstallPrompt(file)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download failed", Toast.LENGTH_SHORT)
                            .show()
                        if (progressBarId == id.progressbar_1) {
                            progressBar.visibility = View.INVISIBLE
                        } else {
                            progressBar.visibility = View.GONE
                        }
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext, "Download failed: ${e.message}", Toast.LENGTH_SHORT
                    ).show()
                    if (progressBarId == id.progressbar_1) {
                        progressBar.visibility = View.INVISIBLE
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun launchInstallPrompt(file: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "${packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        startActivity(installIntent)
    }

    private fun updateButtonText(button: Button, repo: String) {
        checkUpdates(button, repo)
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
                            val latestReleaseDate = latestRelease.getString("published_at")
                            val packageName = packageNameFromRepo(repo)
                            val isInstalled = isAppInstalled(packageName)
                            val installedAppDate = getInstalledAppDate(packageName)

                            runOnUiThread {
                                if (!isInstalled) {
                                    button.text = getString(R.string.install)
                                    button.visibility = View.VISIBLE
                                    fadeIn(button)
                                    button.setOnClickListener {
                                        setRepositoryDetails(owner, repo, filePath)
                                        downloadFile(getProgressBarId(repo), button, repo)
                                    }
                                } else if (installedAppDate != null && isNewerDate(
                                        latestReleaseDate,
                                        installedAppDate
                                    )
                                ) {
                                    button.text = getString(R.string.update)
                                    button.visibility = View.VISIBLE
                                    fadeIn(button)

                                    // Set click listener to download the update
                                    button.setOnClickListener {
                                        setRepositoryDetails(owner, repo, filePath)
                                        downloadFile(getProgressBarId(repo), button, repo)
                                    }
                                } else {
                                    if (button == findViewById(id.download_1)) {
                                        button.visibility = View.GONE
                                    } else {
                                        button.text = getString(R.string.open)

                                        button.setOnClickListener {
                                            val launchIntent = packageManager.getLaunchIntentForPackage(
                                                packageNameFromRepo(repo)
                                            )
                                            startActivity(launchIntent)
                                        }
                                    }
                                }
                            }
                        } else {
                            runOnUiThread {
                                if (button == findViewById(id.download_1)) {
                                    button.visibility = View.GONE
                                } else {
                                    button.text = getString(R.string.open)

                                    button.setOnClickListener {
                                        val launchIntent = packageManager.getLaunchIntentForPackage(
                                            packageNameFromRepo(repo)
                                        )
                                        startActivity(launchIntent)
                                    }
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            if (button == findViewById(id.download_1)) {
                                button.visibility = View.GONE
                            } else {
                                button.text = getString(R.string.open)

                                button.setOnClickListener {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(
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
                            applicationContext, "Update check failed", Toast.LENGTH_SHORT
                        ).show()
                        Log.d("Update check", "Error on request: $response")
                        button.text = getString(R.string.open)
                    }
                }
            }
        })
    }

    private fun getInstalledAppDate(packageName: String): String? {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val lastUpdateTime = packageInfo.lastUpdateTime
            val instant = Instant.ofEpochMilli(lastUpdateTime)
            val offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            formatter.format(offsetDateTime)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isNewerDate(date1: String, date2: String): Boolean {
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val dateTime1 = OffsetDateTime.parse(date1, formatter)
        val dateTime2 = OffsetDateTime.parse(date2, formatter)
        return dateTime1.isAfter(dateTime2)
    }

    private fun packageNameFromRepo(repo: String): String {
        return when (repo) {
            xenonStoreRepo -> "com.xenon.store"
            todoRepo -> "com.xenon.todo"
            calculatorRepo -> "com.xenon.calculator"
            fileexplorerRepo -> "com.xenon.fileexplorer"
            else -> throw IllegalArgumentException("Unknown repository: $repo")
        }
    }

    private fun getProgressBarId(repo: String): Int {
        return when (repo) {
            xenonStoreRepo -> id.progressbar_1
            todoRepo -> id.progressbar_2
            calculatorRepo -> id.progressbar_3
            fileexplorerRepo -> id.progressbar_4
            else -> throw IllegalArgumentException("Unknown repository: $repo")
        }
    }

    private fun fadeIn(view: View) {
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 500
        fadeIn.fillAfter = true
        view.startAnimation(fadeIn)
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}