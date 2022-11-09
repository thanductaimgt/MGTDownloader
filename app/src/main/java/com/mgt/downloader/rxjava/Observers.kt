package com.mgt.downloader.rxjava

import androidx.annotation.CallSuper
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.utils.recordNonFatalException

abstract class Observer(protected val hasDisposable: HasDisposable) {
    private lateinit var disposable: Disposable

    @CallSuper
    open fun onSubscribe(disposable: Disposable) {
        this.disposable = disposable
        hasDisposable.compositeDisposable.add(disposable)
    }

    @CallSuper
    open fun onError(t: Throwable) {
        recordNonFatalException(t)
        removeDisposable()
    }

    protected fun removeDisposable() {
        if (this::disposable.isInitialized) {
            hasDisposable.compositeDisposable.remove(disposable)
        }
    }
}

abstract class StreamObserver<T>(hasDisposable: HasDisposable) : Observer(hasDisposable) {
    abstract fun onNext(item: T?)

    @CallSuper
    open fun onComplete() {
        removeDisposable()
    }
}

open class CompletableObserver(hasDisposable: HasDisposable) : Observer(hasDisposable) {
    @CallSuper
    open fun onComplete() {
        removeDisposable()
    }
}

abstract class SingleObserver<T>(hasDisposable: HasDisposable) : Observer(hasDisposable) {
    @CallSuper
    open fun onSuccess(result: T) {
        removeDisposable()
    }
}