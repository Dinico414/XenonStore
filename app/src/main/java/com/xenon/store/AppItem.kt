package com.xenon.store

import com.xenon.store.viewmodel.LiveListItem

enum class AppEntryState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    INSTALLED_AND_OUTDATED,
}

data class AppItem(
    override var id: Int,
    var name: String,
    var icon: String = "",
    var currentVersion: String = "",
    var githubUrl: String = "",
    var packageName: String = "",
    var state: AppEntryState = AppEntryState.NOT_INSTALLED,
) : LiveListItem {
    // Download progressbar variables
    var bytesDownloaded: Int = 0
    var fileSize: Int = 0
}