package com.mgt.downloader.base

interface ContainsSelectableList {
    fun getSelectableList(): List<*>
    fun selectAllItems()
    fun removeAllItems()
}