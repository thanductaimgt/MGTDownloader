package com.mgt.downloader.base

import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver


abstract class HtmlWebExtractor : BaseExtractor {
    final override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            extract(url)
        }.subscribe(observer)
    }

    abstract fun extract(url: String):FilePreviewInfo
}