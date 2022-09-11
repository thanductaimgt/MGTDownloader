package com.mgt.downloader.nonserialize_model

import android.os.Bundle

data class Event(
    val type: Int,
    val data: Bundle? = null
)