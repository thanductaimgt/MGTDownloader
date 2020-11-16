package com.mgt.downloader.data_model

data class ExtractFields(
    val title: ExtractFieldMeta,
    val thumbUrl: ExtractFieldMeta,
    val downloadUrl: ExtractFieldMeta,
    val width: ExtractFieldMeta,
    val height: ExtractFieldMeta,
)