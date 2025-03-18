package com.xenon.store

import android.util.Log
import com.xenon.store.viewmodel.LiveListItem

enum class AppEntryState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    INSTALLED_AND_OUTDATED,
}

data class AppItem(
    val name: String,
    val icon: String,
    val githubUrl: String,
    val packageName: String,
) : LiveListItem {

    override var id: Int = -1;
    var state: AppEntryState = AppEntryState.NOT_INSTALLED
    var installedVersion: String = ""
    var newVersion: String = ""
    var downloadUrl: String = ""
    // Download progressbar variables
    var bytesDownloaded: Long = 0
    var fileSize: Long = 0

    private val ownerRepoRegex = "^https://[^/]*github\\.com/([^/]+)/([^/]+)".toRegex()

//    fun getOwnerRepo(): String {
//        val m = ownerRepoRegex.find(githubUrl)
//        Log.d("aaa 1", m?.groups?.get(1)?.value ?: "")
//        Log.d("aaa 2", m?.groups?.get(2)?.value ?: "")
//        return m?.groups?.get(1)?.value ?: ""
//    }

    // Github url is also checked for validity
    val owner = ownerRepoRegex.find(githubUrl)!!.groups[1]!!.value
    val repo = ownerRepoRegex.find(githubUrl)!!.groups[2]!!.value
}