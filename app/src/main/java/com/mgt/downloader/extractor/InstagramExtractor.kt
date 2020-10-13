package com.mgt.downloader.extractor

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Utils
import org.apache.commons.lang.StringEscapeUtils


class InstagramExtractor : BaseExtractor {
    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        getJsWebContent(url) { html ->
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                var data = html
                var fileName =
                    data.substring("<title>".let{data.indexOf(it) + it.length}).let {
                        it.substring(0, it.indexOf("</title>"))
                    }

                var isVideo = true
                var idx = data.indexOf("<video")
                if (idx == -1) {
                    idx = data.indexOf("<img")
                    isVideo = false
                }

                data = data.substring(idx)

                //Grab content URL (video file)
                val downloadUrl: String
                val thumbUrl: String?

                val width = 473
                val height = 840

                if (isVideo) {
                    thumbUrl = data.substring("poster=\"".let{data.indexOf(it) + it.length}).let {
                        StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
                    }

                    downloadUrl = data.substring("src=\"".let{data.indexOf(it) + it.length}).let {
                        StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
                    }

                    fileName = "$fileName.mp4"
                } else {
                    downloadUrl = data.substring("src=\"".let{data.indexOf(it) + it.length}).let {
                        StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf("\"")))
                    }
                    thumbUrl = downloadUrl

                    fileName = "$fileName.jpg"
                }

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

    private fun getJsWebContent(url: String, onSuccess: ((html: String) -> Any)?) {
        WebView(MyApplication.appContext).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(MyJavaScriptInterface(onSuccess), "HtmlViewer")

            webViewClient = object : WebViewClient() {
                private val handler = Handler(Looper.getMainLooper())

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    urlNewString: String?
                ): Boolean {
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    handler.postDelayed({
                        loadUrl(
                            "javascript:window.HtmlViewer.onLoaded" +
                                    "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                        )
                    }, 3000)
                }
            }
            loadUrl(url)
        }
    }

    internal class MyJavaScriptInterface(private val onSuccess: ((html: String) -> Any)?) {
        @JavascriptInterface
        fun onLoaded(html: String?) {
            html?.let { onSuccess?.invoke(it) }
        }
    }
}