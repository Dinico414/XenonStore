package com.xenon.store

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

//    fun getOwnerRepo(): Set<String> {
//        val m = ownerRepoRegex.find(githubUrl)
//        return setOf(m?.groups?.get(1)?.value ?: "", m?.groups?.get(2)?.value ?: "")
//    }

    // Github url is also checked for validity
    val owner = ownerRepoRegex.matchAt(githubUrl, 0)?.value ?: ""
    val repo = ownerRepoRegex.matchAt(githubUrl, 1)?.value ?: ""
}