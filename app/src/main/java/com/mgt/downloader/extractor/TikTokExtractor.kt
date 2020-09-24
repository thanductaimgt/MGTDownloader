package com.mgt.downloader.extractor

import org.apache.commons.lang.StringEscapeUtils
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Utils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

class TikTokExtractor : BaseExtractor {
    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            val uRL = URL(url)
            val conn = uRL.openConnection()

            //Set the user agent so TikTok will think we're a person using a browser instead of a program
            conn.setRequestProperty(
                "User-Agent",
                BaseExtractor.USER_AGENT
            )

            //Set up the bufferedReader
            val br = BufferedReader(InputStreamReader(conn.getInputStream()))

            /*
             * Read every line until we get
             * to a string with the text 'videoObject'
             * which is where misc. information about
             * the user is stored and where the video
             * URL is stored too
             */
            var data = br.readLine()
            while (data != null) {
                if (data.contains("videoObject")) {
                    // Read up until we reach a string in the HTML file valled 'videoObject'
                    break
                }
                data = br.readLine()
            }

            //Close the bufferedReader as we don't need it anymore
            br.close()

            /*
             * Because we are viewing the raw source code from
             * the website, there's a lot of trash including but not
             * limited to HTML tags, javascript, random text, and so
             * on. We don't want that. That's why it will be cropped
             * out down below
             */

            //Crop out the useless tags and code behind the VideoObject string
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

            FilePreviewInfo(
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
        }.subscribe(observer)
    }
}