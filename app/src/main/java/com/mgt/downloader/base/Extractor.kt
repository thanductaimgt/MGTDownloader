package com.mgt.downloader.base

import com.google.gson.Gson
import com.mgt.downloader.MyApplication
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.findValue
import com.mgt.downloader.utils.unescapeHtml

abstract class Extractor(protected val hasDisposable: HasDisposable) {
    abstract fun extract(url: String, observer: SingleObserver<FilePreviewInfo>)

    companion object {
        fun getRemoteExtractFields(extractorName: String): ExtractFields {
            val content = Utils.getContent(Constants.API_EXTRACT_FIELDS.format(extractorName))
            val json = content.findValue("<textarea(.*?)>", "</textarea>", "", false).unescapeHtml()

            return Gson().fromJson(json, ExtractFields::class.java)
        }

        fun getLocalExtractFields(extractorName: String): ExtractFields {
            val json =
                Utils.getContent(MyApplication.appContext.assets.open("extractfields/$extractorName.json"))
            return Gson().fromJson(json, ExtractFields::class.java)
        }
    }
}