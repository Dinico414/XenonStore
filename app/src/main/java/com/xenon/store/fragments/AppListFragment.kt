package com.xenon.store.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xenon.store.AppEntryState
import com.xenon.store.AppItem
import com.xenon.store.AppListAdapter
import com.xenon.store.AppListChangeType
import com.xenon.store.R
import com.xenon.store.databinding.FragmentAppListBinding
import com.xenon.store.viewmodel.AppListViewModel
import com.xenon.store.viewmodel.LiveListViewModel
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppListFragment : Fragment(R.layout.fragment_app_list) {
    private lateinit var binding: FragmentAppListBinding
    private lateinit var appListModel: AppListViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var installPermissionLauncher: ActivityResultLauncher<Intent>
    private val isDownloadInProgress = AtomicBoolean(false)

    private val preReleaseKey = "pre_releases"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appListModel = ViewModelProvider(this)[AppListViewModel::class.java]

        loadAppListFromJson()
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

        val context = requireContext()
        sharedPreferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)

        setupRecyclerView()

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshAppList()
        }

        installPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                if (checkInstallPermission()) {
                    showToast("Install permission granted")
                } else {
                    showToast("Install permission denied")
                }
            }
    }

    override fun onResume() {
        super.onResume()
        refreshAppList()
    }

    private fun loadAppListFromJson() {
        val activity = requireActivity()

        // Load app list from json
        val appListInput = activity.assets.open("app_list.json")
        val jsonString = appListInput.bufferedReader().use { it.readText() }
//        val list = Json.decodeFromString<ArrayList<AppItem>>(jsonString)
        val json = JSONObject(jsonString)
        val list = json.getJSONArray("appList")
        val appList = ArrayList<AppItem>()
        for (i in 0 until list.length()) {
            val el = list.getJSONObject(i)
            val appItem = AppItem(
                el.getString("name"),
                el.getString("icon"),
                el.getString("githubUrl"),
                el.getString("packageName")
            )
            appList.add(appItem)
        }
        appListModel.setList(appList)
    }

    private fun setupRecyclerView() {
        val context = requireContext()
        binding.appListRecyclerView.layoutManager = LinearLayoutManager(context)
        val adapter = AppListAdapter(context, appListModel.getList(), object : AppListAdapter.AppItemListener {
            override fun buttonClicked(appItem: AppItem, position: Int) {
                when (appItem.state) {
                    AppEntryState.NOT_INSTALLED,
                    AppEntryState.INSTALLED_AND_OUTDATED -> {
                        // Try downloading
                        appItem.state = AppEntryState.DOWNLOADING
                        appListModel.update(appItem, AppListChangeType.STATE_CHANGE)

                        downloadFile(appItem.downloadUrl, appItem.packageName, object : DownloadListener {
                            override fun onProgress(downloaded: Long, size: Long) {
                                appItem.bytesDownloaded = downloaded
                                appItem.fileSize = size
                                appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                            }
                            override fun onCompleted(tempFile: File) {
                                installApk(tempFile)

                            }
                            override fun onFailure() {
                                appItem.state = AppEntryState.NOT_INSTALLED
                                showToast("Download failed")
                            }
                        })
                    }
                    AppEntryState.DOWNLOADING -> {}
                    AppEntryState.INSTALLED -> {
                        startActivity(
                            activity?.packageManager?.getLaunchIntentForPackage(appItem.packageName))
                    }
                }
                appListModel.update(appItem)
            }
        })
        binding.appListRecyclerView.adapter = adapter
        binding.appListRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                val marginInPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    10.toFloat(),
                    view.context.resources.displayMetrics
                ).toInt()

                val position = parent.getChildAdapterPosition(view)
                if (position == RecyclerView.NO_POSITION) {
                    val oldPosition = parent.getChildViewHolder(view)?.oldPosition
                    if (oldPosition == 0) {
                        outRect.top = marginInPx
                    }
                }
                else if (position == 0) {
                    outRect.top = marginInPx
                }
            }
        })

        appListModel.listStatus.observe(viewLifecycleOwner) { change ->
            when (change.type) {
                LiveListViewModel.ListChangedType.ADD -> {
                    adapter.notifyItemInserted(change.idx)
                }
                LiveListViewModel.ListChangedType.REMOVE -> {
                    adapter.notifyItemRemoved(change.idx)
                }
                LiveListViewModel.ListChangedType.MOVED -> {
                    adapter.notifyItemMoved(change.idx, change.idx2)
                }
                LiveListViewModel.ListChangedType.UPDATE -> {
                    adapter.notifyItemChanged(change.idx, change.payload)
                }
                LiveListViewModel.ListChangedType.MOVED_AND_UPDATED -> {
                    adapter.notifyItemChanged(change.idx, change.payload)
                    adapter.notifyItemMoved(change.idx, change.idx2)
                    if (change.idx == 0) {
                        binding.appListRecyclerView.scrollToPosition(0)
                    }
                }
                LiveListViewModel.ListChangedType.OVERWRITTEN -> {
                    adapter.appItems = appListModel.getList()
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun refreshAppList() {
        binding.swipeRefreshLayout.isRefreshing = true

        for (appItem in appListModel.getList()) {
            appItem.installedVersion = getInstalledAppVersion(appItem.packageName) ?: ""
            if (appItem.installedVersion != "") {
                appItem.state = AppEntryState.INSTALLED
                appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
            }
            else {
                appItem.state = AppEntryState.NOT_INSTALLED
                appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
            }

            getNewReleaseVersionGithub(appItem.owner, appItem.repo, object : APIRequestCallback{
                override fun onCompleted(result: String) {
                    val releases = JSONArray(result)
                    val latestRelease = findLatestRelease(releases)

                    if (latestRelease != null) {
                        val assets = latestRelease.getJSONArray("assets")
                        val newVersion = latestRelease.getString("tag_name")
                        if (assets.length() > 0) {
                            if (isNewerVersion(newVersion, appItem.installedVersion)) {
                                val asset = assets.getJSONObject(0)
                                appItem.newVersion = newVersion
                                appItem.downloadUrl = asset.getString("browser_download_url")
                                appItem.state = AppEntryState.INSTALLED_AND_OUTDATED
                                appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                            }
                        } else {
                            showToast("No assets found for ${appItem.repo}")
                        }
                    } else {
                        showToast("No suitable release found for ${appItem.repo}")
                    }
                }
                override fun onFailure(error: String) {
                    showToast("$error for ${appItem.repo}")
                }
            })
        }

        binding.swipeRefreshLayout.isRefreshing = false
    }

    interface APIRequestCallback {
        fun onCompleted(result: String)
        fun onFailure(error: String)
    }

    private fun getNewReleaseVersionGithub(owner: String, repo: String, callback: APIRequestCallback) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases")
            .build()
        Log.d("fetching releases", "https://api.github.com/repos/$owner/$repo/releases")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure("Failure")
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onFailure("Response ${response.code}")
                    return
                }
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    callback.onFailure("Empty response body")
                    return
                }
                callback.onCompleted(responseBody)
            }
        })
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
        if (installedVersion == "") return true

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

    interface DownloadListener {
        fun onProgress(downloaded: Long, size: Long)
        fun onCompleted(tempFile: File)
        fun onFailure()
    }

    private fun downloadFile(
        downloadUrl: String,
        name: String,
        progressListener: DownloadListener
    ) {
        val request = Request.Builder().url(downloadUrl).build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    progressListener.onFailure()
                    return
                }

                val contentLength = response.body?.contentLength() ?: -1
                var downloadedBytes: Long = 0
                val buffer = ByteArray(8192)
                val inputStream = response.body?.byteStream()
                val tempFile = File(activity!!.getExternalFilesDir(null), "$name.apk")
                val outputStream = FileOutputStream(tempFile)

                try {
                    var bytesRead: Int
                    while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        progressListener.onProgress(downloadedBytes, contentLength)
                    }
                    progressListener.onCompleted(tempFile)
                } catch (e: Exception) {
                    progressListener.onFailure()
                } finally {
                    inputStream?.close()
                    outputStream.close()
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                progressListener.onFailure()
            }
        })
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            requireActivity(),
            "${requireActivity().packageName}.provider",
            apkFile
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

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    enum class ProgressBarType {
        LINEAR, CIRCULAR
    }
}