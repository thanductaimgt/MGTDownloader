package com.mgt.downloader.rxjava

class Disposable(private val observer: Observer, private val emitter: Emitter) {
    private var isDisposed: Boolean = false

    fun isDisposed(): Boolean {
        return isDisposed
    }

    fun dispose() {
        if(!isDisposed){
            emitter.removeObserver(observer)
            isDisposed = true
        }
    }
}