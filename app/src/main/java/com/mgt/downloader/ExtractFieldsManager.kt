package com.mgt.downloader

import com.google.gson.Gson
import com.mgt.downloader.data_model.ExtractFields
import com.mgt.downloader.utils.*
import kotlin.text.format

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
        val json = Prefs.get().getString(Prefs.KEY_EXTRACT_FIELDS.format(extractorName), null)
        return if (json == null) {
            ExtractFields::class.fromJson(
                Utils.getContent(MyApplication.appContext.assets.open("extractfields/$extractorName.json"))
            ).also {
                updateLocalExtractFields(extractorName, it)
            }
        } else {
            ExtractFields::class.fromJson(json)
        }
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

    fun updateLocalExtractFields(extractorName: String, extractFields: ExtractFields) {
        Prefs.edit {
            putString(
                Prefs.KEY_EXTRACT_FIELDS.format(extractorName),
                extractFields.toJson()
            )
        }
        localExtractFieldsCache[extractorName] = extractFields
    }
}
