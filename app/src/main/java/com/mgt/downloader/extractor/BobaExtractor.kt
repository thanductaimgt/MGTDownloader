package com.mgt.downloader.extractor

import com.mgt.downloader.base.JsWebExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.findValue


class BobaExtractor : JsWebExtractor() {
    override fun extract(
        url: String,
        webContent: String
    ):FilePreviewInfo {
        var data = webContent
        var fileName = data.findValue("property=\"og:title\" content=\"", "\"")

        var isVideo = true
        var idx = data.indexOf("<video")
        if (idx == -1) {
            idx = data.indexOf("style=\"background-image:")
            isVideo = false
        }

        data = data.substring(idx)

        //Grab content URL (video file)
        val downloadUrl: String
        val thumbUrl: String?

        val width = 473
        val height = 840

        if (isVideo) {
            thumbUrl = data.findValue("poster=\"", "\"")
            downloadUrl = data.findValue("<source src=\"", "\"")
            fileName = "$fileName.mp4"
        } else {
            downloadUrl = data.findValue("url(&quot;", "\"")
            thumbUrl = downloadUrl
            fileName = "$fileName.jpg"
        }

        val fileSize = -1L;//Utils.getFileSize(url)

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
            )
        )
    }
}