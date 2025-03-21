package com.xenon.store.viewmodel

import androidx.lifecycle.MutableLiveData
import com.xenon.store.AppItem
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class AppListViewModel : LiveListViewModel<AppItem>() {
    val storeAppItem: MutableLiveData<AppItem> = MutableLiveData()
    val downloadedApkFile: MutableLiveData<File> = MutableLiveData()
    val downloadedApkQueue: ConcurrentLinkedQueue<File> = ConcurrentLinkedQueue()

    override fun update(item: AppItem, payload: Any?) {
        if (item == storeAppItem.value) {
            storeAppItem.postValue(item)
        }
        else {
            super.update(item, payload)
        }
    }
}