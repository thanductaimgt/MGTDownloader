package com.mgt.downloader.base

import androidx.lifecycle.ViewModel
import com.mgt.downloader.nonserialize_model.Event
import com.mgt.downloader.nonserialize_model.MyError
import com.mgt.downloader.rxjava.CompositeDisposable
import com.mgt.livedata.LiveEvent

abstract class BaseViewModel : ViewModel(), HasDisposable {
    val liveError = LiveEvent<MyError>()
    val liveEvent = LiveEvent<Event>()
    override val compositeDisposable = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}