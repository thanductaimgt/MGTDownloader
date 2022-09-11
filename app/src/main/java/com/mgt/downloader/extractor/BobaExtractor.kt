package com.mgt.downloader.extractor

import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.DownloadUrlNotFoundThrowable
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.utils.findValue
import kotlin.reflect.KClass


class BobaExtractor(hasDisposable: HasDisposable) : WebJsExtractor<ExtractorConfig>(hasDisposable) {
    override val extractorConfigClass: KClass<ExtractorConfig>
        get() = ExtractorConfig::class

    override fun extract(
        url: String,
        webContent: String
    ): FilePreviewInfo {
        var fileName = webContent.findValue(
            "property=\"og:title\"(.*?)content=\"",
            "\"",
            default = "Boba story"
        )

        val isVideo = webContent.indexOf("<video(.*?)>") != -1

        val thumbUrl: String?
        val downloadUrl: String

        if (isVideo) {
            thumbUrl = webContent.findValue("poster=\"", "\"", default = null)
            downloadUrl = webContent.findValue("<source(.*?)src=\"", "\"", default = null)
                ?: throw DownloadUrlNotFoundThrowable()
            fileName = "$fileName.mp4"
        } else {
            downloadUrl = webContent.findValue(
                "style=\"(.*?)background-image:(.*?)url[(]&quot;",
                "&quot;[)]",
                null
            ) ?: throw DownloadUrlNotFoundThrowable()
            thumbUrl = downloadUrl
            fileName = "$fileName.jpg"
        }

        val width = 473
        val height = 840

        val fileSize = utils.getFileSize(url)

        return FilePreviewInfo(
            fileName,
            url,
            downloadUrl,
            fileSize,
            -1,
            -1,
            thumbUri = thumbUrl,
            thumbRatio = utils.getFormatRatio(
                width,
                height
            )
        )
    }
}