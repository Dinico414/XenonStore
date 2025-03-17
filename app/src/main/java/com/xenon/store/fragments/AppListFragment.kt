package com.xenon.store.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xenon.store.MainActivity
import com.xenon.store.R
import com.xenon.store.databinding.FragmentAppListBinding
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

class AppListFragment : Fragment(R.layout.fragment_app_list) {
    private lateinit var binding: FragmentAppListBinding
    private lateinit var appListModel: AppListViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var installPermissionLauncher: ActivityResultLauncher<Intent>
    private val isDownloadInProgress = AtomicBoolean(false)

    private val owner = "Dinico414"
    private val fileexplorerRepo = "FileExplorer"
    private val calculatorRepo = "Calculator"
    private val todoListRepo = "TodoList"
    private val xenonStoreRepo = "XenonStore"
    private val preReleaseKey = "pre_releases"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appListModel = ViewModelProvider(this)[AppListViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAppListBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun getViewModel(): AppListViewModel {
        return appListModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        sharedPreferences = ctx.getSharedPreferences(ctx.packageName, Context.MODE_PRIVATE)

        setRecyclerView()

        binding.swipeRefreshLayout.setOnRefreshListener {
            checkAllUpdates()
        }

        installPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                if (checkInstallPermission()) {
                    Toast.makeText(context, "Install permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Install permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        setupButtons()
    }

    fun setRecyclerView() {
    }


    private fun checkInstallPermission(): Boolean {
        return activity?.packageManager?.canRequestPackageInstalls() ?: false
    }

    private fun launchInstallPrompt(uri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        installPermissionLauncher.launch(installIntent)
    }

    private fun checkAllUpdates() {
        binding.swipeRefreshLayout.isRefreshing = true

//        val download1 = binding.download_1
//        val download1image = binding.download_1_image

        val updateChecks = listOf(
//            { checkUpdates(download1, download1image, xenonStoreRepo) },
            { checkUpdates(binding.download2, null, todoListRepo) },
            { checkUpdates(binding.download3, null, calculatorRepo) },
            { checkUpdates(binding.download4, null, fileexplorerRepo) }
        )
        var completedChecks = 0

        val onCheckComplete = {
            completedChecks++
            if (completedChecks == updateChecks.size) {
                activity?.runOnUiThread {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
        updateChecks.forEach { check ->
            check().also { onCheckComplete() }
        }
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
                activity?.runOnUiThread {
                    Log.e("UpdateCheck", "Error checking for updates", e)
                    val message = when (e) {
                        is SocketTimeoutException -> "Connection timed out"
                        is UnknownHostException -> "No internet connection"
                        else -> "Update check failed: ${e.message}"
                    }
                    showToast(message)
                    updateButton(button, getString(R.string.open))
                }
                onUpdateCheckComplete()
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    activity?.runOnUiThread { updateButton(button, getString(R.string.open)) }
                    onUpdateCheckComplete()
                    return
                }
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    activity?.runOnUiThread { showToast("Empty response body for $repo") }
                    onUpdateCheckComplete()
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
                            activity?.runOnUiThread { showToast("No assets found for $repo") }
                        }
                    } else {
                        activity?.runOnUiThread { showToast("No suitable release found for $repo") }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        showToast("Error parsing response for $repo: ${e.message}")
                        Log.e("UpdateCheck", "Error parsing response for $repo", e)
                    }
                }
                onUpdateCheckComplete()
            }
        })
    }

    private var pendingUpdateChecks = 0

    fun onUpdateCheckComplete() {
        pendingUpdateChecks--
        if (pendingUpdateChecks == 0) {
            activity?.runOnUiThread {
                binding.swipeRefreshLayout.isRefreshing = false
            }
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
            activity?.packageManager?.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getInstalledAppVersion(packageName: String): String? {
        return try {
            val packageInfo = activity?.packageManager?.getPackageInfo(packageName, 0)
            packageInfo!!.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
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

    private fun downloadFile(
        progressBarId: Int,
        button: Button?,
        imageButton: ImageButton?,
        repo: String,
        downloadUrl: String,
    ) {
        val linearProgressBar: LinearProgressIndicator? =
            if (progressBarId == R.id.progressbar_1 || progressBarId == R.id.progressbar_2 || progressBarId == R.id.progressbar_3 || progressBarId == R.id.progressbar_4) {
                binding.progressbar2.apply {
                    visibility = View.VISIBLE
                    progress = 0
                }
            } else {
                null
            }
        val circularProgressBar: CircularProgressIndicator? =
            if (progressBarId == R.id.progressbar_1_circle) {
                activity?.findViewById<CircularProgressIndicator>(progressBarId)?.apply {
                    visibility = View.VISIBLE
                    progress = 0
                }
            } else {
                null
            }
        val originalButtonText = button?.text?.toString()
        val originalImageDrawable = imageButton?.drawable

        activity?.runOnUiThread {
            button?.text = ""
            imageButton?.setImageDrawable(null)
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
                fun onDownloadCompleteOrFailed() {
                    activity?.runOnUiThread {
                        isDownloadInProgress.set(false)
                        button?.isEnabled = true
                        imageButton?.isEnabled = true
                    }
                }
                val contentLength = response.body?.contentLength() ?: -1
                var downloadedBytes: Long = 0
                val buffer = ByteArray(8192)
                val inputStream = response.body?.byteStream()
                val tempFile = File(activity!!.getExternalFilesDir(null), "$repo.apk")
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
                        activity?.runOnUiThread {
                            linearProgressBar?.progress = progress
                            circularProgressBar?.progress = progress
                        }
                    }
                    onDownloadCompleteOrFailed()
                    activity?.runOnUiThread {
                        linearProgressBar?.visibility = View.GONE
                        circularProgressBar?.visibility = View.GONE
                        onDownloadComplete(
                            tempFile,
                            linearProgressBar
                                ?: circularProgressBar!!,
                            button,
                            imageButton,
                            repo,
                            originalImageDrawable
                        )
                    }
                } catch (e: Exception) {
                    onDownloadCompleteOrFailed()
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
        originalImageDrawable: Drawable?,
    ) {
        activity?.runOnUiThread {
            Toast.makeText(context, this, Toast.LENGTH_SHORT).show()
            activity?.findViewById<LinearProgressIndicator>(progressBarId)?.visibility = View.GONE
            activity?.findViewById<CircularProgressIndicator>(progressBarId)?.visibility = View.GONE
            button?.apply {
                visibility = View.VISIBLE
                text = originalButtonText
            }
            imageButton?.apply {
                visibility = View.VISIBLE
                setImageDrawable(originalImageDrawable)
            }
        }
    }

    private fun onDownloadComplete(
        tempFile: File,
        progressBar: View,
        button: Button?,
        imageButton: ImageButton?,
        repo: String,
        originalImageDrawable: Drawable?,
    ) {
        activity?.runOnUiThread {
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
                requireActivity(),
                "${requireActivity().packageName}.provider",
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

    @SuppressLint("SetTextI18n")
    private fun handleUpdate(
        button: Button,
        imageButton: ImageButton?,
        repo: String,
        downloadUrl: String,
        latestTag: String,
    ) {
        val packageName = packageNameFromRepo(repo)
        val installedVersion = getInstalledAppVersion(packageName)
        val isInstalled = isAppInstalled(packageName)

        val cardId = when (repo) {
//            xenonStoreRepo -> id.card_1
            todoListRepo -> R.id.card_2
            calculatorRepo -> R.id.card_3
            fileexplorerRepo -> R.id.card_4
            else -> null
        }
        val cardView = cardId?.let { activity?.findViewById<CardView>(it) }
        val linearLayoutId = when (repo) {
//            xenonStoreRepo -> id.version_1
            todoListRepo -> R.id.version_2
            calculatorRepo -> R.id.version_3
//            fileexplorerRepo -> id.version_4
            else -> null
        }
        val linearLayout = linearLayoutId?.let { cardView?.findViewById<LinearLayout>(it) }
        activity?.runOnUiThread {
            val versionTextView1 = linearLayout?.findViewById<TextView>(
                when (repo) {
//                    xenonStoreRepo -> id.installed_version_1
                    todoListRepo -> R.id.installed_version_2
                    calculatorRepo -> R.id.installed_version_3
//                    fileexplorerRepo -> id.installed_version_4
                    else -> null
                }!!
            )
            val versionTextView2 = linearLayout?.findViewById<TextView>(
                when (repo) {
//                    xenonStoreRepo -> id.new_version_1
                    todoListRepo -> R.id.new_version_2
                    calculatorRepo -> R.id.new_version_3
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
                        paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
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

    private fun setupButtons() {
//        updateButtonText(binding.download1, xenonStoreRepo)
        updateButtonText(binding.download2, todoListRepo)
        updateButtonText(binding.download3, calculatorRepo)
        updateButtonText(binding.download4, fileexplorerRepo)
    }



    private fun setupButton(button: Button, text: String, repo: String, downloadUrl: String) {
        button.text = text
        button.visibility = View.VISIBLE
        fadeIn(button)
        button.setOnClickListener {
            if (isDownloadInProgress.compareAndSet(false, true)) {
                downloadFile(
                    ProgressBarType.LINEAR.getProgressBarId(repo),
                    button,
                    null,
                    repo,
                    downloadUrl
                )
                button.isEnabled = false
            }
        }
    }

    private fun setupImageButton(imageButton: ImageButton, repo: String, downloadUrl: String) {
        imageButton.visibility = View.VISIBLE
        fadeIn(imageButton)
        imageButton.setOnClickListener {
            if (isDownloadInProgress.compareAndSet(false, true)) {
                downloadFile(
                    ProgressBarType.CIRCULAR.getProgressBarId(repo),
                    null,
                    imageButton,
                    repo,
                    downloadUrl
                )
                imageButton.isEnabled = false
            }
        }
    }

    private fun setupLaunchButton(button: Button, repo: String) {
        button.text = getString(R.string.open)
        button.setOnClickListener {
            startActivity(activity?.packageManager?.getLaunchIntentForPackage(packageNameFromRepo(repo)))
        }
    }

    private fun setupLaunchImageButton(imageButton: ImageButton, repo: String) {
        imageButton.setOnClickListener {
            startActivity(activity?.packageManager?.getLaunchIntentForPackage(packageNameFromRepo(repo)))
        }
    }

    private fun updateButton(button: Button, text: String) {
        button.text = text
    }

    private fun updateButtonText(button: Button, repo: String) {
        val packageName = packageNameFromRepo(repo)
        val isInstalled = isAppInstalled(packageName)
        getInstalledAppVersion(packageName)

        if (isInstalled) {
            button.text = getString(R.string.open)
            button.setOnClickListener {
                val launchIntent = activity?.packageManager?.getLaunchIntentForPackage(packageName)
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
                ProgressBarType.LINEAR -> R.id.progressbar_1
                ProgressBarType.CIRCULAR -> R.id.progressbar_1_circle
            }
            todoListRepo -> R.id.progressbar_2
            calculatorRepo -> R.id.progressbar_3
            fileexplorerRepo -> R.id.progressbar_4
            else -> throw IllegalArgumentException("Unknown repository: $repo")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun fadeIn(view: View) {
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 500
        view.startAnimation(fadeIn)
    }

    enum class ProgressBarType {
        LINEAR, CIRCULAR
    }
}