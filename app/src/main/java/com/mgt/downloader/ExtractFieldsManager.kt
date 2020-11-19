package com.mgt.downloader

import com.google.gson.Gson
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.findValue
import com.mgt.downloader.utils.unescapeHtml

object ExtractFieldsManager {
    private val localExtractFieldsCache = HashMap<String, ExtractFields>()
    private val remoteExtractFieldsCache = HashMap<String, ExtractFields>()

    fun getLocalExtractFields(extractorName:String):ExtractFields{
        return localExtractFieldsCache[extractorName]?: getLocalExtractFieldsInternal(extractorName).also {
            localExtractFieldsCache[extractorName] = it
        }
    }

    private fun getLocalExtractFieldsInternal(extractorName: String): ExtractFields {
        val json =
            Utils.getContent(MyApplication.appContext.assets.open("extractfields/$extractorName.json"))
        return Gson().fromJson(json, ExtractFields::class.java)
    }

    fun getRemoteExtractFields(extractorName:String):ExtractFields{
        return remoteExtractFieldsCache[extractorName]?: getRemoteExtractFieldsInternal(extractorName).also {
            remoteExtractFieldsCache[extractorName] = it
        }
    }

    private fun getRemoteExtractFieldsInternal(extractorName: String): ExtractFields {
        val content = Utils.getContent(Constants.API_EXTRACT_FIELDS.format(extractorName))
        val json = content.findValue("<textarea(.*?)>", "</textarea>", "", false).unescapeHtml()

        return Gson().fromJson(json, ExtractFields::class.java)
    }
}