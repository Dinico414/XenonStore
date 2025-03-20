package com.xenon.store

import android.content.Context
import android.content.res.Resources
import com.xenon.store.viewmodel.LiveListItem

enum class AppEntryState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    INSTALLED_AND_OUTDATED,
}

data class AppItem(
    val name: String,
    val iconPath: String,
    val githubUrl: String,
    val packageName: String,
) : LiveListItem {

    override var id: Int = -1;
    var state: AppEntryState = AppEntryState.NOT_INSTALLED
    var installedVersion: String = ""
    var newVersion: String = ""
    // Download progressbar variables
    var bytesDownloaded: Long = 0
    var fileSize: Long = 0
    var downloadUrl: String = ""

    private val ownerRepoRegex = "^https://[^/]*github\\.com/([^/]+)/([^/]+)".toRegex()
    // Github url is also checked for validity
    val owner = ownerRepoRegex.find(githubUrl)!!.groups[1]!!.value
    val repo = ownerRepoRegex.find(githubUrl)!!.groups[2]!!.value

    private val iconRegex = "^@([^/]+)/([^/]+)".toRegex()
    private val iconDirectory = iconRegex.find(iconPath)?.groups?.get(1)?.value
    private val iconName = iconRegex.find(iconPath)?.groups?.get(2)?.value

    fun getDrawableId(context: Context): Int {
        if (iconDirectory == null || iconName == null) return 0
        return context.resources.getIdentifier(iconName, iconDirectory, context.packageName)
    }
}