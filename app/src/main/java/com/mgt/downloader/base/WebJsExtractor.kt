package com.mgt.downloader.base

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgt.downloader.MyApplication
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver


abstract class WebJsExtractor(hasDisposable: HasDisposable) : Extractor(hasDisposable) {
    open val waitTime = 5000L

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        getJsWebContent(url) { webContent ->
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                extract(url, webContent)
            }.subscribe(observer)
        }
    }

    abstract fun extract(url: String, webContent: String): FilePreviewInfo

    protected fun getJsWebContent(url: String, onSuccess: ((html: String) -> Any)?) {
        WebView(MyApplication.appContext)
            .apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                addJavascriptInterface(MyJavaScriptInterface(onSuccess), "HtmlViewer")

                webViewClient = object : WebViewClient() {
                    private var loadingFinished = true
                    private var redirect = false
                    private val handler = Handler(Looper.getMainLooper())

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
                                loadUrl(
                                    "javascript:window.HtmlViewer.onLoaded" +
                                            "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                                )
                            }, waitTime)
                        } else {
                            redirect = false
                        }
                    }
                }
                loadUrl(url)
            }
    }

    private class MyJavaScriptInterface(private val onSuccess: ((html: String) -> Any)?) {
        private var isFirstLoad = true
        private val handler = Handler(Looper.getMainLooper())
        private lateinit var runnable: Runnable

        @JavascriptInterface
        fun onLoaded(html: String?) {
            if (isFirstLoad) {
                isFirstLoad = false
                runnable = Runnable { html?.let { onSuccess?.invoke(it) } }
                handler.postDelayed(runnable, 2000)
                return
            }
            handler.removeCallbacks(runnable)
            html?.let {
                onSuccess?.invoke(it)
            }
        }
    }
}