package com.mgt.downloader.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import com.mgt.downloader.MyApplication
import com.mgt.downloader.extractor.OtherFileExtractor
import com.mgt.downloader.extractor.TikTokExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.extractor.FacebookExtractor
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Utils


class MainViewModel : ViewModel() {
    @SuppressLint("CheckResult")
    fun getFilePreviewInfo(
        context: Context,
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
            Utils.isFacebookUrl(url)->{
                FacebookExtractor()
            }
            else -> {
                OtherFileExtractor()
            }
        }.extract(url, observer)
    }
}