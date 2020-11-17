package com.mgt.downloader.extractor

import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.findValue


class TwitterExtractor(hasDisposable: HasDisposable) : WebJsExtractor(hasDisposable) {
    override fun extract(
        url: String,
        webContent: String
    ):FilePreviewInfo {
        var data = webContent

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

        val fileName:String
        if (isVideo) {
            thumbUrl = data.findValue("poster=\"", "\"")
            downloadUrl = data.findValue("src=\"", "\"")
            fileName = "Twitter video.mp4"
        } else {
            downloadUrl = data.findValue("url(&quot;", "\"")
            thumbUrl = downloadUrl
            fileName = "Twitter image.png"
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