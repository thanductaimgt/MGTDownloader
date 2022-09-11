package com.mgt.downloader.ui

import com.mgt.downloader.App
import com.mgt.downloader.base.BaseViewModel
import com.mgt.downloader.di.DI
import com.mgt.downloader.di.DI.config
import com.mgt.downloader.di.DI.extractorProvider
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.Event
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver


class MainViewModel : BaseViewModel() {
    private val getConfigObserver by lazy {
        object : SingleObserver<String>(this) {
            override fun onSuccess(result: String) {
                super.onSuccess(result)

                config.updateAppConfig(result)

                val appVersionCode = utils.getAppVersionCode()
                if (appVersionCode < config.newestVersionCode) {
                    liveEvent.postValue(
                        Event(
                            MainActivity.EVENT_SHOW_UPDATE_APP_DIALOG
                        )
                    )
                }
            }
        }
    }

    init {
        fetchAppConfig()
    }

    private fun fetchAppConfig() {
        SingleObservable.fromCallable(DI.unboundExecutorService) {
            utils.getDontpadContent(config.getDontpadAppConfigUrl())
        }.subscribe(getConfigObserver)
    }

    fun getFilePreviewInfo(
        url: String,
        observer: SingleObserver<FilePreviewInfo>
    ) {
        App.fileInfoCaches[url]?.let {
            observer.onSuccess(it)
        } ?: extractorProvider.provideExtractor(this, url).extract(url, observer)
    }
}