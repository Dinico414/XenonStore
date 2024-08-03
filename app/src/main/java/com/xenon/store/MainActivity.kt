package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.progressindicator.LinearProgressIndicator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private var owner = "Dinico414"
    private var todoRepo = "TodoList"
    private var calculatorRepo = "Calculator"
    private var xenonStoreRepo = "XenonStore"
    private var filePath = "app/release/app-release.apk"
    private var personalAccessToken = "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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getPreferences(MODE_PRIVATE)

        val downloadButton1: Button = findViewById(R.id.download_1)
        val downloadButton2: Button = findViewById(R.id.download_2)
        val downloadButton3: Button = findViewById(R.id.download_3)

        updateButtonText(downloadButton1, todoRepo)
        updateButtonText(downloadButton2, calculatorRepo)
        updateButtonText(downloadButton3, xenonStoreRepo)

        downloadButton1.setOnClickListener {
            setRepositoryDetails("Dinico414", todoRepo, "app/release/app-release.apk", "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")
            downloadFile(R.id.progressbar_1, downloadButton1, todoRepo)
        }

        downloadButton2.setOnClickListener {
            setRepositoryDetails("Dinico414", calculatorRepo, "app/release/app-release.apk", "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")
            downloadFile(R.id.progressbar_2, downloadButton2, calculatorRepo)
        }

        downloadButton3.setOnClickListener {
            setRepositoryDetails("Dinico414", xenonStoreRepo, "app/release/app-release.apk", "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")
            downloadFile(R.id.progressbar_3, downloadButton3, xenonStoreRepo)
        }
        val swipeRefreshLayout: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            // Refresh all buttons
            updateButtonText(findViewById(R.id.download_1), todoRepo)
            updateButtonText(findViewById(R.id.download_2), calculatorRepo)
            updateButtonText(findViewById(R.id.download_3), xenonStoreRepo)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()

        updateButtonText(findViewById(R.id.download_1), todoRepo)
        updateButtonText(findViewById(R.id.download_2), calculatorRepo)
        updateButtonText(findViewById(R.id.download_3), xenonStoreRepo)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                setStoragePermissionGranted(true)
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot download the file.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun isStoragePermissionGranted(): Boolean {
        return sharedPreferences.getBoolean(KEY_STORAGE_PERMISSION_GRANTED, false)
    }

    private fun setStoragePermissionGranted(@Suppress("SameParameterValue") granted: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_STORAGE_PERMISSION_GRANTED, granted).apply()
    }

    private fun setRepositoryDetails(@Suppress("SameParameterValue") owner: String, repo: String, @Suppress("SameParameterValue") filePath: String, @Suppress("SameParameterValue") personalAccessToken: String) {
        this.owner = owner
        this.filePath = filePath
        this.personalAccessToken = personalAccessToken
    }

    private fun downloadFile(progressBarId: Int, button: Button, repo: String) {
        if (!isStoragePermissionGranted()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            requestPermissionLauncher.launch(intent)
            return
        }

        val progressBar: LinearProgressIndicator = findViewById(progressBarId)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $personalAccessToken")
                    .build()
                chain.proceed(request)
            }
            .build()

        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/$owner/$repo/master/$filePath")
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
                        Toast.makeText(applicationContext, "Download completed", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        updateButtonText(button, repo)
                        launchInstallPrompt(file)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download failed", Toast.LENGTH_SHORT).show()
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

    private fun launchInstallPrompt(file: File) {
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.provider",
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW)
        installIntent.setDataAndType(uri, "application/vnd.android.package-archive")
        installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(installIntent)
    }

    private fun updateButtonText(button: Button, repo: String) {
        val packageName = packageNameFromRepo(repo)
        if (isAppInstalled(packageName)) {
            // App is installed, check for updates
            checkUpdates(button, repo)
            // Set click listener to open the app
            button.setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                startActivity(launchIntent)
            }
        } else {
            // App is not installed
            button.text = getString(R.string.install)
            // Keep the original download logic
            button.setOnClickListener {
                setRepositoryDetails("Dinico414", repo, "app/release/app-release.apk", "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")
                downloadFile(getProgressBarId(repo), button, repo)
            }
        }
    }
    private fun getProgressBarId(repo: String): Int {
        return when (repo) {
            todoRepo -> R.id.progressbar_1
            calculatorRepo -> R.id.progressbar_2
            xenonStoreRepo -> R.id.progressbar_3
            else -> throw IllegalArgumentException("Invalid repository name")
        }
    }


    private fun checkUpdates(button: Button, repo: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Authorization", "Bearer $personalAccessToken")
            .build()

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
                    val jsonObject = responseBody?.let { JSONObject(it) }
                    val latestReleaseDate = jsonObject?.getString("published_at")
                    val installedAppDate = getInstalledAppDate(packageNameFromRepo(repo))
                    Log.d("UpdateCheck", "Response: $responseBody")

                    runOnUiThread {
                        if (installedAppDate != null && isNewerDate(latestReleaseDate.toString(), installedAppDate)) {
                            button.text = getString(R.string.update)
                        } else {
                            button.text = getString(R.string.open)
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Update check failed", Toast.LENGTH_SHORT).show()
                        button.text = getString(R.string.open)
                    }
                }
            }
        })
    }

    private fun isNewerDate(date1: String, date2: String): Boolean {
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val dateObj1 = OffsetDateTime.parse(date1, formatter)
        val dateObj2 = OffsetDateTime.parse(date2, formatter)
        return dateObj1.isAfter(dateObj2)
    }

    private fun getInstalledAppDate(packageName: String): String? {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val installTime = packageInfo.lastUpdateTime
            val instant = Instant.ofEpochMilli(installTime)
            val offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            formatter.format(offsetDateTime)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }


    private fun isAppInstalled(packageName: String): Boolean {
        val packageManager = packageManager
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun packageNameFromRepo(repo: String): String {
        return when (repo) {
            todoRepo -> "com.xenon.todolist"
            calculatorRepo -> "com.xenon.calculator"
            xenonStoreRepo -> "com.xenon.store"
            else -> ""
        }
    }

    companion object {
        private const val KEY_STORAGE_PERMISSION_GRANTED = "storage_permission_granted"
    }
}
