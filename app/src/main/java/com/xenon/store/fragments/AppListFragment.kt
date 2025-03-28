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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppListFragment : Fragment(R.layout.fragment_app_list) {
    private lateinit var binding: FragmentAppListBinding
    private lateinit var appListModel: AppListViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var installPermissionLauncher: ActivityResultLauncher<Intent>
    private val isDownloadInProgress = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appListModel = ViewModelProvider(this)[AppListViewModel::class.java]

        if (appListModel.getList().size == 0)
            loadAppListFromUrl()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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
        sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

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

    private fun loadAppListFromUrl() {
        val activity = requireActivity()
        val urlString = "https://raw.githubusercontent.com/Dinico414/Xenon-Commons/master/accesspoint/src/main/java/com/xenon/commons/accesspoint/app_list.json"

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = fetchJsonFromUrl(urlString)
                if (jsonString != null) {
                    val appList = parseJson(jsonString)
                    withContext(Dispatchers.Main) {
                        appListModel.setList(appList)
                    }
                } else {
                    // Handle the case where jsonString is null
                    Log.e("AppListFragment", "Failed to fetch or read JSON data.")
                    withContext(Dispatchers.Main) {
                        // Optionally, update UI to show an error message
                    }
                }
            } catch (e: Exception) {
                Log.e("AppListFragment", "Error loading app list: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Optionally, update UI to show an error message
                }
            }
        }
    }

    private fun fetchJsonFromUrl(urlString: String): String? {
        var urlConnection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connect()

            val inputStream = urlConnection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            stringBuilder.toString()
        } catch (e: IOException) {
            Log.e("AppListFragment", "Error fetching JSON: ${e.message}")
            null
        } finally {
            urlConnection?.disconnect()
        }
    }

    private fun parseJson(jsonString: String): ArrayList<AppItem> {
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
        return appList
    }
    private fun setupRecyclerView() {
        val context = requireContext()
        binding.appListRecyclerView.layoutManager = LinearLayoutManager(context)
        val adapter = AppListAdapter(context, appListModel.getList(), object : AppListAdapter.AppItemListener {
            override fun buttonClicked(appItem: AppItem, position: Int) {
                when (appItem.state) {
                    AppEntryState.NOT_INSTALLED,
                    AppEntryState.INSTALLED_AND_OUTDATED,
                        -> {
                        if (appItem.downloadUrl == "") {
                            showToast("Failed to fetch download url of ${appItem.name}")
                            return
                        }

                        // Try downloading
                        appItem.state = AppEntryState.DOWNLOADING
                        appListModel.update(appItem, AppListChangeType.STATE_CHANGE)

                        downloadAppItem(appItem)
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
                state: RecyclerView.State,
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

        appListModel.liveListEvent.observe(viewLifecycleOwner) { _ ->
            while (true) {
                val change = appListModel.listEventQueue.poll() ?: break
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
                        refreshAppList()
                        adapter.appItems = appListModel.getList()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

        appListModel.downloadedApkFile.observe(viewLifecycleOwner) { _ ->
            appListModel.downloadedApkFile.postValue(null)
            while (true) {
                val apkFile = appListModel.downloadedApkQueue.poll() ?: break
                installApk(apkFile)
            }
        }
    }

    private fun refreshAppList(invalidateCaches: Boolean = false) {
        binding.swipeRefreshLayout.isRefreshing = true

        refreshAppItem(appListModel.storeAppItem)

        for (appItem in appListModel.getList()) {
            refreshAppItem(appItem, invalidateCaches)
        }

        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun refreshAppItem(appItem: AppItem, invalidateCaches: Boolean = false) {
        val preReleases = sharedPreferences.getBoolean("pre_releases", false)

        if (appItem.newIsPreRelease != preReleases) {
            appItem.newVersion = ""
            appItem.downloadUrl = ""
            appItem.newIsPreRelease = false
        }

        appItem.installedVersion = getInstalledAppVersion(appItem.packageName) ?: ""
        if (appItem.state == AppEntryState.DOWNLOADING) {
//                downloadAppItem(appItem)
        }
        else if (appItem.installedVersion != "" && isNewerVersion(appItem.newVersion, appItem.installedVersion)) {
            appItem.state = AppEntryState.INSTALLED_AND_OUTDATED
        }
        else if (appItem.installedVersion != "") {
            appItem.state = AppEntryState.INSTALLED
        }
        else {
            appItem.state = AppEntryState.NOT_INSTALLED
        }
        appListModel.update(appItem, AppListChangeType.STATE_CHANGE)

        if (appItem.downloadUrl == "" || invalidateCaches) {
            getNewReleaseVersionGithub(appItem.owner, appItem.repo, preReleases, object : GithubReleaseAPICallback{
                override fun onCompleted(version: String, downloadUrl: String) {
                    appItem.newIsPreRelease = preReleases
                    appItem.downloadUrl = downloadUrl
                    if (isNewerVersion(version, appItem.installedVersion)) {
                        appItem.newVersion = version
                        if (appItem.state == AppEntryState.INSTALLED) {
                            appItem.state = AppEntryState.INSTALLED_AND_OUTDATED
                            appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                        }
                    }
                }
                override fun onFailure(error: String) {
                    showToast("$error for ${appItem.repo}")
                }
            })
        }
    }

    interface GithubReleaseAPICallback {
        fun onCompleted(version: String, downloadUrl: String)
        fun onFailure(error: String)
    }

    private fun getNewReleaseVersionGithub(owner: String, repo: String, preRelease: Boolean, callback: GithubReleaseAPICallback) {
        val url = if (preRelease) "https://api.github.com/repos/$owner/$repo/releases?per_page=1"
            else "https://api.github.com/repos/$owner/$repo/releases/latest"
        Log.d("fetching releases", url)

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
        val request = Request.Builder()
            .url(url)
            .build()

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

                Log.d("response body $repo", responseBody)

                val latestRelease: JSONObject
                if (preRelease) {
                    val releases = JSONArray(responseBody)
                    latestRelease = releases.getJSONObject(0)
                }
                else {
                    latestRelease = JSONObject(responseBody)
                }

                val assets = latestRelease.getJSONArray("assets")
                val newVersion = latestRelease.getString("tag_name")
                if (assets.length() > 0) {
                    val asset = assets.getJSONObject(0)
                    callback.onCompleted(newVersion, asset.getString("browser_download_url"))
                } else {
                    showToast("No assets found for $repo")
                }
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
            packageInfo?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
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

    fun downloadAppItem(appItem: AppItem) {
        val currentlyDownloading = isDownloadInProgress.getAndSet(true)
        if (currentlyDownloading) return

        downloadFile(
            appItem.downloadUrl,
            appItem.packageName,
            object : DownloadListener {
                override fun onProgress(downloaded: Long, size: Long) {
                    appItem.bytesDownloaded = downloaded
                    appItem.fileSize = size
                    appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                }

                override fun onCompleted(tempFile: File) {
                    appListModel.downloadedApkQueue.add(tempFile)
                    appListModel.downloadedApkFile.postValue(tempFile)
                    appItem.state = AppEntryState.NOT_INSTALLED
                    refreshAppItem(appItem)
                    appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                    isDownloadInProgress.set(false)
                }

                override fun onFailure() {
                    appItem.state = AppEntryState.NOT_INSTALLED
                    refreshAppItem(appItem)
                    showToast("Download failed")
                }
            })
    }

    private fun downloadFile(
        downloadUrl: String,
        name: String,
        progressListener: DownloadListener,
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
                val tempFile = File(activity?.getExternalFilesDir(null), "$name.apk")
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
}