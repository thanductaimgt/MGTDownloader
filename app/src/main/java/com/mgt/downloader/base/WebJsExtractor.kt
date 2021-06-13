package com.mgt.downloader.base

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgt.downloader.MyApplication
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.ui.MainActivity


abstract class WebJsExtractor(hasDisposable: HasDisposable) : Extractor(hasDisposable) {
    open val waitTime = 5000L
    protected val webView
        get() = MainActivity.webView
    protected val handler = Handler(Looper.getMainLooper())

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        getJsWebContent(url) { webContent ->
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                extract(url, webContent)
            }.subscribe(observer)
        }
    }

    protected fun getJsWebContent(url: String, onSuccess: ((html: String) -> Any)?) {
        MainActivity.webView
//        WebView(MyApplication.appContext)
            .apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                addJavascriptInterface(MyJavaScriptInterface(onSuccess), "HtmlViewer")

                webViewClient = this@WebJsExtractor.webViewClient
                loadUrl(url)
            }
    }

    open val webViewClient = object : WebViewClient() {
        private var loadingFinished = true
        private var redirect = false

        override fun shouldOverrideUrlLoading(
            view: WebView,
            urlNewString: String?
        ): Boolean {
            if (!loadingFinished) {
                redirect = true
            }
            loadingFinished = false
            view.loadUrl(urlNewString!!)
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
            loadingFinished = false
            //SHOW LOADING IF IT ISNT ALREADY VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (!redirect) {
                loadingFinished = true
            }
            if (loadingFinished && !redirect) {
//                        //HIDE LOADING IT HAS FINISHED
                handler.postDelayed({
                    view?.loadUrl(
                        "javascript:window.HtmlViewer.onLoaded" +
                                "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                    )
                }, waitTime)
            } else {
                redirect = false
            }
        }
    }
}