package com.xenon.store.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xenon.store.AppItem
import com.xenon.store.viewmodel.LiveListViewModel
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class AppListViewModel : LiveListViewModel<AppItem>() {
    val downloadedApkFile: MutableLiveData<File> = MutableLiveData()
    val downloadedApkQueue: ConcurrentLinkedQueue<File> = ConcurrentLinkedQueue()
}