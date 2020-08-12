package com.mgt.downloader.extractor

import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Utils

class OtherFileExtractor : BaseExtractor {
    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.zip(
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                Utils.getZipCentralDirInfo(
                    url
                )
            },
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                Utils.getFileSize(
                    url
                )
            }
        ) { pair: Pair<Int, Int>, fileSize: Long ->
            val fileName = Utils.getFileName(
                url,
                if (pair.first >= 0 || pair.second >= 0) "zip" else null
            )

            FilePreviewInfo(
                fileName,
                url,
                url,
                fileSize,
                pair.first, //centralDirOffset
                pair.second //centralDirSize
            )
        }.subscribe(observer)
    }
}