package com.mgt.downloader.rxjava

import android.os.Handler
import android.os.Looper

abstract class Emitter {
    abstract val observers : HashSet<Observer>

    fun addObserver(observer: Observer){
        observers.add(observer)
    }

    fun removeObserver(observer: Observer){
        observers.remove(observer)
    }

    fun onError(throwable: Throwable) {
            Handler(Looper.getMainLooper()).post {
                observers.forEach {
                    it.onError(throwable)
                }
            }
    }
}

class StreamEmitter<T>:Emitter(){
    override val observers = HashSet<Observer>()

    fun onNext(item: T?) {
        Handler(Looper.getMainLooper()).post {
            (observers as HashSet<StreamObserver<T>>).forEach {
                it.onNext(item)
            }
        }
    }

    fun onComplete() {
        Handler(Looper.getMainLooper()).post {
            (observers as HashSet<StreamObserver<T>>).forEach {
                it.onComplete()
            }
        }
    }
}

class SingleEmitter<T>:Emitter(){
    override val observers = HashSet<Observer>()

    fun onSuccess(item: T) {
        Handler(Looper.getMainLooper()).post {
            (observers as HashSet<SingleObserver<T>>).forEach {
                it.onSuccess(item)
            }
        }
    }
}

class CompletableEmitter:Emitter(){
    override val observers = HashSet<Observer>()

    fun onComplete() {
        Handler(Looper.getMainLooper()).post {
            (observers as HashSet<CompletableObserver>).forEach {
                it.onComplete()
            }
        }
    }
}