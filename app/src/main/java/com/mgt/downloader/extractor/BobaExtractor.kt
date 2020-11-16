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
        var fileName = webContent.findValue("property=\"og:title\"(.*?)content=\"", "\"", default = "Boba story")

        val isVideo = webContent.indexOf("<video(.*?)>")!=-1

        val thumbUrl:String?
        val downloadUrl:String

        if (isVideo) {
            thumbUrl = webContent.findValue("poster=\"", "\"", default = null)
            downloadUrl = webContent.findValue("<source(.*?)src=\"", "\"", default = null)!!
            fileName = "$fileName.mp4"
        } else {
            downloadUrl = webContent.findValue("style=\"(.*?)background-image:(.*?)url[(]&quot;", "&quot;[)]", null)!!
            thumbUrl = downloadUrl
            fileName = "$fileName.jpg"
        }

        val width = 473
        val height = 840

        val fileSize = Utils.getFileSize(url)

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