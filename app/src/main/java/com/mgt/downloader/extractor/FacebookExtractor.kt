package com.mgt.downloader.extractor

import com.mgt.downloader.base.HtmlWebExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.findValue
import com.mgt.downloader.utils.unescapeJava


class FacebookExtractor : HtmlWebExtractor() {
    override fun extract(url: String):FilePreviewInfo {
        val webContent = getHtmlContent(url)!!

        val fileName = "${webContent.findValue("VideoObject(.*?)\"name\":\"", "\"", default = "Facebook video")}.mp4"

        val downloadUrl = webContent.findValue("VideoObject(.*?)\"url\":\"", "\"", default = null)!!.unescapeJava()
        val thumbUrl = webContent.findValue("VideoObject(.*?)\"thumbnailUrl\":\"", "\"", default = null)?.unescapeJava()
        val width = webContent.findValue("VideoObject(.*?)\"width\":", ",", default = "1")!!.toInt()
        val height = webContent.findValue("VideoObject(.*?)\"height\":", ",", default = "1")!!.toInt()

        val fileSize = Utils.getFileSize(downloadUrl)

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