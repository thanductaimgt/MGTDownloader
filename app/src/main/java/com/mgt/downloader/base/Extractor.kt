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
            val targetFileName =
                "${
                    webContent.findValue(
                        title.prefix,
                        title.postfix,
                        title.default,
                        target = title.target,
                    )
                }.mp4"
            val targetThumbUrl =
                webContent.findValue(
                    thumbUrl.prefix,
                    thumbUrl.postfix,
                    thumbUrl.default,
                    target = thumbUrl.target,
                )
            val targetDownloadUrl = webContent.findValue(
                downloadUrl.prefix,
                downloadUrl.postfix,
                downloadUrl.default,
                target = downloadUrl.target,
            )!!

            val targetWidth =
                webContent.findValue(
                    width.prefix,
                    width.postfix,
                    width.default,
                    target = width.target,
                )?.toInt() ?: 1
            val targetHeight =
                webContent.findValue(
                    height.prefix,
                    height.postfix,
                    height.default,
                    target = height.target,
                )?.toInt()
                    ?: 1

            val fileSize = Utils.getFileSize(targetDownloadUrl)
            val isMultipartSupported = runCatching {
                Utils.isMultipartSupported(targetDownloadUrl)
            }.getOrDefault(false)

            return FilePreviewInfo(
                targetFileName,
                url,
                targetDownloadUrl,
                fileSize,
                -1,
                -1,
                thumbUri = targetThumbUrl,
                thumbRatio = Utils.getFormatRatio(
                    targetWidth,
                    targetHeight
                ),
                isMultipartSupported = isMultipartSupported
            )
        }
    }
}