package com.mgt.downloader.ui.view_file

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import com.mgt.downloader.MyApplication
import com.mgt.downloader.base.BaseViewModel
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.data_model.NullZipNode
import com.mgt.downloader.data_model.NullableZipNode
import com.mgt.downloader.data_model.ZipNode
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver


@SuppressLint("CheckResult")
class ViewFileViewModel(filePreviewInfo: FilePreviewInfo) : BaseViewModel() {
    val liveRootNode = MutableLiveData<NullableZipNode>()

    init {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            ZipNode.getZipTree(filePreviewInfo)
        }.subscribe(BuildZipTreeObserver())
    }

    inner class BuildZipTreeObserver : SingleObserver<ZipNode>(this) {
        override fun onSuccess(result: ZipNode) {
            super.onSuccess(result)
            liveRootNode.value = result
        }

        override fun onError(t: Throwable) {
            super.onError(t)
            liveRootNode.value = NullZipNode()
        }
    }
}