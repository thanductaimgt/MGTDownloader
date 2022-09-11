package com.mgt.downloader.base

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.mgt.downloader.nonserialize_model.MyError
import com.mgt.livedata.Observer

abstract class BaseActivity : AppCompatActivity() {
    abstract val viewModel: BaseViewModel

    fun observeError(viewLifecycleOwner: LifecycleOwner, observer: Observer<MyError>) {
        viewModel.liveError.observe(viewLifecycleOwner) {
            observer.onChanged(mapError(it))
        }
    }

    open fun mapError(error: MyError): MyError {
        return error
    }

    companion object {
        var baseError = 0
        var baseEvent = 0
    }
}
