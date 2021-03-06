package com.mgt.downloader

import com.google.gson.Gson
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.Utils

object ExtractFieldsManager {
    private val localExtractFieldsCache = HashMap<String, ExtractFields>()
    private val remoteExtractFieldsCache = HashMap<String, ExtractFields>()

    fun getLocalExtractFields(extractorName: String): ExtractFields {
        return localExtractFieldsCache[extractorName]
            ?: getLocalExtractFieldsInternal(extractorName).also {
                localExtractFieldsCache[extractorName] = it
            }
    }

    private fun getLocalExtractFieldsInternal(extractorName: String): ExtractFields {
        val json =
            Utils.getContent(MyApplication.appContext.assets.open("extractfields/$extractorName.json"))
        return Gson().fromJson(json, ExtractFields::class.java)
    }

    fun getRemoteExtractFields(extractorName: String): ExtractFields {
        return remoteExtractFieldsCache[extractorName] ?: getRemoteExtractFieldsInternal(
            extractorName
        ).also {
            remoteExtractFieldsCache[extractorName] = it
        }
    }

    private fun getRemoteExtractFieldsInternal(extractorName: String): ExtractFields {
        val json = Utils.getDontpadContent(Constants.SUBPATH_EXTRACT_FIELDS.format(extractorName))
        return Gson().fromJson(json, ExtractFields::class.java)
    }
}