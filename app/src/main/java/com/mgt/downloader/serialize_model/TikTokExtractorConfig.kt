package com.mgt.downloader.serialize_model

class TikTokExtractorConfig(
    version: Int,
    extractFields: ExtractFields?,
    val jsCode: String,
    val loadWebWaitTime: Long? = null,// ms
    val parseUrlWaitInterval: Long? = null,
    val parseUrlFailRetryCount: Int? = null,
) : ExtractorConfig(version, extractFields)
