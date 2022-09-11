package com.mgt.downloader.extractor.tiktok

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.HttpPostMultipart
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.di.DI.extractContentManager
import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.logD
import com.mgt.downloader.utils.logE
import kotlin.reflect.KClass

class TikTokExtractor(hasDisposable: HasDisposable) :
    WebJsExtractor<ExtractorConfig>(hasDisposable) {
    override val extractorName = "tiktok"
    override val extractorConfigClass: KClass<ExtractorConfig>
        get() = ExtractorConfig::class

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(unboundExecutorService) {
            getSnaptikWebContent(url)
        }.subscribe(object : SingleObserver<String>(hasDisposable) {
            override fun onSuccess(result: String) {
                super.onSuccess(result)
                if (extractContentManager.isTarget(url, result)) {
                    SingleObservable.fromCallable(unboundExecutorService) {
                        extract(url, result)
                    }.subscribe(observer)
                } else {
                    getJsWebContent(WEB_URL) {
                        SingleObservable.fromCallable(unboundExecutorService) {
                            val webContent = getSnaptikWebContent(url)
                            extract(url, webContent)
                        }.subscribe(observer)
                    }
                }
            }

            override fun onError(t: Throwable) {
                super.onError(t)
                observer.onError(t)
            }
        })
    }

    private fun getSnaptikWebContent(url: String): String {
        val snaptikCookie = CookieManager.getInstance().getCookie(WEB_URL)
        val appendedCookie = ";ref=google;"
        val cookie = "$snaptikCookie$appendedCookie"

        val headers = hashMapOf(
            "User-Agent" to Constants.USER_AGENT,
            "cookie" to cookie
        )
        return with(HttpPostMultipart(API_URL, "utf-8", headers)) {
            addFormField("url", url)
            finish()
        }.also { logD(TAG, "getSnaptikWebContent, url: $url") }
    }

    override val webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            urlNewString: String?
        ): Boolean {
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            view?.loadUrl(
                "javascript:window.HtmlViewer.onLoaded" +
                        "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
            ) ?: logE(TAG, "webView is null !!!")
        }
    }

    companion object {
        private const val WEB_URL = "https://snaptik.app/vn"
        private val API_URL = "https://snaptik.app/abc.php"
    }
}