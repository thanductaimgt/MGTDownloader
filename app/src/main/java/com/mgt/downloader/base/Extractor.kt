package com.mgt.downloader.base

import com.mgt.downloader.di.DI.extractorConfigManager
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.DownloadUrlNotFoundThrowable
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.findValue
import com.mgt.downloader.utils.logD
import kotlin.reflect.KClass

abstract class Extractor<C : ExtractorConfig>(protected val hasDisposable: HasDisposable) {
    open val extractorName = "base"
    abstract val extractorConfigClass: KClass<C>

    abstract fun extract(url: String, observer: SingleObserver<FilePreviewInfo>)

    protected open fun extract(url: String, webContent: String): FilePreviewInfo {
        val extractConfig = extractorConfigManager.getConfig(extractorName, extractorConfigClass)
            ?: throw RuntimeException("No extractFields, extractor $extractorName")

        logD(TAG, extractConfig.toString())
        return getFilePreviewInfo(url, webContent, extractConfig)
    }

    private fun getFilePreviewInfo(
        url: String,
        webContent: String,
        extractConfig: ExtractorConfig
    ): FilePreviewInfo {
        extractConfig.extractFields?.apply {
            val targetFileName =
                "${
                    webContent.findValue(
                        title?.prefix,
                        title?.postfix,
                        title?.default,
                        target = title?.target,
                    )
                }.mp4"
            val targetThumbUrl =
                webContent.findValue(
                    thumbUrl?.prefix,
                    thumbUrl?.postfix,
                    thumbUrl?.default,
                    target = thumbUrl?.target,
                )
            val targetDownloadUrl = webContent.findValue(
                downloadUrl?.prefix,
                downloadUrl?.postfix,
                downloadUrl?.default,
                target = downloadUrl?.target,
            ) ?: throw DownloadUrlNotFoundThrowable()

            val targetWidth =
                webContent.findValue(
                    width?.prefix,
                    width?.postfix,
                    width?.default,
                    target = width?.target,
                )?.toInt() ?: 1
            val targetHeight =
                webContent.findValue(
                    height?.prefix,
                    height?.postfix,
                    height?.default,
                    target = height?.target,
                )?.toInt()
                    ?: 1

            val fileSize = utils.getFileSize(targetDownloadUrl)
            val isMultipartSupported = runCatching {
                utils.isMultipartSupported(targetDownloadUrl)
            }.getOrDefault(false)

            return FilePreviewInfo(
                targetFileName,
                url,
                targetDownloadUrl,
                fileSize,
                -1,
                -1,
                thumbUri = targetThumbUrl,
                thumbRatio = utils.getFormatRatio(
                    targetWidth,
                    targetHeight
                ),
                isMultipartSupported = isMultipartSupported
            )
        }
        throw RuntimeException("extractFields is null, extractConfig=$extractConfig")
    }
}