package com.mgt.downloader.extractor

import com.mgt.downloader.base.Extractor
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.serialize_model.ExtractorConfig
import kotlin.reflect.KClass

class OtherFileExtractor(hasDisposable: HasDisposable) : Extractor<ExtractorConfig>(hasDisposable) {
    override val extractorConfigClass: KClass<ExtractorConfig>
        get() = ExtractorConfig::class

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.zip(
            SingleObservable.fromCallable(unboundExecutorService) {
                utils.getZipCentralDirInfo(
                    url
                )
            },
            SingleObservable.fromCallable(unboundExecutorService) {
                utils.getFileSize(
                    url
                )
            }
        ) { pair: Pair<Int, Int>, fileSize: Long ->
            val fileName = utils.getFileName(
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