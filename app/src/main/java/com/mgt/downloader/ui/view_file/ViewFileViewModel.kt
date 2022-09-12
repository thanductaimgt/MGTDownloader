package com.mgt.downloader.ui.view_file

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import com.mgt.downloader.base.BaseViewModel
import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.nonserialize_model.NullZipNode
import com.mgt.downloader.nonserialize_model.NullableZipNode
import com.mgt.downloader.nonserialize_model.ZipNode
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver


@SuppressLint("CheckResult")
class ViewFileViewModel : BaseViewModel() {
    val liveRootNode = MutableLiveData<NullableZipNode>()
    private val buildZipTreeObserver = BuildZipTreeObserver()

    fun buildZipTree(filePreviewInfo: FilePreviewInfo) {
        SingleObservable.fromCallable(unboundExecutorService) {
            ZipNode.getZipTree(filePreviewInfo)
        }.subscribe(buildZipTreeObserver)
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