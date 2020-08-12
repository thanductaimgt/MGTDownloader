package com.mgt.downloader.data_model

data class FilePreviewInfo(
    var name: String,
    var displayUri: String,
    var downloadUri: String,
    var size: Long = 0,
    var centralDirOffset: Int = -1,
    var centralDirSize: Int = -1,
    var isLocalFile: Boolean = false,
    var thumbUri: String? = null,
    var thumbRatio: String = "1:1"
)