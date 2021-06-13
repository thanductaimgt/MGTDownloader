package com.mgt.downloader.extractor

import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.MyJavaScriptInterface
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.Utils

class TikTokExtractorV2(hasDisposable: HasDisposable) : WebJsExtractor(hasDisposable) {
    override val extractorName = "tiktok"

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        val onSuccess = { webContent: String ->
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                extract(url, webContent)
            }.subscribe(observer)
        }

        webView
            .apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                addJavascriptInterface(
                    MyJavaScriptInterface(onSuccess),
                    "HtmlViewer"
                )

                webViewClient = this@TikTokExtractorV2.webViewClient
                loadUrl(WEB_URL)
            }
        handler.postDelayed({
            webView.loadUrl("$JS_CODE;javascript:window.HtmlViewer.dummy();")
            handler.postDelayed({
                webView.loadUrl(
                    "javascript:window.HtmlViewer.onLoaded" +
                            "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                )
            }, 2000)
        }, 2000)
    }

    override val webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            urlNewString: String?
        ): Boolean {
            return false
        }
    }

    companion object {
        private const val WEB_URL = "https://snaptik.app/vn"
        private val JS_CODE by lazy {
            try {
                getRemoteJsCodeUrl()
            } catch (t: Throwable) {
                "javascript:document.getElementById('url').value = 'https://vt.tiktok.com/ZSJxoRayP/';javascript:document.getElementById('submiturl').click()"
            }
        }

        private fun getRemoteJsCodeUrl(): String {
            return Utils.getDontpadContent(Constants.SUBPATH_TIKTOKV2_JS_CODE)
        }
    }
}