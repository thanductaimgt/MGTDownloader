package com.mgt.downloader.extractor

import com.mgt.downloader.base.JsWebExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.utils.Utils
import org.apache.commons.lang.StringEscapeUtils


class InstagramExtractor : JsWebExtractor() {
    override fun extract(url: String, webContent: String):FilePreviewInfo {
        var data = webContent
        var fileName =
            data.substring("<title>".let { data.indexOf(it) + it.length }).let {
                it.substring(0, it.indexOf("</title>"))
            }

        var isVideo = true
        var idx = data.indexOf("<video")
        if (idx == -1) {
            idx = data.indexOf("<img")
            isVideo = false
        }

        data = data.substring(idx)

        //Grab content URL (video file)
        val downloadUrl: String
        val thumbUrl: String?

        val width = 473
        val height = 840

        if (isVideo) {
            thumbUrl = data.substring("poster=\"".let { data.indexOf(it) + it.length }).let {
                StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
            }

            downloadUrl = data.substring("src=\"".let { data.indexOf(it) + it.length }).let {
                StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
            }

            fileName = "$fileName.mp4"
        } else {
            downloadUrl = data.substring("src=\"".let { data.indexOf(it) + it.length }).let {
                StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
            }
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