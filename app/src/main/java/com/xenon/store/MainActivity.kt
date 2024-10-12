package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xenon.store.R.id
import com.xenon.store.R.string
import com.xenon.store.activities.SettingsActivity
import com.xenon.store.databinding.ActivityMainBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@Suppress("UNUSED_PARAMETER", "SameParameterValue")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    private var owner = "XenonOSProduction"
    private var filePath = "app/release/app-release.apk"
    private var personalAccessToken = BuildConfig.personalAccessToken

    private var xenonStoreRepo = "XenonStore"
    private var todoRepo = "TodoList"
    private var calculatorRepo = "Calculator"
    private var fileexplorerRepo = "FileExplorer"

    private var hasCheckedForUpdates = false


    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferenceManager = SharedPreferenceManager(this)
        AppCompatDelegate.setDefaultNightMode(sharedPreferenceManager.themeFlag[sharedPreferenceManager.theme])
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        sharedPreferences = getPreferences(MODE_PRIVATE)

        val downloadButton1: Button = findViewById(id.download_1)
        val downloadButton2: Button = findViewById(id.download_2)
        val downloadButton3: Button = findViewById(id.download_3)
        val downloadButton4: Button = findViewById(id.download_4)


        updateButtonText(downloadButton1, xenonStoreRepo)
        updateButtonText(downloadButton2, todoRepo)
        updateButtonText(downloadButton3, calculatorRepo)
        updateButtonText(downloadButton4, fileexplorerRepo)

        val swipeRefreshLayout: SwipeRefreshLayout = findViewById(id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            updateButtonText(findViewById(id.download_1), xenonStoreRepo)
            updateButtonText(findViewById(id.download_2), todoRepo)
            updateButtonText(findViewById(id.download_3), calculatorRepo)
            updateButtonText(findViewById(id.download_4), fileexplorerRepo)
            swipeRefreshLayout.isRefreshing = false
            hasCheckedForUpdates = false
        }
        findViewById<AppBarLayout>(id.appbar).also {

            it.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                val totalScrollRange = appBarLayout.totalScrollRange
                val alpha = 1.0f + (verticalOffset.toFloat() / totalScrollRange * 5)
                downloadButton1.alpha = alpha
            }
        }
    }


    override fun onResume() {
        super.onResume()
        if (!hasCheckedForUpdates) {
            updateButtonText(findViewById(id.download_1), xenonStoreRepo)
            updateButtonText(findViewById(id.download_2), todoRepo)
            updateButtonText(findViewById(id.download_3), calculatorRepo)
            updateButtonText(findViewById(id.download_4), fileexplorerRepo)
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
//                            "https://github.com/XenonOSProduction/XenonStore/raw/master/app/release/app-release.apk"
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


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                setStoragePermissionGranted(true)
            } else {
                Toast.makeText(
                    this, "Permission denied. Cannot download the file.", Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun openSettingsActivity() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun isStoragePermissionGranted(): Boolean {
        return sharedPreferences.getBoolean(KEY_STORAGE_PERMISSION_GRANTED, false)
    }

    private fun setStoragePermissionGranted(granted: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_STORAGE_PERMISSION_GRANTED, granted).apply()
    }

    private fun setRepositoryDetails(
        owner: String,
        repo: String,
        filePath: String,
        personalAccessToken: String
    ) {
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
        if (progressBarId == id.progressbar_1) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.VISIBLE
        }
        progressBar.progress = 0

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request =
                chain.request().newBuilder().header("Authorization", "Bearer $personalAccessToken")
                    .build()
            chain.proceed(request)
        }.build()

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
        val uri = FileProvider.getUriForFile(
            applicationContext, "${applicationContext.packageName}.provider", file
        )

        val installIntent = Intent(Intent.ACTION_VIEW)
        installIntent.setDataAndType(uri, "application/vnd.android.package-archive")
        installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(installIntent)
    }

    private fun updateButtonText(button: Button, repo: String) {
        if (button == findViewById(id.download_1)) {
            val packageName = packageNameFromRepo(repo)
            if (isAppInstalled(packageName)) {
                checkUpdates(button, repo)
            } else {
                button.visibility = View.GONE
            }
        } else {

            val packageName = packageNameFromRepo(repo)
            if (isAppInstalled(packageName)) {

                checkUpdates(button, repo)

                button.setOnClickListener {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    startActivity(launchIntent)
                }
            } else {

                button.text = getString(string.install)

                button.setOnClickListener {
                    setRepositoryDetails(owner, repo, filePath, personalAccessToken)
                    downloadFile(getProgressBarId(repo), button, repo)
                }
            }
        }
    }

    private fun getProgressBarId(repo: String): Int {
        return when (repo) {
            xenonStoreRepo -> id.progressbar_1
            todoRepo -> id.progressbar_2
            calculatorRepo -> id.progressbar_3
            fileexplorerRepo -> id.progressbar_4
            else -> throw IllegalArgumentException("Invalid repository name")
        }
    }


    private fun checkUpdates(button: Button, repo: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://api.github.com/repos/$owner/$repo/commits")
            .header("Authorization", "Bearer $personalAccessToken").build()

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
                    button.text = getString(string.open)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonArray = responseBody?.let { JSONArray(it) }
                    val firstCommit = jsonArray?.getJSONObject(0)?.getJSONObject("commit")
                    val author = firstCommit?.getJSONObject("author")
                    val latestReleaseDate = author?.getString("date")
                    val installedAppDate = getInstalledAppDate(packageNameFromRepo(repo))
                    Log.d("UpdateCheck", "Response: $responseBody")

                    runOnUiThread {
                        if (installedAppDate != null && isNewerDate(
                                latestReleaseDate.toString(),
                                installedAppDate
                            )
                        ) {
                            button.text = getString(string.update)
                            button.visibility = View.VISIBLE
                            fadeIn(button)

                            // Set click listener to download the update
                            button.setOnClickListener {
                                setRepositoryDetails(owner, repo, filePath, personalAccessToken)
                                downloadFile(getProgressBarId(repo), button, repo)
                            }
                        } else {
                            if (button == findViewById(id.download_1)) {
                                button.visibility = View.GONE
                            } else {
                                button.text = getString(string.open)

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
                        button.text = getString(string.open)
                    }
                }
            }
        })
    }

    private fun fadeIn(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(300).setListener(null)
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
            xenonStoreRepo -> "com.xenon.store"
            todoRepo -> "com.xenon.todolist"
            calculatorRepo -> "com.xenon.calculator"
            fileexplorerRepo -> "com.xenon.fileexplorer"
            else -> ""
        }
    }

    companion object {
        private const val KEY_STORAGE_PERMISSION_GRANTED = "storage_permission_granted"
    }
}
