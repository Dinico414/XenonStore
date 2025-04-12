package com.xenon.store.fragments

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_UNINSTALL_PACKAGE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.xenon.commons.accesspoint.R.color
import com.xenon.commons.accesspoint.R.drawable
import com.xenon.store.AppEntryState
import com.xenon.store.AppItem
import com.xenon.store.AppListAdapter
import com.xenon.store.AppListChangeType
import com.xenon.store.R
import com.xenon.store.databinding.FragmentAppListBinding
import com.xenon.store.viewmodel.AppListViewModel
import com.xenon.store.viewmodel.LiveListViewModel
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
class AppListFragment : Fragment(R.layout.fragment_app_list) {
    private val TAG = AppListFragment::class.qualifiedName
    private lateinit var binding: FragmentAppListBinding
    private lateinit var appListModel: AppListViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var activeSnackbar: Snackbar? = null
    private lateinit var networkChangeListener: NetworkChangeListener

    private lateinit var client: OkHttpClient

    private lateinit var installPermissionLauncher: ActivityResultLauncher<Intent>
    private val isDownloadInProgress = AtomicBoolean(false)
    private val isRefreshInProgress = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appListModel = ViewModelProvider(this)[AppListViewModel::class.java]

        val context = requireContext()

        client = OkHttpClient.Builder()
            .cache(Cache(
                directory = File(context.cacheDir, "http_cache"),
                maxSize = 10L * 1024L * 1024L // 10 MiB
            ))
            .eventListener(object : EventListener() {
                override fun cacheHit(call: Call, response: Response) {
                    super.cacheHit(call, response)
                    Log.d(TAG, "CACHE HIT ${response.request.url}")
                }
            })
            .build()

        if (appListModel.getList().isEmpty())
            fetchAndRefreshAppList(useCache = false)
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
            fetchAndRefreshAppList(useCache = false)
        }

        installPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult())
            { _ ->
                if (checkInstallPermission()) {
                    showToast(getString(R.string.permission_granted))
                } else {
                    showToast(getString(R.string.permission_denied))
                }
            }
        networkChangeListener = NetworkChangeListener(
            requireContext(),
            onNetworkAvailable = {
                Log.d(TAG, "Network connected")
                activeSnackbar?.dismiss()
                activeSnackbar = null

                fetchAndRefreshAppList()
            },
            onNetworkUnavailable = {
                Log.d(TAG, "Network disconnected")
                showNoInternetSnackbar()

                refreshAppList()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // It seems NetworkChangeListener.register is already calling the callbacks
        networkChangeListener.register()
//        if (isNetworkAvailable()) {
//            networkChangeListener.onNetworkAvailable()
//        } else {
//            networkChangeListener.onNetworkUnavailable()
//        }
//        fetchAndRefreshAppList()
    }

    override fun onPause() {
        super.onPause()
        networkChangeListener.unregister()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun fetchAndRefreshAppList(useCache: Boolean = true, synchronous: Boolean = false) {
        if (isRefreshInProgress.getAndSet(true)) return
        val urlString =
            "https://raw.githubusercontent.com/Dinico414/Xenon-Commons/master/accesspoint/src/main/java/com/xenon/commons/accesspoint/app_list.json"

        downloadToString(urlString, object : DownloadListener<String> {
            override fun onProgress(downloaded: Long, size: Long) {}
            override fun onCompleted(result: String) {
                isRefreshInProgress.set(false)
                val hash = result.hashCode()
                Log.d(TAG, "Fetched app list hash: ${appListModel.cachedJsonHash} == $hash")
                if (appListModel.cachedJsonHash != 0 && appListModel.getList().isNotEmpty() && appListModel.cachedJsonHash == hash) {
                    // app list has not changed
                    refreshAppList(useCache)
                    return
                }
                appListModel.cachedJsonHash = hash
                val appList = parseAppListJson(result)
                // Will refresh the list in the MutableLiveData listener
                appListModel.setList(appList)
            }
            override fun onFailure(error: String) {
                showErrorSnackbar(error)
                isRefreshInProgress.set(false)
            }
        }, useCache, synchronous)
    }

    private fun parseAppListJson(jsonString: String): ArrayList<AppItem> {
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
        val adapter = AppListAdapter(
            context,
            appListModel.getList(),
            object : AppListAdapter.AppItemListener {
                override fun installButtonClicked(appItem: AppItem, position: Int) {
                    when (appItem.state) {
                        AppEntryState.NOT_INSTALLED,
                        AppEntryState.INSTALLED_AND_OUTDATED,
                            -> {
                            if (appItem.downloadUrl == "") {
                                val noData = getString(R.string.no_data)
                                showErrorSnackbar("$noData ${appItem.name}")
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
                                activity?.packageManager?.getLaunchIntentForPackage(appItem.packageName)
                            )
                        }
                    }
                    appListModel.update(appItem)
                }
                override fun uninstallButtonClicked(appItem: AppItem, position: Int) {
                    openUninstallDialog(appItem.packageName)
                }
                override fun openButtonClicked(appItem: AppItem, position: Int) {
                    openApp(appItem.packageName)
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
                } else if (position == 0) {
                    outRect.top = marginInPx
                }
            }
        })

        appListModel.liveListEvent.observe(viewLifecycleOwner) { _ ->
            while (true) {
                val change = appListModel.listEventQueue.poll() ?: break
                when (change.type) {
                    LiveListViewModel.ListChangedType.ADD -> {
                        refreshAppItem(change.item!!)
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
                        refreshAppList(false)
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


    private fun refreshAppList(useCache: Boolean = true) {
        activity?.runOnUiThread {
            binding.swipeRefreshLayout.isRefreshing = true
        }

        refreshAppItem(appListModel.storeAppItem, useCache)

        for (appItem in appListModel.getList()) {
            refreshAppItem(appItem, useCache)
        }

        activity?.runOnUiThread {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun refreshAppItem(appItem: AppItem, useCache: Boolean = true) {
        val preReleases = sharedPreferences.getBoolean("pre_releases", false)

        if (appItem.newIsPreRelease != preReleases) {
            appItem.newVersion = ""
            appItem.downloadUrl = ""
            appItem.newIsPreRelease = false
        }

        appItem.installedVersion = getInstalledAppVersion(appItem.packageName) ?: ""

        if (appItem.state == AppEntryState.DOWNLOADING) {
//            downloadAppItem(appItem)
        } else if (appItem.isOutdated()) {
            appItem.state = AppEntryState.INSTALLED_AND_OUTDATED
        } else if (appItem.installedVersion != "") {
            appItem.state = AppEntryState.INSTALLED
        } else {
            appItem.state = AppEntryState.NOT_INSTALLED
        }
        appListModel.update(appItem, AppListChangeType.STATE_CHANGE)

        if (isNetworkAvailable() && (appItem.downloadUrl == "" || !useCache)) {

            getNewReleaseVersionGithub(appItem.owner, appItem.repo, preReleases, useCache,
                object : GithubReleaseAPICallback {
                    override fun onCompleted(version: String, downloadUrl: String) {
                        appItem.newIsPreRelease = preReleases
                        appItem.downloadUrl = downloadUrl
                        if (appItem.isNewerVersion(version)) {
                            appItem.newVersion = version
                            if (appItem.state == AppEntryState.INSTALLED) {
                                appItem.state = AppEntryState.INSTALLED_AND_OUTDATED
                                appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                            }
                        }
                    }

                    override fun onFailure(error: String) {
                        showErrorSnackbar("$error ${appItem.repo}")
                    }
                })
        }
    }

    interface GithubReleaseAPICallback {
        fun onCompleted(version: String, downloadUrl: String)
        fun onFailure(error: String)
    }

    private fun getNewReleaseVersionGithub(
        owner: String,
        repo: String,
        preRelease: Boolean,
        useCache: Boolean,
        callback: GithubReleaseAPICallback
    ) {
        val url = if (preRelease) "https://api.github.com/repos/$owner/$repo/releases?per_page=1"
        else "https://api.github.com/repos/$owner/$repo/releases/latest"

        downloadToString(url, object : DownloadListener<String> {
            override fun onProgress(downloaded: Long, size: Long) {}
            override fun onCompleted(result: String) {
                Log.d("response body $repo", result)

                // Parse json
                val latestRelease: JSONObject
                if (preRelease) {
                    val releases = JSONArray(result)
                    latestRelease = releases.getJSONObject(0)
                } else {
                    latestRelease = JSONObject(result)
                }

                val assets = latestRelease.getJSONArray("assets")
                val newVersion = latestRelease.getString("tag_name")
                if (assets.length() > 0) {
                    val asset = assets.getJSONObject(0)
                    callback.onCompleted(newVersion, asset.getString("browser_download_url"))
                } else {
                    val noAssets = getString(R.string.no_assets)
                    callback.onFailure("$noAssets $repo")
                }
            }
            override fun onFailure(error: String) {
                callback.onFailure(error)
            }
        }, useCache)
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

    interface DownloadListener<T> {
        fun onProgress(downloaded: Long, size: Long)
        fun onCompleted(result: T)
        fun onFailure(error: String)
    }

    fun downloadAppItem(appItem: AppItem) {
//        val currentlyDownloading = isDownloadInProgress.getAndSet(true)
//        if (currentlyDownloading) return

        downloadToFile(
            appItem.downloadUrl,
            "${appItem.packageName}.apk",
            object : DownloadListener<File> {
                override fun onProgress(downloaded: Long, size: Long) {
                    appItem.bytesDownloaded = downloaded
                    appItem.fileSize = size
                    appListModel.update(appItem, AppListChangeType.STATE_CHANGE)
                }

                override fun onCompleted(tempFile: File) {
                    Log.d(TAG, "Completed download: $tempFile")
                    appListModel.downloadedApkQueue.add(tempFile)
                    appListModel.downloadedApkFile.postValue(tempFile)
                    appItem.state = AppEntryState.NOT_INSTALLED
                    refreshAppItem(appItem)
//                    isDownloadInProgress.set(false)
                }

                override fun onFailure(error: String) {
                    appItem.state = AppEntryState.NOT_INSTALLED
                    refreshAppItem(appItem)
                    showErrorSnackbar(getString(R.string.download_failed))
                }
            },
            useCache = false)
    }

    private fun downloadToString(
        url: String,
        progressListener: DownloadListener<String>,
        useCache: Boolean = true,
        synchronous: Boolean = false,
    ) {
        Log.d(TAG, "downloadToString(url=$url, useCache=$useCache)")
        val request = Request.Builder()
            .url(url)
            .build()
        (if (useCache) client else OkHttpClient()).newCall(request).apply {
            val callback = object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        progressListener.onFailure(getString(R.string.response_error_code, response.code.toString()))
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        progressListener.onFailure(getString(R.string.empty_body))
                        return
                    }
                    progressListener.onCompleted(responseBody)
                }

                override fun onFailure(call: Call, e: IOException) {
                    progressListener.onFailure(getString(R.string.download_failed))
                }
            }
            if (!synchronous) this.enqueue(callback)
            else {
                try {
                    val response = this.execute()
                    callback.onResponse(this, response)
                } catch (e: IOException) {
                    callback.onFailure(this, e)
                }
            }
        }
    }

    private fun downloadToFile(
        url: String,
        filename: String,
        progressListener: DownloadListener<File>,
        useCache: Boolean = true,
        synchronous: Boolean = false,
    ) {
        Log.d(TAG, "downloadToFile(url=$url, useCache=$useCache)")
        val request = Request.Builder()
            .url(url)
            .build()
        (if (useCache) client else OkHttpClient()).newCall(request).apply {
            val callback = object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        progressListener.onFailure(getString(R.string.response_error_code, response.code.toString()))
                        return
                    }

                    val contentLength = response.body?.contentLength() ?: -1
                    var downloadedBytes: Long = 0
                    val buffer = ByteArray(8192)
                    val inputStream = response.body?.byteStream()
                    val tempFile = File(activity?.getExternalFilesDir(null), filename)
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
                        progressListener.onFailure(getString(R.string.download_failed))
                    } finally {
                        inputStream?.close()
                        outputStream.close()
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    progressListener.onFailure(getString(R.string.download_failed))
                }
            }
            if (!synchronous) this.enqueue(callback)
            else {
                try {
                    val response = this.execute()
                    callback.onResponse(this, response)
                } catch (e: IOException) {
                    callback.onFailure(this, e)
                }
            }
        }
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

    private fun openUninstallDialog(packageName: String) {
        val intent = Intent(ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
        }
        requireContext().startActivity(intent)
    }

    private fun openApp(packageName: String) {
        val context = requireContext()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        context.startActivity(intent)
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

    private fun showNoInternetSnackbar() {
        activity?.runOnUiThread {
            val snackbar = Snackbar.make(
                binding.root,
                getString(R.string.offline_message),
                Snackbar.LENGTH_INDEFINITE
            )
            val backgroundDrawable =
                ResourcesCompat.getDrawable(resources, drawable.tile_popup, null)

            snackbar.view.background = backgroundDrawable
            snackbar.setTextColor(resources.getColor(color.inverseOnSurface, null))
            snackbar.setBackgroundTint(resources.getColor(color.inverseSurface, null))
            snackbar.setAction(getString(R.string.open_settings)) {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                startActivity(intent)
            }
            activeSnackbar = snackbar
            snackbar.show()
        }
    }

    private fun showErrorSnackbar(message: String) {
        activity?.runOnUiThread {
            val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            val backgroundDrawable =
                ResourcesCompat.getDrawable(resources, drawable.tile_popup, null)

            snackbar.view.background = backgroundDrawable
            snackbar.setTextColor(resources.getColor(color.onError, null))
            snackbar.setBackgroundTint(resources.getColor(color.error, null))
            activeSnackbar = snackbar
            snackbar.show()
        }
    }

    inner class NetworkChangeListener(
        private val context: Context,
        val onNetworkAvailable: () -> Unit,
        val onNetworkUnavailable: () -> Unit
    ) : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            onNetworkAvailable()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            onNetworkUnavailable()
        }

        fun register() {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, this)
        }

        fun unregister() {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(this)
        }
    }
}