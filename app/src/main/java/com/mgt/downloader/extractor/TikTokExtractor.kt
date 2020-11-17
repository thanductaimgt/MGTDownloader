package com.mgt.downloader.extractor

import android.webkit.CookieManager
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.HttpPostMultipart
import com.mgt.downloader.base.WebJsExtractor
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.*

class TikTokExtractor(hasDisposable: HasDisposable) : WebJsExtractor(hasDisposable) {
    override fun extract(url: String, observer: SingleObserver<FilePreviewInfo>) {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            getSnaptikWebContent(url)
        }.subscribe(object : SingleObserver<String>(hasDisposable) {
            override fun onSuccess(result: String) {
                super.onSuccess(result)
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
            logD(TAG, remoteExtractFields.toString())
            getFilePreviewInfo(url, webContent, remoteExtractFields)
        } catch (t: Throwable) {
            logE(TAG, "parse remote fields fail")
            logE(TAG, "webContent: $webContent")
            t.printStackTrace()
            getFilePreviewInfo(url, webContent, localExtractFields)
        }
    }

    private fun getSnaptikWebContent(url: String): String {
        val headers = hashMapOf(
            "User-Agent" to Constants.USER_AGENT,
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
        extractFields.apply {
            val fileName =
                "${webContent.findValue(title.prefix, title.postfix, title.default)}.mp4"
            val thumbUrl =
                webContent.findValue(thumbUrl.prefix, thumbUrl.postfix, thumbUrl.default)
            val downloadUrl = webContent.findValue(
                downloadUrl.prefix,
                downloadUrl.postfix,
                downloadUrl.default
            )!!

            val width =
                webContent.findValue(width.prefix, width.postfix, width.default)?.toInt() ?: 1
            val height =
                webContent.findValue(height.prefix, height.postfix, height.default)?.toInt()
                    ?: 1

            val fileSize = Utils.getFileSize(downloadUrl)
            val isMultipartSupported = Utils.isMultipartSupported(downloadUrl)

            return FilePreviewInfo(
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
                ),
                isMultipartSupported = isMultipartSupported
            )
        }
    }

    companion object {
        private const val EXTRACTOR_NAME = "tiktok"
        private const val SNAPTIK_URL = "https://snaptik.app/vn"

        val localExtractFields: ExtractFields by lazy {
            getLocalExtractFields(
                EXTRACTOR_NAME
            )
        }
        val remoteExtractFields: ExtractFields by lazy {
            getRemoteExtractFields(
                EXTRACTOR_NAME
            )
        }
    }
}