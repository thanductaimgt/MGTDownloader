package com.mgt.downloader.nonserialize_model

data class MyError(
    val code: Int,
    val throwable: Throwable,
    val message: String? = null
)