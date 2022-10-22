package com.mgt.downloader.extractor.tiktok

import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.di.DI.extractorConfigManager
import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.serialize_model.TikTokExtractorConfig
import com.mgt.downloader.ui.MainActivity
import kotlin.reflect.KClass

class TikTokExtractorV2(hasDisposable: HasDisposable) :
    WebJsExtractor<TikTokExtractorConfig>(hasDisposable) {
    override val extractorName = "tiktok"
    override val extractorConfigClass: KClass<TikTokExtractorConfig>
        get() = TikTokExtractorConfig::class

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        val onSuccess = { webContent: String ->
            SingleObservable.fromCallable(unboundExecutorService) {
                extract(url, webContent)
            }.subscribe(observer)
            MainActivity.jsInterface.onSuccess = null
            // cannot call on bridge thread, so post on main
            handler.post { MainActivity.reloadWebView() }
        }

        if (!fetchRemoteSuccess) {
            SingleObservable.fromCallable(unboundExecutorService) {
                extractorConfigManager.getRemote(extractorName, TikTokExtractorConfig::class)
            }.subscribe(object : SingleObserver<ExtractorConfig?>(hasDisposable) {
                override fun onSuccess(result: ExtractorConfig?) {
                    super.onSuccess(result)
                    result?.let {
                        extractorConfigManager.updateMemCache(extractorName, result)
                        extractorConfigManager.updateFileCache(extractorName, result)
                        fetchRemoteSuccess = true
                    }
                }
            })
        }

        SingleObservable.fromCallable(unboundExecutorService) {
            extractorConfigManager.getConfig(
                extractorName,
                TikTokExtractorConfig::class
            )?.jsCode?.format(url)
                ?.let { "$it;" } ?: ""
        }.subscribe(object : SingleObserver<String>(hasDisposable) {
            override fun onSuccess(result: String) {
                super.onSuccess(result)

                MainActivity.jsInterface.onSuccess = onSuccess

                val curTime = System.currentTimeMillis()
                val delayTimeLoadWeb = (DELAY_LOAD_TIME_WEB - (curTime - (MainActivity.loadWebTime
                    ?: System.currentTimeMillis()))).coerceAtLeast(0)

                handler.postDelayed({
                    webView.loadUrl("${result}javascript:window.HtmlViewer.dummy();")
                    handler.postDelayed({
                        webView.loadUrl(
                            "javascript:window.HtmlViewer.onLoaded" +
                                    "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                        )
                    }, DELAY_LOAD_TIME_URL)
                }, delayTimeLoadWeb)
            }

            override fun onError(t: Throwable) {
                super.onError(t)
                observer.onError(t)
            }
        })
    }

    companion object {
        private const val DELAY_LOAD_TIME_WEB = 5000L
        private const val DELAY_LOAD_TIME_URL = 5000L
        const val JS_INTERFACE_NAME = "HtmlViewer"
        const val WEB_URL = "https://snaptik.app/vn"

        private var fetchRemoteSuccess = false
    }
}
