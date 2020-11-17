package com.mgt.downloader.base

import com.mgt.downloader.rxjava.CompositeDisposable

interface HasDisposable {
    val compositeDisposable : CompositeDisposable
}