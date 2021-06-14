package com.mgt.downloader.extractor

import androidx.annotation.WorkerThread
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.ui.MainActivity
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.Utils

class TikTokExtractorV2(hasDisposable: HasDisposable) : WebJsExtractor(hasDisposable) {
    override val extractorName = "tiktok"

    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        val onSuccess = { webContent: String ->
            SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                extract(url, webContent)
            }.subscribe(observer)
            MainActivity.jsInterface.onSuccess = null
            // cannot call on bridge thread, so post on main
            handler.post { MainActivity.reloadWebView() }
        }

        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            getJsCode().format(url)
        }.subscribe(object : SingleObserver<String>(hasDisposable) {
            override fun onSuccess(result: String) {
                super.onSuccess(result)

                MainActivity.jsInterface.onSuccess = onSuccess

                val curTime = System.currentTimeMillis()
                val delayTimeLoadWeb = (DELAY_LOAD_TIME_WEB - (curTime - (MainActivity.loadWebTime
                    ?: System.currentTimeMillis()))).coerceAtLeast(0)

                handler.postDelayed({
                    webView.loadUrl("$result;javascript:window.HtmlViewer.dummy();")
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

    @WorkerThread
    private fun getJsCode() =
        try {
            getRemoteJsCodeUrl().replace("java:", "javascript:")
        } catch (t: Throwable) {
            "javascript:document.getElementById('url').value = '%s';javascript:document.getElementById('submiturl').click()"
        }

    private fun getRemoteJsCodeUrl(): String {
        return Utils.getDontpadContent(Constants.SUBPATH_TIKTOKV2_JS_CODE)
    }

    companion object {
        private const val DELAY_LOAD_TIME_WEB = 3000L
        private const val DELAY_LOAD_TIME_URL = 3000L
        const val JS_INTERFACE_NAME = "HtmlViewer"
        const val WEB_URL = "https://snaptik.app/vn"
    }
}
