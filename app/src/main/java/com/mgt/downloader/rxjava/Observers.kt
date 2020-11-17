package com.mgt.downloader.rxjava

import androidx.annotation.CallSuper
import com.mgt.downloader.base.HasDisposable

abstract class Observer(protected val hasDisposable: HasDisposable) {
    protected lateinit var disposable: Disposable

    @CallSuper
    open fun onSubscribe(disposable: Disposable) {
        this.disposable = disposable
        hasDisposable.compositeDisposable.add(disposable)
    }

    @CallSuper
    open fun onError(t: Throwable) {
        t.printStackTrace()
        hasDisposable.compositeDisposable.remove(disposable)
    }
}

abstract class StreamObserver<T>(hasDisposable: HasDisposable) : Observer(hasDisposable) {
    abstract fun onNext(item: T?)

    @CallSuper
    open fun onComplete() {
        hasDisposable.compositeDisposable.remove(disposable)
    }
}

abstract class CompletableObserver(hasDisposable: HasDisposable) : Observer(hasDisposable) {
    @CallSuper
    open fun onComplete() {
        hasDisposable.compositeDisposable.remove(disposable)
    }
}

abstract class SingleObserver<T>(hasDisposable: HasDisposable) : Observer(hasDisposable) {
    @CallSuper
    open fun onSuccess(result: T) {
        hasDisposable.compositeDisposable.remove(disposable)
    }
}