package com.mgt.downloader.extractor

import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.serialize_model.ExtractorConfig
import org.apache.commons.lang.StringEscapeUtils
import kotlin.reflect.KClass


class InstagramExtractor(hasDisposable: HasDisposable) :
    WebJsExtractor<ExtractorConfig>(hasDisposable) {
    override val extractorConfigClass: KClass<ExtractorConfig>
        get() = ExtractorConfig::class

    override fun extract(url: String, webContent: String): FilePreviewInfo {
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
            thumbRatio = utils.getFormatRatio(
                width,
                height
            )
        )
    }
}