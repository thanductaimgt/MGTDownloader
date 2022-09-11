package com.mgt.downloader.serialize_model

data class ExtractFieldMeta(
    val prefix: String? = null,
    val postfix: String? = null,
    val default: String? = null,
    val target: String? = null,
)