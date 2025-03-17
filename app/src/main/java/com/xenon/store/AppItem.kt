package com.xenon.store

import com.xenon.store.viewmodel.LiveListItem

enum class AppEntryState {
    NOT_INSTALLED,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLED
}

data class AppItem(
    override var id: Int,
    var name: String,
    var icon: String,
    var currentVersion: String,
    var state: AppEntryState = AppEntryState.NOT_INSTALLED
) : LiveListItem {

}