package com.mgt.downloader.base

import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.utils.recordNonFatalException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection


abstract class WebHtmlExtractor<C : ExtractorConfig>(hasDisposable: HasDisposable) :
    Extractor<C>(hasDisposable) {
    final override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(unboundExecutorService) {
            extract(url)
        }.subscribe(observer)
    }

    abstract fun extract(url: String): FilePreviewInfo

    protected fun getHtmlContent(url: String): StringBuilder? {
        var conn: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            conn = utils.openConnection(url)
            reader = BufferedReader(InputStreamReader(conn.inputStream))
            val streamMap = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                streamMap.append(line)
            }
            return streamMap
        } catch (t: Throwable) {
            recordNonFatalException(t)
            return null
        } finally {
            reader?.close()
            conn?.disconnect()
        }
    }
}