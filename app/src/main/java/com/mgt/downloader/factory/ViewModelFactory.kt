package com.mgt.downloader.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.ui.view_file.ViewFileViewModel

class ViewModelFactory private constructor() : ViewModelProvider.Factory {
    private lateinit var zipPreviewInfo: FilePreviewInfo

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            ViewFileViewModel::class.java -> ViewFileViewModel(
                zipPreviewInfo
            ) as T
            else -> modelClass.newInstance()
        }
    }

    companion object {
        private var instance: ViewModelFactory? = null

        fun getInstance(zipPreviewInfo: FilePreviewInfo?=null): ViewModelFactory {
            if (instance == null) {
                synchronized(ViewModelFactory) {
                    if (instance == null) {
                        instance = ViewModelFactory()
                    }
                }
            }

            zipPreviewInfo?.let { instance!!.zipPreviewInfo =it }
            return instance!!
        }
    }
}