package com.mgt.downloader.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import com.mgt.downloader.App
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.config
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.serialize_model.DownloadTask
import org.json.JSONObject
import java.io.*
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
import kotlin.system.exitProcess


class Utils {
    fun getDownloadFilePath(fileName: String): String {
        return getDownloadFile(fileName).path
    }

    fun getDownloadFile(fileName: String): File {
        return File(getDownloadDir(), fileName)
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
            recordNonFatalException(t)
            Constants.ERROR.toLong()
        }
    }

    fun isMultipartSupported(url: String): Boolean {
        return openConnection(url, rangeStart = 0).use {
            it.responseCode == Constants.HTTP_PARTIAL_CONTENT
        }
    }

    fun deleteFileOrDir(context: Context, localPath: String): Boolean {
        val file = File(getDownloadDir(), localPath)
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
        fileName: String,
        stopCondition: (newFileName: String) -> Boolean = { newFileName ->
            !getDownloadFile(newFileName).exists()
        }
    ): String {
        var file = getDownloadFile(fileName)
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
        file = getDownloadFile("$newFileNameWithoutExtension$tail")

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
            file = getDownloadFile("$newFileNameWithoutExtension$tail")
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

    fun getDownloadDirPath(): String? {
        return getDownloadDir()?.path
    }

    private fun getDownloadDir(): File? {
        return try {
            val child = if (config.getEnv() == Config.Env.LIVE) {
                "MGT Downloader"
            } else {
                "MGT Downloader Test"
            }
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                child
            )

            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw Throwable("Fail to create directory ${downloadDir.path}")
            }
            downloadDir
        } catch (t: Throwable) {
            return null
        }
    }

    fun getDownloadDirRelativePath(): String? {
        return runCatching {
            getDownloadDir()?.toRelativeString(Environment.getExternalStorageDirectory())
        }.getOrDefault(getDownloadDirPath())
    }

    fun getCacheFile(context: Context, fileName: String): File {
        val name = "cache-${config.getEnv()}"
        val cacheDir = context.getDir(name, Context.MODE_PRIVATE)
        return File(cacheDir, fileName)
    }

    fun isDownloadedFileExist(downloadTask: DownloadTask): Boolean {
        val file = getDownloadFile(downloadTask.fileName)
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
        return res ?: ByteArray(0)
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
            var count = input?.read(buffer) ?: -1
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
            recordNonFatalException(e)
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
            for (header in config.requestHeaders) {
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

    fun readInputStream(url: String): String {
        return openConnection(url).use {
            readInputStream(it.inputStream)
        }
    }

    fun readInputStream(inputStream: InputStream): String {
        val streamMap = StringBuilder()

        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                streamMap.append("$line\n")
            }
            streamMap.toString()
        }
    }

    fun writeOutputStream(outputStream: OutputStream, content: String) {
        BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
            writer.write(content)
        }
    }

    /**
     * dontpad url format: http://dontpad.com{subpath}
     */
    fun getDontpadContent(dontpadUrl: String): String {
        val content = readInputStream("$dontpadUrl.body.json?lastUpdate=0")
        val contentObj = JSONObject(content)
        return contentObj.optString("body")
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

    fun getFileOrDirSize(dirAppLocalPath: String): Long {
        return try {
            val file = getDownloadFile(dirAppLocalPath)
            if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
        } catch (t: Throwable) {
            logD(TAG, "getFileOrDirSize fail")
            recordNonFatalException(t)
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

    fun getAppVersionCode(): Int {
        return try {
            val pInfo: PackageInfo =
                App.instance.packageManager
                    .getPackageInfo(App.instance.packageName, 0)
            pInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            recordNonFatalException(e)
            -1
        }
    }

    fun navigateToCHPlay(context: Context) {
        val packageName = context.applicationContext.packageName
        val uri: Uri = Uri.parse("market://details?id=$packageName")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        var flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            flags = flags or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
        goToMarket.addFlags(flags)

        try {
            context.startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    fun restartApp() {
//        val context=app
//        val mStartActivity = Intent(context, HomeActivity::class.java)
//        val mPendingIntentId = 123456
//        val mPendingIntent: PendingIntent = PendingIntent.getActivity(
//            context,
//            mPendingIntentId,
//            mStartActivity,
//            PendingIntent.FLAG_CANCEL_CURRENT
//        )
//        val mgr: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent)
        exitProcess(0)
    }
}
