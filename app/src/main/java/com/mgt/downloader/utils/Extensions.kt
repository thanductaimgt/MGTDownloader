package com.mgt.downloader.utils

import android.os.Build
import android.util.Log
import android.widget.ImageView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mgt.downloader.di.DI.gson
import com.mgt.downloader.di.DI.perlStringHelper
import com.mgt.downloader.di.DI.utils
import com.squareup.picasso.*
import org.apache.commons.lang.StringEscapeUtils
import java.net.HttpURLConnection
import java.util.*
import java.util.regex.Pattern
import kotlin.reflect.KClass


val Any.TAG: String
    get() = this::class.java.simpleName

fun BitSet.toInt(): Int {
    var res = 0
    for (i in 0 until this.length()) {
        res += if (this.get(i)) 1.shl(i) else 0
    }
    return res
}

private fun Picasso.loadCompat(url: String?): RequestCreator {
    val doCache: Boolean
    val urlToLoad: String?
    if (url == null || utils.isNetworkUri(url) || utils.isContentUri(url)) {
        urlToLoad = url
        doCache = url != null && utils.isNetworkUri(url)
    } else {
        urlToLoad = "file://$url"
        doCache = false
    }
    return load(urlToLoad).let { if (!doCache) it.networkPolicy(NetworkPolicy.NO_STORE) else it }
}

fun Picasso.smartLoad(
    url: String?,
    imageView: ImageView,
    applyConfig: ((requestCreator: RequestCreator) -> Unit)? = null
) {
    var requestCreator = loadCompat(url)
    applyConfig?.invoke(requestCreator)

    requestCreator.networkPolicy(NetworkPolicy.OFFLINE)
        .into(imageView, object : Callback.EmptyCallback() {
            override fun onError(e: Exception?) {
                requestCreator = Picasso.get().loadCompat(url)
                applyConfig?.invoke(requestCreator)

                requestCreator.networkPolicy(NetworkPolicy.NO_CACHE)
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .into(imageView)
            }
        })
}

fun String.findValue(prefix: String, postfix: String): String {
    return substring(prefix.let { indexOf(it) + it.length }).let {
        StringEscapeUtils.unescapeJava(it.substring(0, it.indexOf(postfix)))
    }
}

const val DEFAULT_TARGET = """(.|\n)*?"""

/**
 * Only prefix could be regex.
 *
 * Be careful with ( and )
 */
fun <T : String?> String.findValue(
    prefix: String?,
    postfix: String?,
    default: T,
    unescape: Boolean = true,
    target: String? = null,
): T {
    return findValue(this, prefix, postfix, default, unescape, target ?: DEFAULT_TARGET)
}

private fun <T : String?> findValue(
    input: CharSequence,
    prefix: String?,
    postfix: String?,
    default: T,
    unescape: Boolean,
    target: String,
): T {
    if (prefix == null || postfix == null) return default
    try {
        val pattern = Pattern.compile(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                "$prefix(?<target>$target)$postfix"
            } else {
                "$prefix($target)$postfix"
            }
        )
        val matcher = pattern.matcher(input)
        return if (matcher.find()) {
            var valueEscaped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                matcher.group("target")
            } else {
                matcher.group(matcher.groupCount() - 1)
            }
            try {
                if (unescape) {
                    valueEscaped = valueEscaped?.unescapePerl()
                    valueEscaped = valueEscaped?.unescapeJava()
                    valueEscaped = valueEscaped?.unescapeHtml()
                }
                valueEscaped
            } catch (t: Throwable) {
                valueEscaped
            }
        } else {
            default
        } as T
    } catch (t: Throwable) {
        recordNonFatalException(t)
        return default
    }
}

fun String.unescapeJava(): String {
    return StringEscapeUtils.unescapeJava(this)
}

fun String.unescapeHtml(): String {
    return StringEscapeUtils.unescapeHtml(this)
}

fun String.unescapePerl(): String {
    return perlStringHelper.unescapePerl(this)
}

fun String.format(vararg args: Any?): String {
    return String.format(this, args)
}

inline fun <T> HttpURLConnection.use(block: (conn: HttpURLConnection) -> T): T {
    try {
        return block(this)
    } finally {
        disconnect()
    }
}

fun logD(tag: String, message: String) {
    if (com.mgt.downloader.BuildConfig.DEBUG) {
        Log.d(tag, message)
    }
}

fun logE(tag: String, message: String) {
    if (com.mgt.downloader.BuildConfig.DEBUG) {
        Log.e(tag, message)
    }
}

fun recordNonFatalException(t: Throwable) {
    logE("Extensions", "recordNonFatalException")
    printTrace(t)
    FirebaseCrashlytics.getInstance().recordException(t)
}

fun printTrace(t: Throwable) {
    if (com.mgt.downloader.BuildConfig.DEBUG) {
        t.printStackTrace()
    }
}

fun Any.toJson(): String = gson.toJson(this)

fun <T : Any> KClass<T>.fromJson(json: String): T = gson.fromJson(json, this.java)
