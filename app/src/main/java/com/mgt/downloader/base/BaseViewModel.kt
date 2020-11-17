package com.mgt.downloader.base

import androidx.lifecycle.ViewModel
import com.mgt.downloader.rxjava.CompositeDisposable

abstract class BaseViewModel :ViewModel(), HasDisposable{
    override val compositeDisposable = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}