package com.mgt.downloader.ui

import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseViewModel
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.extractor.ExtractorManager
import com.mgt.downloader.rxjava.SingleObserver


class MainViewModel : BaseViewModel() {
    fun getFilePreviewInfo(
        url: String,
        observer: SingleObserver<FilePreviewInfo>
    ) {
        MyApplication.fileInfoCaches[url]?.let {
            observer.onSuccess(it)
        } ?: ExtractorManager.create(this, url).extract(url, observer)
    }
}