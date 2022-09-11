package com.mgt.downloader.serialize_model

open class ExtractorConfig(
    open val extractFields: ExtractFields,
)

data class ExtractFields(
    val title: ExtractFieldMeta,
    val thumbUrl: ExtractFieldMeta,
    val downloadUrl: ExtractFieldMeta,
    val width: ExtractFieldMeta,
    val height: ExtractFieldMeta,
)