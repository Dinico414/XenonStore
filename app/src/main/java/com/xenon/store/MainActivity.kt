package com.xenon.store

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.xenon.store.R.id.download_1
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val baseUrl = "https://raw.githubusercontent.com"
    private val owner = "Dinico414"
    private val repo = "TodoList"
    private val filePath = "app/release/app-release.apk"
    private val personalAccessToken = "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"

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getPreferences(MODE_PRIVATE)

        val downloadButton: Button = findViewById(download_1)
        downloadButton.setOnClickListener {
            if (!isStoragePermissionGranted()) {
                // Request storage access using SAF
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                requestPermissionLauncher.launch(intent)
            } else {
                // Permission already granted, proceed with download
                downloadFile()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Permission granted, proceed with download
                setStoragePermissionGranted(true)
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

    private fun setStoragePermissionGranted(granted: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_STORAGE_PERMISSION_GRANTED, granted).apply()
    }

    private fun downloadFile() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $personalAccessToken")
                    .build()
                chain.proceed(request)
            }
            .build()

        val request = Request.Builder()
            .url("$baseUrl/$owner/$repo/master/$filePath")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val fileName = "Todo.apk"
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )

                    val inputStream = response.body?.byteStream()
                    val outputStream = FileOutputStream(file)
                    inputStream?.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download completed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Download failed", Toast.LENGTH_SHORT).show()
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
                }
            }
        })
    }

    companion object {
        private const val KEY_STORAGE_PERMISSION_GRANTED = "storage_permission_granted"
    }
}
