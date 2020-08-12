package com.mgt.downloader.rxjava

class CompositeDisposable {
    private val disposables = ArrayList<Disposable>()

    fun add(disposable:Disposable){
        disposables.add(disposable)
    }

    fun clear(){
        disposables.forEach {
            it.dispose()
        }
    }
}