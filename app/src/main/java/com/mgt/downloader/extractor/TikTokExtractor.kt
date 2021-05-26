package com.mgt.downloader.extractor

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgt.downloader.ExtractContentManager
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.HttpPostMultipart
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.*

class TikTokExtractor(hasDisposable: HasDisposable) : WebJsExtractor(hasDisposable) {
    override val extractorName = "tiktok"

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            getSnaptikWebContent(url)
        }.subscribe(object : SingleObserver<String>(hasDisposable) {
            override fun onSuccess(result: String) {
                super.onSuccess(result)
                if (ExtractContentManager.isTarget(url, result)) {
                    SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                        extract(url, result)
                    }.subscribe(observer)
                } else {
                    getJsWebContent(WEB_URL) {
                        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
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
        val appendedCookie = Utils.getDontpadContent(Constants.SUBPATH_TIKTOK_APPENDED_COOKIE)
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
        private val API_URL by lazy {
            try {
                getRemoteApiUrl()
            } catch (t: Throwable) {
                "https://snaptik.app/action_2021.php"
            }
        }

        private fun getRemoteApiUrl(): String {
            return Utils.getDontpadContent(Constants.SUBPATH_TIKTOK_API)
        }
    }
}