package com.mgt.downloader.extractor.tiktok

import android.util.Base64
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.di.DI.extractorConfigManager
import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.CompletableObservable
import com.mgt.downloader.rxjava.CompletableObserver
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.serialize_model.TikTokExtractorConfig
import com.mgt.downloader.ui.MainActivity
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

class TikTokExtractorV2(hasDisposable: HasDisposable) :
    WebJsExtractor<TikTokExtractorConfig>(hasDisposable) {
    override val extractorName = "tiktok"
    override val extractorConfigClass: KClass<TikTokExtractorConfig>
        get() = TikTokExtractorConfig::class

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        val internalObserver = createInternalObserver(observer)
        val onSuccess = { webContent: String ->
            SingleObservable.fromCallable(unboundExecutorService) {
                extract(url, webContent)
            }.subscribe(internalObserver)
        }

        if (!fetchRemoteSuccess) {
            SingleObservable.zip(
                SingleObservable.fromCallable(unboundExecutorService) {
                    extractorConfigManager.getRemote(extractorName, TikTokExtractorConfig::class)
                },
                SingleObservable.fromCallable(unboundExecutorService) {
                    extractorConfigManager.getConfig(extractorName, TikTokExtractorConfig::class)
                }
            ) { remote, local ->
                Pair(remote, local)
            }.subscribe(object :
                SingleObserver<Pair<TikTokExtractorConfig, TikTokExtractorConfig>?>(hasDisposable) {
                override fun onSuccess(result: Pair<TikTokExtractorConfig, TikTokExtractorConfig>?) {
                    super.onSuccess(result)
                    result?.let {
                        val remoteConfig = result.first
                        val localConfig = result.second
                        if (remoteConfig.version > localConfig.version) {
                            extractorConfigManager.updateMemCache(extractorName, remoteConfig)
                            extractorConfigManager.updateFileCache(extractorName, remoteConfig)
                        }
                        fetchRemoteSuccess = true
                    }
                }
            })
        }

        val onAfterWaitForRemoteConfig = {
            SingleObservable.fromCallable(unboundExecutorService) {
                extractorConfigManager.getConfig(
                    extractorName,
                    TikTokExtractorConfig::class
                )
            }.subscribe(object : SingleObserver<TikTokExtractorConfig?>(hasDisposable) {
                override fun onSuccess(result: TikTokExtractorConfig?) {
                    super.onSuccess(result)

                    val jsCode = result?.jsCode.orEmpty()
                    val data: ByteArray = Base64.decode(jsCode, Base64.DEFAULT)
                    val jsCodeDecoded = String(data, StandardCharsets.UTF_8)
                    val jsCodeTransformed = jsCodeDecoded.format(url).takeIf { it.isNotEmpty() }
                        ?.let { "$it;" }.orEmpty()

                    val loadWebWaitTime = result?.loadWebWaitTime ?: DEFAULT_LOAD_WEB_WAIT_TIME
                    val parseUrlWaitInterval =
                        result?.parseUrlWaitInterval ?: DEFAULT_PARSE_URL_WAIT_INTERVAL

                    MainActivity.jsInterface.onSuccess = onSuccess

                    val curTime = System.currentTimeMillis()
                    val delayTimeLoadWeb =
                        (loadWebWaitTime - (curTime - (MainActivity.loadWebTime
                            ?: System.currentTimeMillis()))).coerceAtLeast(0)

                    handler.postDelayed({
                        webView.loadUrl("${jsCodeTransformed}javascript:window.HtmlViewer.dummy();")
                        delayParseWebContent(parseUrlWaitInterval)
                    }, delayTimeLoadWeb)
                }

                override fun onError(t: Throwable) {
                    super.onError(t)
                    observer.onError(t)
                }
            })
        }

        if (!fetchRemoteSuccess) {
            handler.postDelayed({ onAfterWaitForRemoteConfig() }, DELAY_LOAD_TIME_REMOTE_CONFIG)
        } else {
            onAfterWaitForRemoteConfig()
        }
    }

    private fun delayParseWebContent(parseUrlWaitInterval: Long) {
        handler.postDelayed({
            webView.loadUrl(
                "javascript:window.HtmlViewer.onLoaded" +
                        "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
            )
        }, parseUrlWaitInterval)
    }

    private fun createInternalObserver(observer: SingleObserver<FilePreviewInfo>): SingleObserver<FilePreviewInfo> {
        return object : SingleObserver<FilePreviewInfo>(hasDisposable) {
            private var curRetryCount = 0

            override fun onSuccess(result: FilePreviewInfo) {
                super.onSuccess(result)
                observer.onSuccess(result)
                finalize()
            }

            override fun onError(t: Throwable) {
                super.onError(t)
                CompletableObservable.fromCallable(unboundExecutorService) {
                    val config = extractorConfigManager.getConfig(
                        extractorName,
                        TikTokExtractorConfig::class
                    )
                    val maxRetryCount =
                        config?.parseUrlFailRetryCount ?: DEFAULT_PARSE_URL_FAIL_RETRY_COUNT
                    val parseUrlWaitInterval =
                        config?.parseUrlWaitInterval ?: DEFAULT_PARSE_URL_WAIT_INTERVAL

                    if (curRetryCount < maxRetryCount) {
                        delayParseWebContent(parseUrlWaitInterval)
                        curRetryCount++
                    } else {
                        throw t
                    }
                }.subscribe(object : CompletableObserver(hasDisposable) {
                    override fun onError(t: Throwable) {
                        super.onError(t)
                        observer.onError(t)
                        finalize()
                    }
                })
            }

            private fun finalize() {
                MainActivity.jsInterface.onSuccess = null
                // cannot call on bridge thread, so post on main
                handler.post { MainActivity.reloadWebView() }
            }
        }
    }

    companion object {
        private const val DEFAULT_LOAD_WEB_WAIT_TIME = 3000L
        private const val DEFAULT_PARSE_URL_WAIT_INTERVAL = 3000L
        private const val DEFAULT_PARSE_URL_FAIL_RETRY_COUNT = 3
        private const val DELAY_LOAD_TIME_REMOTE_CONFIG = 2000L
        const val JS_INTERFACE_NAME = "HtmlViewer"
        const val WEB_URL = "https://snaptik.app/en"

        private var fetchRemoteSuccess = false
    }
}
