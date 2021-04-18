package com.mgt.downloader.rxjava

class CompositeDisposable {
    private val disposables = HashSet<Disposable>()

    fun add(disposable: Disposable) {
        disposables.add(disposable)
    }

    fun remove(disposable: Disposable) {
        disposables.remove(disposable)
    }

    fun clear() {
        disposables.forEach {
            it.dispose()
        }
        disposables.clear()
    }
}