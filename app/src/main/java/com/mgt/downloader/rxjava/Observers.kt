package com.mgt.downloader.rxjava

interface Observer {
    fun onSubscribe(disposable: Disposable)
    fun onError(throwable: Throwable)
}

interface StreamObserver<T>:Observer{
    fun onNext(item:T?)
    fun onComplete()
}

interface CompletableObserver:Observer {
    fun onComplete()
}

interface SingleObserver<T>:Observer {
    fun onSuccess(result:T)
}