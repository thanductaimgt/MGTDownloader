package com.mgt.downloader.rxjava

import android.os.Handler
import android.os.Looper
import com.mgt.downloader.base.HasDisposable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

abstract class Observable {
    abstract val emitter: Emitter
    var future: Future<*>? = null
    val childObservables: ArrayList<Observable> = ArrayList()

    fun subscribe(observer: Observer): Disposable {
        emitter.addObserver(observer)
        val disposable = Disposable(observer, emitter)

        Handler(Looper.getMainLooper()).post {
            observer.onSubscribe(disposable)
        }

        return disposable
    }

    fun cancel() {
        childObservables.forEach { it.cancel() }
        future?.cancel(true)
    }
}

class StreamObservable<T> private constructor() : Observable() {
    override val emitter = StreamEmitter<T>()

    companion object {
        fun <T> create(
            executorService: ExecutorService,
            callable: (emitter: StreamEmitter<T>) -> Any?
        ): StreamObservable<T> {
            val newObservable = StreamObservable<T>()
            newObservable.future = executorService.submit {
                try {
                    callable(newObservable.emitter)
                } catch (t: Throwable) {
                    newObservable.emitter.onError(t)
                }
            }
            return newObservable
        }
    }
}

class SingleObservable<T> private constructor() : Observable() {
    override val emitter = SingleEmitter<T>()

    companion object {
        fun <T> fromCallable(
            executorService: ExecutorService,
            callable: () -> T
        ): SingleObservable<T> {
            val newObservable = SingleObservable<T>()
            newObservable.future = executorService.submit {
                try {
                    val res = callable()
                    newObservable.emitter.onSuccess(res)
                } catch (t: Throwable) {
                    newObservable.emitter.onError(t)
                }
            }
            return newObservable
        }

        fun <T1, T2, T> zip(
            singleObservable1: SingleObservable<T1>,
            singleObservable2: SingleObservable<T2>,
            biFunction: (result1: T1, result2: T2) -> T
        ): SingleObservable<T> {
            val zipSingleObservable = SingleObservable<T>()

            val compositeDisposable = CompositeDisposable()

            var result1: T1? = null
            var result2: T2? = null

            class ChildSingleObserver(private val isFirstOfPair: Boolean) :
                SingleObserver<Any>(object : HasDisposable {
                    override val compositeDisposable = compositeDisposable
                }) {
                @Suppress("UNCHECKED_CAST")
                override fun onSuccess(result: Any) {
                    super.onSuccess(result)
                    if (isFirstOfPair) {
                        result1 = result as T1?
                    } else {
                        result2 = result as T2?
                    }
                    result1?.let { r1 ->
                        result2?.let { r2 ->
                            try {
                                val zipResult = biFunction(r1, r2)
                                zipSingleObservable.emitter.onSuccess(zipResult)
                            } catch (t: Throwable) {
                                onError(t)
                            }
                        }
                    }
                }

                override fun onError(t: Throwable) {
                    super.onError(t)
                    // cancel all when 1 of 2 failed
                    compositeDisposable.clear()
                    zipSingleObservable.emitter.onError(t)
                }
            }

            singleObservable1.subscribe(ChildSingleObserver(true))
            singleObservable2.subscribe(ChildSingleObserver(false))

            zipSingleObservable.future = null
            zipSingleObservable.childObservables.add(singleObservable1)
            zipSingleObservable.childObservables.add(singleObservable2)

            return zipSingleObservable
        }

        fun <T> just(item: T): SingleObservable<T> {
            val newObservable = SingleObservable<T>()
            newObservable.emitter.onSuccess(item)
            return newObservable
        }
    }
}

class CompletableObservable private constructor() : Observable() {
    override val emitter = CompletableEmitter()

    companion object {
        fun fromCallable(
            executorService: ExecutorService,
            callable: () -> Any
        ): CompletableObservable {
            val newObservable = CompletableObservable()
            executorService.submit {
                try {
                    callable()
                    newObservable.emitter.onComplete()
                } catch (t: Throwable) {
                    newObservable.emitter.onError(t)
                }
            }
            return newObservable
        }
    }
}