package com.mgt.downloader.base

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class MyJavaScriptInterface(private val onSuccess: ((html: String) -> Any)?) {
    private var isFirstLoad = true
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    @JavascriptInterface
    fun onLoaded(html: String?) {
        if (isFirstLoad) {
            isFirstLoad = false
            runnable = Runnable { html?.let { onSuccess?.invoke(it) } }
            handler.postDelayed(runnable, 2000)
            return
        }
        handler.removeCallbacks(runnable)
        html?.let {
            onSuccess?.invoke(it)
        }
    }
}