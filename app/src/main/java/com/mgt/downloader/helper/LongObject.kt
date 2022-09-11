package com.mgt.downloader.helper

import com.mgt.downloader.di.DI.utils

class LongObject(value: Long) {
    var value: Long = value
        set(value) {
            isFormatSizeChange = utils.getFormatFileSize(
                field
            ) != utils.getFormatFileSize(value)
            field = value
        }
    var isFormatSizeChange: Boolean = true
}