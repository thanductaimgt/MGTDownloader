package com.mgt.downloader.helper

import com.mgt.downloader.utils.Utils

class LongObject(value:Long) {
    var value: Long = value
        set(value) {
            isFormatSizeChange = Utils.getFormatFileSize(
                field
            ) != Utils.getFormatFileSize(value)
            field = value
        }
    var isFormatSizeChange: Boolean = true
}