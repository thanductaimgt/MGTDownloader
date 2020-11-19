package com.mgt.downloader.base

import com.mgt.downloader.ExtractFieldsManager
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.*

abstract class Extractor(protected val hasDisposable: HasDisposable) {
    open val extractorName = "base"

    abstract fun extract(url: String, observer: SingleObserver<FilePreviewInfo>)

    protected open fun extract(url: String, webContent: String): FilePreviewInfo {
        return try {
            ExtractFieldsManager.getRemoteExtractFields(extractorName).let {
                logD(TAG, it.toString())
                getFilePreviewInfo(url, webContent, it)
            }
        } catch (t: Throwable) {
            logE(TAG, "parse remote fields fail")
            logE(TAG, "webContent: $webContent")
            t.printStackTrace()
            ExtractFieldsManager.getLocalExtractFields(extractorName).let {
                logD(TAG, it.toString())
                getFilePreviewInfo(url, webContent, it)
            }
        }
    }

    private fun getFilePreviewInfo(
        url: String,
        webContent: String,
        extractFields: ExtractFields
    ): FilePreviewInfo {
        extractFields.apply {
            val fileName =
                "${webContent.findValue(title.prefix, title.postfix, title.default)}.mp4"
            val thumbUrl =
                webContent.findValue(thumbUrl.prefix, thumbUrl.postfix, thumbUrl.default)
            val downloadUrl = webContent.findValue(
                downloadUrl.prefix,
                downloadUrl.postfix,
                downloadUrl.default
            )!!

            val width =
                webContent.findValue(width.prefix, width.postfix, width.default)?.toInt() ?: 1
            val height =
                webContent.findValue(height.prefix, height.postfix, height.default)?.toInt()
                    ?: 1

            val fileSize = Utils.getFileSize(downloadUrl)
            val isMultipartSupported = Utils.isMultipartSupported(downloadUrl)

            return FilePreviewInfo(
                fileName,
                url,
                downloadUrl,
                fileSize,
                -1,
                -1,
                thumbUri = thumbUrl,
                thumbRatio = Utils.getFormatRatio(
                    width,
                    height
                ),
                isMultipartSupported = isMultipartSupported
            )
        }
    }
}