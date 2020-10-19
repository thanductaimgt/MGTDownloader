package com.mgt.downloader.extractor

import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.base.HtmlWebExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.utils.Utils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern


class FacebookExtractor : HtmlWebExtractor() {
    override fun extract(url: String):FilePreviewInfo {
        val conn = Utils.openConnection(url)
        var reader: BufferedReader? = null
        conn.setRequestProperty("User-Agent", BaseExtractor.USER_AGENT)
        val streamMap = StringBuilder()
        try {
            reader =
                BufferedReader(InputStreamReader(conn.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                streamMap.append(line)
            }
        } finally {
            reader?.close()
            conn.disconnect()
        }
        val fileNamePattern =
            Pattern.compile("\"name\":\"(.+?)\"")
        val fileNameMatcher = fileNamePattern.matcher(streamMap)
        val fileName = if (fileNameMatcher.find()) {
            val fileNameEscaped = fileNameMatcher.group().let {
                "${it.substring(8, it.length - 1)}.mp4"
            }
            try {
                Utils.unescapePerlString(fileNameEscaped)
            } catch (t: Throwable) {
                fileNameEscaped
            }
        } else {
            "No_name"
        }

        val hdVideo =
            Pattern.compile("(hd_src):\"(.+?)\"")
        val hdVideoMatcher = hdVideo.matcher(streamMap)
        val downloadUrl = if (hdVideoMatcher.find()) {
            hdVideoMatcher.group().let {
                it.substring(8, it.length - 1) //hd_scr: 8 char
            }
        } else {
            null
        }

        val thumbPattern = Pattern.compile("\"thumbnailUrl\":\"(.+?)\"")
        val thumbMatcher = thumbPattern.matcher(streamMap)
        val thumbnailUrl = if (thumbMatcher.find()) {
            thumbMatcher.group().let {
                it.substring(16, it.length - 1).replace("\\", "")
            }
        } else {
            null
        }

        val widthPattern = Pattern.compile("\"width\":[0-9]+")
        val widthMatcher = widthPattern.matcher(streamMap)
        val width = if (widthMatcher.find()) {
            widthMatcher.group().let {
                it.substring(8, it.length)
            }.toInt()
        } else {
            null
        }

        val heightPattern = Pattern.compile("\"height\"[ ]*:[ ]*[0-9]+")
        val heightMatcher = heightPattern.matcher(streamMap)
        val height = if (heightMatcher.find()) {
            heightMatcher.group().let {
                it.substring(9, it.length)
            }.toInt()
        } else {
            null
        }

        if (downloadUrl == null || thumbnailUrl == null) {
            throw RuntimeException("Url Not Valid")
        }

        val fileSize = Utils.getFileSize(downloadUrl)

        return FilePreviewInfo(
            fileName,
            url,
            downloadUrl,
            fileSize,
            -1,
            -1,
            thumbUri = thumbnailUrl,
            thumbRatio = Utils.getFormatRatio(
                width,
                height
            )
        )
    }
}