package com.mgt.downloader.data_model

data class ExtractFieldMeta(
    val prefix: String? = null,
    val postfix: String? = null,
    val default: String? = null,
    val target: String? = null,
)