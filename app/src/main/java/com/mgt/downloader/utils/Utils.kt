package com.mgt.downloader.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.ImageView
import com.mgt.downloader.R
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.data_model.FilePreviewInfo
import com.squareup.picasso.*
import org.apache.commons.lang.StringEscapeUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.collections.ArrayList


object Utils {
    fun getFile(context: Context, fileName: String): File {
        return File(getFilePath(context, fileName))
    }

    fun getFilePath(context: Context, fileName: String): String {
        return "${getDownloadDirPath(context)}/$fileName"
    }

    fun getFileName(uri: String, fileExtension: String? = null): String {
        val isValidUrl = URLUtil.isValidUrl(uri)

        val fileName = if (isValidUrl) {
            URLUtil.guessFileName(uri, null, fileExtension).let {
                if (it.endsWith(".bin")) it.dropLast(4) else it
            }
        } else {
            getFileNameFromLocalUri(uri)
        }

        val extension = fileExtension?.let { ".$it" }
            ?: getFileExtension(fileName).let { if (it == "") it else ".$it" }

        val fileNameWithoutExtension = if (isValidUrl) {
            fileName.takeWhile { it != '.' && it != '#' && it != '?' }
        } else {
            val lastDotIdx = fileName.indexOfLast { it == '.' }
            if (lastDotIdx == -1) {
                fileName
            } else {
                fileName.take(lastDotIdx)
            }
        }

        return "$fileNameWithoutExtension$extension"
    }

    private fun getFileNameFromLocalUri(uri: String): String {
        val filePathWithoutSeparator =
            if (uri.endsWith(File.separator)) uri.dropLast(1) else uri
        return filePathWithoutSeparator
            .takeLastWhile { it != '/' }
    }

    fun getFileExtension(uri: String): String {
        return uri.takeLastWhile { it != '.' }.let { if (it.length != uri.length) it else "" }
            .takeWhile { it != '#' && it != '?' }
    }

    fun getFileSize(url: String): Long {
        return try {
            openConnection(url).use {
                it.contentLength.toLong()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Constants.ERROR.toLong()
        }
    }

    fun isMultipartSupported(url: String): Boolean {
        return openConnection(url, rangeStart = 0).use {
            it.responseCode == Constants.HTTP_PARTIAL_CONTENT
        }
    }

    fun deleteFileOrDir(context: Context, localPath: String): Boolean {
        val absolutePath = "${getDownloadDirPath(context)}${File.separator}$localPath"
        val file = File(absolutePath)
        if (file.exists()) {
            if (file.isDirectory) {
                file.list()?.forEach {
                    if (!deleteFileOrDir(context, "$localPath${File.separator}$it")) {
                        return false
                    }
                }
            }
            return file.delete() || file.canonicalFile.delete() || context.applicationContext.deleteFile(
                file.name
            )
        } else {
            return true
        }
    }

    fun generateNewDownloadFileName(
        context: Context,
        fileName: String,
        stopCondition: (newFileName: String) -> Boolean = { newFileName ->
            !getFile(context, newFileName).exists()
        }
    ): String {
        var file = getFile(context, fileName)
        val tail = if (file.extension != "") ".${file.extension}" else ""
        var originalNameWithoutExtension = file.nameWithoutExtension
        var originalNameWithoutExtensionAndNumber = originalNameWithoutExtension

        var newNumber = 1
        if (originalNameWithoutExtension.length >= 4) {
            val fourLastElement =
                originalNameWithoutExtension.takeLast(4)
            logD(TAG, "originalNameWithoutExtension: $originalNameWithoutExtensionAndNumber")
            logD(TAG, "fourLastElement: $fourLastElement")
            if (fourLastElement.matches(Regex(" \\([1-9]\\)"))) {
                originalNameWithoutExtensionAndNumber = originalNameWithoutExtension.dropLast(4)
                newNumber = fourLastElement[2].toString().toInt() + 1
                logD(
                    TAG,
                    "originalNameWithoutExtension new: $originalNameWithoutExtensionAndNumber"
                )
                logD(TAG, "count: $newNumber")
            }
        }

        var newFileNameWithoutExtension = "$originalNameWithoutExtensionAndNumber ($newNumber)"
        file = getFile(context, "$newFileNameWithoutExtension$tail")

        while (newNumber > 9 || !stopCondition(file.name)) {
            newNumber++
            if (newNumber > 9) {
                newNumber = 1
                originalNameWithoutExtensionAndNumber =
                    if (originalNameWithoutExtensionAndNumber != originalNameWithoutExtension) {
                        originalNameWithoutExtension
                    } else {
                        "$originalNameWithoutExtension ($newNumber)"
                    }
                originalNameWithoutExtension = originalNameWithoutExtensionAndNumber
                newFileNameWithoutExtension = "$originalNameWithoutExtensionAndNumber ($newNumber)"
            } else {
                newFileNameWithoutExtension = "$originalNameWithoutExtensionAndNumber ($newNumber)"
            }
            file = getFile(context, "$newFileNameWithoutExtension$tail")
        }
        return "$newFileNameWithoutExtension$tail"
    }

    fun getResIdFromFileExtension(context: Context, fileExtension: String): Int {
        val resourceId = context.resources.getIdentifier(
            fileExtension, "drawable",
            context.packageName
        )

        return if (resourceId != 0) resourceId else R.drawable.file
    }

    fun getDownloadDirPath(context: Context): String {
        return try {
            val downloadDir = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(null)!!
            } else {
                File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)!!.path}/MGT Downloader")
            }

            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw Throwable("Fail to create directory ${downloadDir.path}")
            }
            downloadDir.path
        } catch (t: Throwable) {
            context.getString(R.string.path_not_available)
        }
    }

    fun isDownloadedFileExist(context: Context, downloadTask: DownloadTask): Boolean {
        val file = getFile(context, downloadTask.fileName)
        return file.exists() && file.isDirectory == downloadTask.isDirectory
    }

    fun getPathRoot(uri: String): String {
        return uri.takeWhile { it != '/' }
    }

    fun getPathLevel(uri: String): Int {
        return uri.split(File.separator).filter { it != "" }.size
    }

    fun getPercentage(current: Number, total: Number): Float {
        return (current.toFloat() * 100) / total.toFloat()
    }

    fun getDownloadRemainingTimeInMilli(
        elapsedTime: Long,
        totalBytes: Long,
        downloadedBytes: Long
    ): Long {
        val estimatedTime = elapsedTime * totalBytes / downloadedBytes
        return estimatedTime - elapsedTime
    }

    fun getFormatFileSize(
        bytes: Long,
        detailLevel: Int = Constants.FILE_SIZE_DETAIL_LEVEL_MEDIUM
    ): String {
        when (detailLevel) {
            Constants.FILE_SIZE_DETAIL_LEVEL_LOW -> {
                return when {
                    bytes < Constants.ONE_KB_IN_B -> "$bytes B"
                    bytes < Constants.ONE_MB_IN_B -> String.format(
                        "%d KB",
                        bytes / Constants.ONE_KB_IN_B
                    )
                    bytes < Constants.ONE_GB_IN_B -> String.format(
                        "%.1f MB",
                        bytes / Constants.ONE_MB_IN_B.toFloat()
                    )
                    else -> String.format("%.2f GB", bytes / Constants.ONE_GB_IN_B.toFloat())
                }
            }
            else -> {
                return when {
                    bytes < Constants.ONE_KB_IN_B -> "$bytes B"
                    bytes < Constants.ONE_MB_IN_B -> String.format(
                        "%.1f KB",
                        bytes / Constants.ONE_KB_IN_B.toFloat()
                    )
                    bytes < Constants.ONE_GB_IN_B -> String.format(
                        "%.2f MB",
                        bytes / Constants.ONE_MB_IN_B.toFloat()
                    )
                    else -> String.format("%.3f GB", bytes / Constants.ONE_GB_IN_B.toFloat())
                }
            }
        }
    }

    fun isSizeChangeSignificantly(prevSize: Long, curSize: Long): Boolean {
        return when {
            prevSize < Constants.ONE_MB_IN_B -> curSize - prevSize > 100 * Constants.ONE_KB_IN_B
            prevSize < Constants.ONE_GB_IN_B -> curSize - prevSize > 0.1 * Constants.ONE_MB_IN_B
            else -> getFormatFileSize(prevSize) != getFormatFileSize(curSize)
        }
    }

    fun getFormatTimeDiff(context: Context, diffInMillisecond: Long): String {
        return when {
            diffInMillisecond < Constants.ONE_MIN_IN_MILLISECOND -> getFormatTime(
                diffInMillisecond / Constants.ONE_SECOND_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_second)
            )
            diffInMillisecond < Constants.ONE_HOUR_IN_MILLISECOND -> getFormatTime(
                diffInMillisecond / Constants.ONE_MIN_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_minute)
            )
            diffInMillisecond < Constants.ONE_DAY_IN_MILLISECOND -> getFormatTime(
                diffInMillisecond / Constants.ONE_HOUR_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_hour)
            )
            else -> getFormatTime(
                diffInMillisecond / Constants.ONE_DAY_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_day)
            )
        }
    }

    fun getFormatTimeDiffTillNow(context: Context, date: Date): String {
        val diffByMillisecond = System.currentTimeMillis() - date.time
        return when {
            diffByMillisecond < Constants.ONE_MIN_IN_MILLISECOND -> context.getString(R.string.desc_now)
            diffByMillisecond < Constants.ONE_HOUR_IN_MILLISECOND -> getFormatTime(
                diffByMillisecond / Constants.ONE_MIN_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_minute)
            ) + " " + context.getString(R.string.desc_ago)
            diffByMillisecond < Constants.ONE_DAY_IN_MILLISECOND -> getFormatTime(
                diffByMillisecond / Constants.ONE_HOUR_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_hour)
            ) + " " + context.getString(R.string.desc_ago)
            diffByMillisecond < Constants.SEVEN_DAYS_IN_MILLISECOND -> getFormatTime(
                diffByMillisecond / Constants.ONE_DAY_IN_MILLISECOND.toLong(),
                context.getString(R.string.desc_day)
            ) + " " + context.getString(R.string.desc_ago)
            else -> SimpleDateFormat.getDateInstance().format(date)
        }
    }

    private fun getFormatTime(quantity: Long, unit: String): String {
        return "$quantity $unit"
    }

    fun getExtraBytes(extra: ByteArray, extraId: Short): ByteArray {
        val wrapped = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN)
        var res: ByteArray? = null
        var i = 0
        while (i < extra.size - 1) {
            if (isHeader(extra.copyOfRange(i, i + 2), extraId)) {
                val extraSize = wrapped.getShort(i + 2)
                res = extra.copyOfRange(i + 4, i + 4 + extraSize)
                break
            }
            i++
        }
        return res!!
    }

    fun isHeader(data: ByteArray, header: Number): Boolean {
        val extraIdBytes =
            when (header) {
                is Short -> ByteBuffer.allocate(Short.Companion.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN).putShort(
                        header
                    )
                is Int -> ByteBuffer.allocate(Int.Companion.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN).putInt(
                        header
                    )
                else -> ByteBuffer.allocate(Long.Companion.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN).putLong(
                        header as Long
                    )
            }
        return data.contentEquals(extraIdBytes.array())
    }

    fun getZipEntries(
        zipPreviewInfo: FilePreviewInfo
    ): List<ZipEntry> {
        return if (zipPreviewInfo.centralDirOffset == -1 && zipPreviewInfo.centralDirSize == -1) {
            getFileEntriesFromLocalUri(zipPreviewInfo)
        } else {
            getFileEntriesFromNetworkUrl(zipPreviewInfo)
        }
    }

    private fun getFileEntriesFromLocalUri(zipPreviewInfo: FilePreviewInfo): List<ZipEntry> {
        // check uri here
        return ZipFile(zipPreviewInfo.displayUri).entries().toList()
    }

    private fun getFileEntriesFromNetworkUrl(
        zipPreviewInfo: FilePreviewInfo
    ): List<ZipEntry> {
        val zipEntries = ArrayList<ZipEntry>()

        var input: InputStream? = null
        var connection: HttpURLConnection? = null
        try {
            connection =
                openConnection(
                    zipPreviewInfo.displayUri,
                    zipPreviewInfo.centralDirOffset.toLong(),
                    (zipPreviewInfo.centralDirOffset + zipPreviewInfo.centralDirSize - 1).toLong()
                )
            input = connection.inputStream

            val data = ByteArray(zipPreviewInfo.centralDirSize)
            val buffer = ByteArray(4096)
            var count = input!!.read(buffer)
            var readByteNum = 0
            while (count != -1) {
                for (i in 0 until count) {
                    data[readByteNum + i] = buffer[i]
                }
                readByteNum += count
                count = input.read(buffer)
            }
            val wrapped = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            var i = 0
            while (i < data.size) {
                val bitFlag = wrapped.getShort(i + 8)
                val compressionMethod = wrapped.getShort(i + 10)
                val lastModifiedTime = data.copyOfRange(i + 12, i + 14)
                val lastModifiedDate = data.copyOfRange(i + 14, i + 16)
                val crC32 = data.copyOfRange(i + 16, i + 20)
                val compressedSize = wrapped.getInt(i + 20)
                val uncompressedSize = wrapped.getInt(i + 24)
                val fileNameLength = wrapped.getShort(i + 28)
                val extraFieldLength = wrapped.getShort(i + 30)
                val fileCommentLength = wrapped.getShort(i + 32)
                val localHeaderRelativeOffset = data.copyOfRange(i + 42, i + 46)
                val fileExtra =
                    data.copyOfRange(
                        i + 46 + fileNameLength,
                        i + 46 + fileNameLength + extraFieldLength
                    )
                val fileComment = data.copyOfRange(
                    i + 46 + fileNameLength + extraFieldLength,
                    i + 46 + fileNameLength + extraFieldLength + fileCommentLength
                ).toString()
                val utfLabel =
                    if (bitFlag.toInt().and(0x800) != 0) Charsets.UTF_8 else Charsets.US_ASCII
                val fileName = String(data.copyOfRange(i + 46, i + 46 + fileNameLength), utfLabel)

                zipEntries.add(ZipEntry(fileName).apply {
                    comment = fileComment
                    crc = CRC32().apply { update(crC32) }.value
                    setCompressedSize(compressedSize.toLong())
                    size = uncompressedSize.toLong()
                    method = compressionMethod.toInt()

                    //add localHeaderRelativeOffset to extra
                    extra = fileExtra + createExtraBytes(
                        Constants.RELATIVE_OFFSET_LOCAL_HEADER,
                        localHeaderRelativeOffset
                    )
                    time = getTime(
                        BitSet.valueOf(lastModifiedTime),
                        BitSet.valueOf(lastModifiedDate)
                    )
                })

                i += 46 + fileNameLength + extraFieldLength + fileCommentLength
            }
        } finally {
            input?.close()
            connection?.disconnect()
        }
        return zipEntries
    }

    @Suppress("SameParameterValue")
    private fun createExtraBytes(extraId: Short, extraData: ByteArray): ByteArray {
        val extraIdBytes =
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(extraId).array()
        val extraSizeBytes =
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(extraData.size.toShort())
                .array()
        return extraIdBytes + extraSizeBytes + extraData
    }

    private fun getTime(msDosTime: BitSet, msDosDate: BitSet): Long {
        val seconds = msDosTime[0, 5].toInt() * 2
        val minutes = msDosTime[5, 11].toInt()
        val hours = msDosTime[11, 16].toInt()

        val days = msDosDate[0, 5].toInt()
        val months = msDosDate[5, 9].toInt() - 1
        val years = msDosDate[9, 16].toInt() + 1980

        return Calendar.getInstance()
            .apply { set(years, months, days, hours, minutes, seconds) }
            .timeInMillis
    }

    fun getZipCentralDirInfo(fileUri: String): Pair<Int, Int> {
        var input: InputStream? = null
        var connection: HttpURLConnection? = null
        try {
            connection =
                openConnection(
                    fileUri,
                    rangeEnd = Constants.MAX_EOCD_AND_COMMENT_SIZE.toLong()
                )
            input = connection.inputStream

            val data = ByteArray(4096)
            val wrapped = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            var count = input.read(data, 3, data.size - 3)
            var isEOCDFound = false
            var i: Int
            var centralDirOffset = -1
            var centralDirSize = -1
            while (count != -1) {
                i = 0
                while (i < count + 3 - 21) {
                    if (data[i] == 0x50.toByte() && data[i + 1] == 0x4B.toByte() && data[i + 2] == 0x05.toByte() && data[i + 3] == 0x06.toByte()) {
                        centralDirOffset = wrapped.getInt(i + 16)
                        centralDirSize = wrapped.getInt(i + 12)
                        isEOCDFound = true
                        break
                    }
                    i++
                }
                if (isEOCDFound) {
                    break
                }
                for (j in 0 until 3) {
                    data[j] = if (count + j >= 0) data[count + j] else 0
                }
                count = input.read(data, 3, data.size - 3)
            }

            return Pair(centralDirOffset, centralDirSize)
        } catch (e: Throwable) {
            e.printStackTrace()
            return Pair(Constants.ERROR, Constants.ERROR)
        } finally {
            //close all resources
            input?.close()
            connection?.disconnect()
        }
    }

    fun openConnection(
        url: String,
        rangeStart: Long? = null,
        rangeEnd: Long? = null,
        timeOut: Int = 10000,
    ): HttpURLConnection {
        logD(TAG, "connect to url: $url")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            (rangeStart ?: rangeEnd)?.let {
                addRequestProperty(
                    "Range",
                    "bytes=${rangeStart ?: ""}-${rangeEnd ?: ""}"
                )
            }
            addRequestProperty(
                "Cookie",
                CookieManager.getInstance().getCookie(url)
            )
            for (header in Configurations.requestHeaders) {
                addRequestProperty(header.key, header.value)
            }
            readTimeout = timeOut

            logD(TAG, "Request headers: $requestProperties")

            connect()
            if (responseCode != Constants.HTTP_PARTIAL_CONTENT && responseCode != HttpURLConnection.HTTP_OK && responseCode != Constants.HTTP_RANGE_NOT_SATISFIABLE) {
                throw Throwable(
                    "$TAG: Server returned HTTP ${responseCode}: $responseMessage"
                )
            }
        }
    }

    fun getContent(url: String): String {
        return openConnection(url).use {
            getContent(it.inputStream)
        }
    }

    fun getContent(inputStream: InputStream): String {
        val streamMap = StringBuilder()

        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                streamMap.append("$line\n")
            }
            streamMap.toString()
        }
    }

    /**
     * dontpad url format: http://dontpad.com{subpath}
     */
    fun getDontpadContent(subpath: String): String {
        val content = getContent("${Constants.DONTPAD_BASE_URL}$subpath")
        return content.findValue("<textarea(.*?)>", "</textarea>", "", false).unescapeHtml()
    }

    fun hideKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    fun showKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun getFileOrDirSize(context: Context, dirAppLocalPath: String): Long {
        return try {
            val file = getFile(context, dirAppLocalPath)
            if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
        } catch (t: Throwable) {
            logD(TAG, "getFileOrDirSize fail")
            t.printStackTrace()
            -1
        }
    }

    private fun getDirSize(directory: File): Long {
        var length: Long = 0
        directory.listFiles()?.let { files ->
            for (file in files) {
                length += if (file.isFile)
                    file.length()
                else
                    getDirSize(file)
            }
        }
        return length
    }

    fun getSharePreference(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.SHARE_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun getBlankArrayList(size: Int): ArrayList<Long> {
        return ArrayList<Long>(size).apply {
            for (i in 0 until size) {
                add(0)
            }
        }
    }

    private val tikTokPattern =
        Pattern.compile("^(https?://)?(www\\.)?((vt\\.)?(tiktok)\\.com)/.+\$")

    fun isTikTokUrl(url: String): Boolean {
        val matcher = tikTokPattern.matcher(url)
        return matcher.find()
    }

    private val facebookPattern =
        Pattern.compile("^(https?://)?(www\\.)?(mbasic.facebook|m\\.facebook|facebook|fb)\\.(com|me)/([^/?].+/)?")//video(s|\\.php)[/?].+\$")

    fun isFacebookUrl(url: String): Boolean {
        val matcher = facebookPattern.matcher(url)
        return matcher.find()
    }

    private val bobaPattern =
        Pattern.compile("^(https?://)?(www\\.)?(bo3\\.me)/(s/)?.+\$")

    fun isBobaUrl(url: String): Boolean {
        val matcher = bobaPattern.matcher(url)
        return matcher.find()
    }

    private val instaPattern =
        Pattern.compile("^(https?://)?(www\\.)?(instagram\\.com)/.+\$")

    fun isInstaUrl(url: String): Boolean {
        val matcher = instaPattern.matcher(url)
        return matcher.find()
    }

    private val twitterPattern =
        Pattern.compile("^(https?://)?(www\\.)?(twitter\\.com)/.+\$")

    fun isTwitterUrl(url: String): Boolean {
        val matcher = twitterPattern.matcher(url)
        return matcher.find()
    }

    fun getFormatRatio(width: Int?, height: Int?): String {
        return if (width == null || height == null) {
            "390:300"
        } else {
            "$width:$height"
        }
    }

    fun isSmallerRatio(ratio1: String, ratio2: String): Boolean {
        val ratio1Float = getRatioFloat(ratio1)
        val ratio2Float = getRatioFloat(ratio2)
        return ratio1Float < ratio2Float
    }

    private fun getRatioFloat(ratio: String): Float {
        val dimens = ratio.split(":")
        return dimens[0].toInt() / dimens[1].toFloat()
    }

    fun isNetworkUri(uri: String): Boolean {
        return uri.startsWith("http")
    }

    fun isContentUri(localPath: String): Boolean {
        return localPath.startsWith("content://")
    }
}

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
    if (url == null || Utils.isNetworkUri(url) || Utils.isContentUri(url)) {
        urlToLoad = url
        doCache = url != null && Utils.isNetworkUri(url)
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

const val DEFAULT_TARGET = "(.|\n)*?"

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
    unescape: Boolean = true,
    target: String,
): T {
    if (prefix == null || postfix == null) return default
    try {
        val pattern = Pattern.compile("$prefix(?<target>$target)$postfix")
        val matcher = pattern.matcher(input)
        return if (matcher.find()) {
            var valueEscaped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                matcher.group("target")
            } else {
                matcher.group(matcher.groupCount())
            }!!
            try {
                if (unescape) {
                    valueEscaped = valueEscaped.unescapePerl()
                    valueEscaped = valueEscaped.unescapeJava()
                    valueEscaped = valueEscaped.unescapeHtml()
                }
                valueEscaped
            } catch (t: Throwable) {
                valueEscaped
            }
        } else {
            default
        } as T
    } catch (t: Throwable) {
        t.printStackTrace()
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
    return PerlStringHelper.unescapePerl(this)
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