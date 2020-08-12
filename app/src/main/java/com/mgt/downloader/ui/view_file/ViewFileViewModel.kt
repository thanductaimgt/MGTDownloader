package com.mgt.downloader.ui.view_file

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mgt.downloader.MyApplication
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.data_model.NullZipNode
import com.mgt.downloader.data_model.NullableZipNode
import com.mgt.downloader.data_model.ZipNode
import com.mgt.downloader.rxjava.CompositeDisposable
import com.mgt.downloader.rxjava.Disposable
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver


@SuppressLint("CheckResult")
class ViewFileViewModel(filePreviewInfo: FilePreviewInfo) : ViewModel() {
    val liveRootNode = MutableLiveData<NullableZipNode>()
    private val compositeDisposable = CompositeDisposable()

    init {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            ZipNode.getZipTree(filePreviewInfo)
        }.subscribe(BuildZipTreeObserver())
    }

    inner class BuildZipTreeObserver : SingleObserver<ZipNode> {
        override fun onSuccess(result: ZipNode) {
            liveRootNode.value = result
        }

        override fun onSubscribe(disposable: Disposable) {
            compositeDisposable.add(disposable)
        }

        override fun onError(t: Throwable) {
            liveRootNode.value = NullZipNode()
            t.printStackTrace()
        }
    }

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}