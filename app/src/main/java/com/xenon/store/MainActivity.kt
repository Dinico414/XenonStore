package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private var owner = "Dinico414"
    private var repo = "TodoList"
    private var filePath = "app/release/app-release.apk"
    private var personalAccessToken = "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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getPreferences(MODE_PRIVATE)

        val downloadButton1: Button = findViewById(R.id.download_1)
        val downloadButton2: Button = findViewById(R.id.download_2)
        val downloadButton3: Button = findViewById(R.id.download_3)

        downloadButton1.setOnClickListener {
            setRepositoryDetails("Dinico414", "TodoList", "app/release/app-release.apk", "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")
            downloadFile(R.id.progressbar_1)
        }

        downloadButton2.setOnClickListener {
            setRepositoryDetails("Dinico414", "Calculator", "app/release/app-release.apk", "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")
            downloadFile(R.id.progressbar_2)
        }

        downloadButton3.setOnClickListener {
            setRepositoryDetails("Dinico414", "XenonStore", "app/release/app-release.apk", "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")
            downloadFile(R.id.progressbar_3)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Permission granted, proceed with download
                setStoragePermissionGranted(true)
                downloadFile(R.id.progressbar_3)
            } else {
                // Permission denied
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
        this.repo = repo
        this.filePath = filePath
        this.personalAccessToken = personalAccessToken
    }

    private fun downloadFile(progressBarId: Int) {
        if (!isStoragePermissionGranted()) {
            // Request storage access using SAF
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            requestPermissionLauncher.launch(intent)
            return
        }

        // Find the progress indicator
        val progressBar: LinearProgressIndicator = findViewById(progressBarId)
        // Show progress indicator
        progressBar.visibility = View.VISIBLE
        // Reset progress
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
                            // Prepare buffer
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            var totalBytesRead: Long = 0
                            val fileSize: Long = response.body!!.contentLength()

                            // Read from input stream and write to output stream
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                // Calculate progress
                                val progress = ((totalBytesRead * 100) / fileSize).toInt()
                                // Update progress bar on UI thread
                                runOnUiThread {
                                    progressBar.progress = progress
                                }
                            }
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download completed", Toast.LENGTH_SHORT).show()
                        // Hide progress indicator after download completion
                        progressBar.visibility = View.GONE

                        // Launch installation prompt
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
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download failed", Toast.LENGTH_SHORT).show()
                        // Hide progress indicator if download failed
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
                    // Hide progress indicator if download failed
                    progressBar.visibility = View.GONE
                }
            }
        })
    }

    companion object {
        private const val KEY_STORAGE_PERMISSION_GRANTED = "storage_permission_granted"
    }
}
