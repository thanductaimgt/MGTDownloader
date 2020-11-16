package com.mgt.downloader.base

import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Utils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection


abstract class HtmlWebExtractor : BaseExtractor {
    final override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            extract(url)
        }.subscribe(observer)
    }

    abstract fun extract(url: String): FilePreviewInfo

    protected fun getHtmlContent(url: String): StringBuilder? {
        var conn: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            conn = Utils.openConnection(url)
            reader = BufferedReader(InputStreamReader(conn.inputStream))
            val streamMap = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                streamMap.append(line)
            }
            return streamMap
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        } finally {
            reader?.close()
            conn?.disconnect()
        }
    }
}