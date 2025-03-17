package com.xenon.store

import com.xenon.store.viewmodel.LiveListItem

data class AppItem(
    override var id: Int,
    var name: String,
    var icon: String,
    var currentVersion: String
) : LiveListItem {

}