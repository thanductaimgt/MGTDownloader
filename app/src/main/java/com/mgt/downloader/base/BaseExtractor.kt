package com.mgt.downloader.base

import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObserver

interface BaseExtractor {
    fun extract(url:String, observer: SingleObserver<FilePreviewInfo>)

    companion object{
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36"
    }
}