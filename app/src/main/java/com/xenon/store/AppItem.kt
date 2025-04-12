@file:Suppress("unused")

package com.xenon.store

import android.annotation.SuppressLint
import android.content.Context
import com.xenon.store.viewmodel.LiveListItem

enum class AppEntryState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    INSTALLED_AND_OUTDATED,
}

data class AppItem(
    val nameMap: HashMap<String, String>,
    val iconPath: String,
    val githubUrl: String,
    val packageName: String,
) : LiveListItem {

    override var id: Int = -1
    var state: AppEntryState = AppEntryState.NOT_INSTALLED
    var installedVersion: String = ""
    var newVersion: String = ""
    var installedIsPreRelease = false
    var newIsPreRelease = false

    fun isOutdated(): Boolean {
        return installedVersion != "" && isNewerVersion(newVersion)
    }

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

    fun getName(langCode: String): String {
        return nameMap.get(langCode) ?: nameMap.get("en") ?: "App"
    }

    fun getDrawableId(context: Context): Int {
        if (iconDirectory == null || iconName == null) return 0
        return context.resources.getIdentifier(iconName, iconDirectory, context.packageName)
    }

    fun isNewerVersion(latestVersion: String): Boolean {
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
}