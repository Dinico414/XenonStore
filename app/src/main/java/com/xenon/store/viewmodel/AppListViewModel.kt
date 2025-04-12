package com.xenon.store.viewmodel

import androidx.lifecycle.MutableLiveData
import com.xenon.store.AppItem
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class AppListViewModel : LiveListViewModel<AppItem>() {
    val storeAppItemLive: MutableLiveData<AppItem> = MutableLiveData()
    val storeAppItem = AppItem(
        "Xenon Store",
        "@mipmap/ic_launcher",
        "https://github.com/Dinico414/XenonStore",
        "com.xenon.store"
    )
    val downloadedApkFile: MutableLiveData<File> = MutableLiveData()
    val downloadedApkQueue: ConcurrentLinkedQueue<File> = ConcurrentLinkedQueue()
    var cachedJsonHash: Int = 0

    init {
        storeAppItem.id = -1
    }

    override fun update(item: AppItem, payload: Any?) {
        if (item == storeAppItem) {
            storeAppItemLive.postValue(item)
        }
        else {
            super.update(item, payload)
        }
    }
}