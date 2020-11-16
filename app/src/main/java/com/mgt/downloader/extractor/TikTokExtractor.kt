package com.mgt.downloader.extractor

import android.util.Log
import android.webkit.CookieManager
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseExtractor
import com.mgt.downloader.base.HttpPostMultipart
import com.mgt.downloader.base.JsWebExtractor
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.Disposable
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.findValue

class TikTokExtractor : JsWebExtractor() {
    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            getSnaptikWebContent(url)
        }.subscribe(object : SingleObserver<String> {
            override fun onSubscribe(disposable: Disposable) {
            }

            override fun onSuccess(result: String) {
                if (!result.contains("download server 01", true)) {
                    getJsWebContent(SNAPTIK_URL) {
                        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                            val webContent = getSnaptikWebContent(url)
                            extract(url, webContent)
                        }.subscribe(observer)
                    }
                } else {
                    SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
                        extract(url, result)
                    }.subscribe(observer)
                }
            }

            override fun onError(t: Throwable) {
                super.onError(t)
                observer.onError(t)
            }
        })
    }

    override fun extract(url: String, webContent: String): FilePreviewInfo {
        return try {
            getFilePreviewInfo(url, webContent, remoteExtractFields)
        } catch (t: Throwable) {
            Log.e(TAG, "parse remote fields fail")
            Log.e(TAG, "webContent: $webContent")
            t.printStackTrace()
            getFilePreviewInfo(url, webContent, localExtractFields)
        }
    }

    private fun getSnaptikWebContent(url: String): String {
        val headers = hashMapOf(
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.88 Safari/537.36",
            "cookie" to CookieManager.getInstance().getCookie(SNAPTIK_URL)
        )
        return with(HttpPostMultipart("https://snaptik.app/action.php", "utf-8", headers)) {
            addFormField("url", url)
            finish()
        }
    }

    private fun getFilePreviewInfo(
        url: String,
        webContent: String,
        extractFields: ExtractFields
    ): FilePreviewInfo {
        val fileNameE: String
        val thumbUrlE: String?
        val downloadUrlE: String
        val widthE: Int
        val heightE: Int

        extractFields.apply {
            fileNameE =
                "${webContent.findValue(title.prefix, title.postfix, title.default)}.mp4"
            thumbUrlE =
                webContent.findValue(thumbUrl.prefix, thumbUrl.postfix, thumbUrl.default)
            downloadUrlE = webContent.findValue(
                downloadUrl.prefix,
                downloadUrl.postfix,
                downloadUrl.default
            )!!

            widthE =
                webContent.findValue(width.prefix, width.postfix, width.default)?.toInt() ?: 1
            heightE =
                webContent.findValue(height.prefix, height.postfix, height.default)?.toInt()
                    ?: 1
        }

        val fileSize = Utils.getFileSize(downloadUrlE)
        val isMultipartSupported = Utils.isMultipartSupported(downloadUrlE)

        return FilePreviewInfo(
            fileNameE,
            url,
            downloadUrlE,
            fileSize,
            -1,
            -1,
            thumbUri = thumbUrlE,
            thumbRatio = Utils.getFormatRatio(
                widthE,
                heightE
            ),
            isMultipartSupported = isMultipartSupported
        )
    }

    companion object {
        private const val EXTRACTOR_NAME = "tiktok"
        private const val SNAPTIK_URL = "https://snaptik.app/vn"

        val localExtractFields: ExtractFields by lazy {
            BaseExtractor.getLocalExtractFields(
                EXTRACTOR_NAME
            )
        }
        val remoteExtractFields: ExtractFields by lazy {
            BaseExtractor.getRemoteExtractFields(
                EXTRACTOR_NAME
            )
        }
    }
}