package com.mgt.downloader.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import com.mgt.downloader.MyApplication
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.extractor.*
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Utils


class MainViewModel : ViewModel() {
    @SuppressLint("CheckResult")
    fun getFilePreviewInfo(
        url: String,
        observer: SingleObserver<FilePreviewInfo>
    ) {
        val fileInfoCache = MyApplication.fileInfoCaches[url]
        if (fileInfoCache != null) {
            observer.onSuccess(fileInfoCache)
            return
        }

        when {
            Utils.isTikTokUrl(url) -> {
                TikTokExtractor()
            }
            Utils.isFacebookUrl(url) -> {
                FacebookExtractor()
            }
            Utils.isBobaUrl(url) -> {
                BobaExtractor()
            }
            Utils.isInstaUrl(url) -> {
                InstagramExtractor()
            }
            else -> {
                OtherFileExtractor()
            }
        }.extract(url, observer)
    }
}