package com.mgt.downloader.utils

import android.Manifest

object Constants {
    const val MESSAGE = "message"
    const val OPEN_DOWNLOAD_LIST = "OPEN_DOWNLOAD_LIST"
    const val START_FOREGROUND_SERVICE = "START_FOREGROUND_SERVICE"

    const val SEVEN_DAYS_IN_MILLISECOND = 7 * 24 * 3600 * 1000
    const val ONE_DAY_IN_MILLISECOND = 24 * 3600 * 1000
    const val ONE_HOUR_IN_MILLISECOND = 3600 * 1000
    const val ONE_MIN_IN_MILLISECOND = 60 * 1000
    const val ONE_SECOND_IN_MILLISECOND = 1000

    const val FILE_SIZE_DETAIL_LEVEL_LOW = 1
    const val FILE_SIZE_DETAIL_LEVEL_MEDIUM = 2

    const val MAX_LINES_ITEM_EXPANDED = 10
    const val MAX_LINES_ITEM_COLLAPSED = 1

    const val MENU_ITEM_DELETE_FROM_LIST = 0
    const val MENU_ITEM_DELETE_FROM_STORAGE = 1
    const val MENU_ITEM_DELETE_FROM_BOTH = 2

    const val FILE_PROVIDER_AUTH = "fileprovider"

    const val ERROR = -2

    const val MAX_EOCD_AND_COMMENT_SIZE = 0xFFFF + 22
    const val RELATIVE_OFFSET_LOCAL_HEADER:Short = 0x1447
    const val LOCAL_FILE_HEADER:Int = 0x04034b50

    // Storage Permissions
    const val REQUEST_PERMISSIONS = 1
    val PERMISSIONS_ID = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    const val FOREGROUND_ID = 1447
    const val CHANNEL_ID = "1447"
    const val CHANNEL_NAME = "Download Service"

    const val HTTP_PARTIAL_CONTENT = 206
    const val HTTP_RANGE_NOT_SATISFIABLE = 416

    const val SHARE_PREFERENCES_NAME = "IDM Share Preferences"

    const val ONE_KB_IN_B = 1000L
    const val ONE_MB_IN_B = 1000L*1000
    const val ONE_GB_IN_B = 1000L*1000*1000

    const val DOWNLOAD_STATE_SUCCESS = 0
    const val DOWNLOAD_STATE_CANCEL_OR_FAIL = 1
    const val DOWNLOAD_STATE_INTERRUPT = 2

    private const val API_BASE_URL = "http://dontpad.com/tdtai/mgtdownloader"
    const val API_GENERAL_HEADERS = "$API_BASE_URL/requestheaders"
    const val API_EXTRACT_FIELDS = "$API_BASE_URL/extractfields/%s"
}