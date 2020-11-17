package com.mgt.downloader.rxjava

class Disposable(private val observer: Observer, private val emitter: Emitter) {
    var isDisposed: Boolean = false

    fun dispose() {
        if(!isDisposed){
            emitter.removeObserver(observer)
            isDisposed = true
        }
    }
}