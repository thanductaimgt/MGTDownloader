package com.mgt.downloader.extractor

import com.mgt.downloader.base.HtmlWebExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.utils.Utils
import org.apache.commons.lang.StringEscapeUtils
import java.io.BufferedReader
import java.io.InputStreamReader

class TikTokExtractor : HtmlWebExtractor() {
    override fun extract(url: String):FilePreviewInfo {
        val conn = Utils.openConnection(url)
        val br = BufferedReader(InputStreamReader(conn.inputStream))

        var data = br.readLine()
        while (data != null) {
            if (data.contains("videoObject")) {
                break
            }
            data = br.readLine()
        }
        br.close()

        data = data.substring(data.indexOf("videoData"))

        //Grab content URL (video file)
        val downloadUrl = data.substring(data.indexOf("urls") + 8).let {
            StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
        }

        val width = data.substring(data.indexOf("width") + 7).let {
            it.substring(0, it.indexOf(","))
        }.toInt()

        val height = data.substring(data.indexOf("height") + 8).let {
            it.substring(0, it.indexOf(","))
        }.toInt()

        val thumbUrl = data.substring(data.indexOf("covers") + 10).let {
            StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
        }

        val title = data.substring(data.indexOf("title") + 8).let {
            it.substring(0, it.indexOf("\""))
        }

        val description = data.substring(data.indexOf("desc") + 7).let {
            it.substring(0, it.indexOf("\""))
        }

        val fileName = "$title | $description.mp4"

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