package com.mgt.downloader.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.ImageView
import com.squareup.picasso.*
import com.squareup.picasso.Target
import com.mgt.downloader.MyApplication
import com.mgt.downloader.R
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.data_model.FilePreviewInfo
import java.io.File
import java.io.InputStream
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
        val input: InputStream? = null
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection(url)
            connection.contentLength.toLong()
        } catch (e: Throwable) {
            e.printStackTrace()
            Constants.ERROR.toLong()
        } finally {
            //close all resources
            input?.close()
            connection?.disconnect()
        }
    }

    fun deleteFileOrDir(context: Context, localPath: String): Boolean {
        val absolutePath = "${getDownloadDirPath(context)}${File.separator}$localPath"
        val file = File(absolutePath)
        if (file.exists()) {
            if (file.isDirectory) {
                file.list().forEach {
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
            log(TAG, "originalNameWithoutExtension: $originalNameWithoutExtensionAndNumber")
            log(TAG, "fourLastElement: $fourLastElement")
            if (fourLastElement.matches(Regex(" \\([1-9]\\)"))) {
                originalNameWithoutExtensionAndNumber = originalNameWithoutExtension.dropLast(4)
                newNumber = fourLastElement[2].toString().toInt() + 1
                log(TAG, "originalNameWithoutExtension new: $originalNameWithoutExtensionAndNumber")
                log(TAG, "count: $newNumber")
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

    @Throws(Throwable::class)
    fun getDownloadDirPath(context: Context): String {
        val downloadDir =File("${ if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(null)
        }else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }!!.path}/MGT Downloader")

        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw Throwable("Fail to create directory ${downloadDir.path}")
        }
        return downloadDir.path
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
                is Short -> ByteBuffer.allocate(Short.Companion.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(
                    header
                )
                is Int -> ByteBuffer.allocate(Int.Companion.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(
                    header
                )
                else -> ByteBuffer.allocate(Long.Companion.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(
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
        fileUri: String,
        rangeStart: Long? = null,
        rangeEnd: Long? = null
    ): HttpURLConnection {
        return (URL(fileUri).openConnection() as HttpURLConnection).apply {
            (rangeStart ?: rangeEnd)?.let {
                setRequestProperty(
                    "Range",
                    "bytes=${rangeStart ?: ""}-${rangeEnd ?: ""}"
                )
                readTimeout = 60000
            }

            connect()
            if (responseCode != Constants.HTTP_PARTIAL_CONTENT && responseCode != HttpURLConnection.HTTP_OK && responseCode != Constants.HTTP_RANGE_NOT_SATISFIABLE) {
                throw Throwable(
                    "$TAG: Server returned HTTP ${responseCode}: $responseMessage"
                )
            }
        }
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

    fun log(tag: String, message: String) {
        if (MyApplication.isLogEnabled) {
            Log.d(tag, message)
        }
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
            log(TAG, "getFileOrDirSize fail")
            t.printStackTrace()
            -1
        }
    }

    private fun getDirSize(directory: File): Long {
        var length: Long = 0
        for (file in directory.listFiles()) {
            length += if (file.isFile)
                file.length()
            else
                getDirSize(file)
        }
        return length
    }

    fun getSharePreference(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.SHARE_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun getBlankArrayList(size:Int):ArrayList<Long>{
        return ArrayList<Long>(size).apply {
            for (i in 0 until size){
                add(0)
            }
        }
    }

    private val tikTokPattern = Pattern.compile("^(https?://)?(www\\.)?((vt\\.)?(tiktok)\\.com)/.+\$")

    fun isTikTokUrl(url:String):Boolean{
        val matcher = tikTokPattern.matcher(url)
        return matcher.find()
    }

    private val facebookPattern = Pattern.compile("^(https?://)?(www\\.)?(mbasic.facebook|m\\.facebook|facebook|fb)\\.(com|me)/([^/?].+/)?")//video(s|\\.php)[/?].+\$")

    fun isFacebookUrl(url:String):Boolean{
        val matcher = facebookPattern.matcher(url)
        return matcher.find()
    }

    fun getFormatRatio(width:Int?, height:Int?):String{
        return if(width==null||height==null){
            "390:300"
        }else{
            "$width:$height"
        }
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * (context.resources
            .displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    fun isSmallerRatio(ratio1:String, ratio2:String):Boolean{
        val ratio1Float= getRatioFloat(ratio1)
        val ratio2Float= getRatioFloat(ratio2)
        return ratio1Float<ratio2Float
    }

    private fun getRatioFloat(ratio:String):Float{
        val dimens = ratio.split(":")
        return dimens[0].toInt()/dimens[1].toFloat()
    }

    fun isNetworkUri(uri: String): Boolean {
        return uri.startsWith("http")
    }

    fun isContentUri(localPath: String): Boolean {
        return localPath.startsWith("content://")
//        return localPath.startsWith("content://")
    }

    fun unescapePerlString(oldStr: String): String {
        /*
     * In contrast to fixing Java's broken regex charclasses,
     * this one need be no bigger, as unescaping shrinks the string
     * here, where in the other one, it grows it.
     */
        val newstr = StringBuffer(oldStr.length)
        var saw_backslash = false
        var i = 0
        while (i < oldStr.length) {
            var cp = oldStr.codePointAt(i)
            if (oldStr.codePointAt(i) > Character.MAX_VALUE.toInt()) {
                i++
                /****WE HATES UTF-16! WE HATES IT FOREVERSES!!! */
            }
            if (!saw_backslash) {
                if (cp == '\\'.toInt()) {
                    saw_backslash = true
                } else {
                    newstr.append(Character.toChars(cp))
                }
                i++
                continue  /* switch */
            }
            if (cp == '\\'.toInt()) {
                saw_backslash = false
                newstr.append('\\')
                newstr.append('\\')
                i++
                continue  /* switch */
            }
            when (cp.toChar()) {
                'r' -> newstr.append('\r')
                'n' -> newstr.append('\n')
//                'f' -> newstr.append('\f')
                'b' -> newstr.append("\\b")
                't' -> newstr.append('\t')
                'a' -> newstr.append('\u0007')
                'e' -> newstr.append('\u001b')
                'c' -> {
                    if (++i == oldStr.length) {
                        die("trailing \\c")
                    }
                    cp = oldStr.codePointAt(i)
                    /*
                 * don't need to grok surrogates, as next line blows them up
                 */if (cp > 0x7f) {
                        die("expected ASCII after \\c")
                    }
                    newstr.append(Character.toChars(cp xor 64))
                }
                '8', '9' -> run{
                    die("illegal octal digit")
                    --i
                        if (i + 1 == oldStr.length) {
                            /* found \0 at end of string */
                            newstr.append(Character.toChars(0))
                            return@run /* switch */
                        }
                        i++
                        var digits = 0
                        var j: Int
                        j = 0
                        while (j <= 2) {
                            if (i + j == oldStr.length) {
                                break /* for */
                            }
                            /* safe because will unread surrogate */
                            val ch = oldStr[i + j].toInt()
                            if (ch < '0'.toInt() || ch > '7'.toInt()) {
                                break /* for */
                            }
                            digits++
                            j++
                        }
                        if (digits == 0) {
                            --i
                            newstr.append('\u0000')
                            return@run /* switch */
                        }
                        var value = 0
                        try {
                            value =
                                oldStr.substring(i, i + digits).toInt(8)
                        } catch (nfe: NumberFormatException) {
                            die("invalid octal value for \\0 escape")
                        }
                        newstr.append(Character.toChars(value))
                        i += digits - 1
                    }
                '1', '2', '3', '4', '5', '6', '7' -> run{
                    --i
                        if (i + 1 == oldStr.length) {
                            newstr.append(Character.toChars(0))
                            return@run
                        }
                        i++
                        var digits = 0
                        var j: Int
                        j = 0
                        while (j <= 2) {
                            if (i + j == oldStr.length) {
                                break
                            }
                            val ch = oldStr[i + j].toInt()
                            if (ch < '0'.toInt() || ch > '7'.toInt()) {
                                break
                            }
                            digits++
                            j++
                        }
                        if (digits == 0) {
                            --i
                            newstr.append('\u0000')
                            return@run
                        }
                        var value = 0
                        try {
                            value =
                                oldStr.substring(i, i + digits).toInt(8)
                        } catch (nfe: NumberFormatException) {
                            die("invalid octal value for \\0 escape")
                        }
                        newstr.append(Character.toChars(value))
                        i += digits - 1
                }
                '0' -> run {
                    if (i + 1 == oldStr.length) {
                        newstr.append(Character.toChars(0))
                        return@run
                    }
                    i++
                    var digits = 0
                    var j: Int
                    j = 0
                    while (j <= 2) {
                        if (i + j == oldStr.length) {
                            break
                        }
                        val ch = oldStr[i + j].toInt()
                        if (ch < '0'.toInt() || ch > '7'.toInt()) {
                            break
                        }
                        digits++
                        j++
                    }
                    if (digits == 0) {
                        --i
                        newstr.append('\u0000')
                        return@run
                    }
                    var value = 0
                    try {
                        value =
                            oldStr.substring(i, i + digits).toInt(8)
                    } catch (nfe: NumberFormatException) {
                        die("invalid octal value for \\0 escape")
                    }
                    newstr.append(Character.toChars(value))
                    i += digits - 1
                } /* end case '0' */
                'x' -> {
                    if (i + 2 > oldStr.length) {
                        die("string too short for \\x escape")
                    }
                    i++
                    var saw_brace = false
                    if (oldStr[i] == '{') {
                        /* ^^^^^^ ok to ignore surrogates here */
                        i++
                        saw_brace = true
                    }
                    var j: Int
                    j = 0
                    while (j < 8) {
                        if (!saw_brace && j == 2) {
                            break /* for */
                        }

                        /*
                     * ASCII test also catches surrogates
                     */
                        val ch = oldStr[i + j].toInt()
                        if (ch > 127) {
                            die("illegal non-ASCII hex digit in \\x escape")
                        }
                        if (saw_brace && ch == '}'.toInt()) {
                            break /* for */
                        }
                        if (!(ch >= '0'.toInt() && ch <= '9'.toInt()
                                    ||
                                    ch >= 'a'.toInt() && ch <= 'f'.toInt()
                                    ||
                                    ch >= 'A'.toInt() && ch <= 'F'.toInt())
                        ) {
                            die(
                                String.format(
                                    "illegal hex digit #%d '%c' in \\x", ch, ch
                                )
                            )
                        }
                        j++
                    }
                    if (j == 0) {
                        die("empty braces in \\x{} escape")
                    }
                    var value = 0
                    try {
                        value = oldStr.substring(i, i + j).toInt(16)
                    } catch (nfe: NumberFormatException) {
                        die("invalid hex value for \\x escape")
                    }
                    newstr.append(Character.toChars(value))
                    if (saw_brace) {
                        j++
                    }
                    i += j - 1
                }
                'u' -> {
                    if (i + 4 > oldStr.length) {
                        die("string too short for \\u escape")
                    }
                    i++
                    var j: Int
                    j = 0
                    while (j < 4) {

                        /* this also handles the surrogate issue */if (oldStr[i + j]
                                .toInt() > 127
                        ) {
                            die("illegal non-ASCII hex digit in \\u escape")
                        }
                        j++
                    }
                    var value = 0
                    try {
                        value = oldStr.substring(i, i + j).toInt(16)
                    } catch (nfe: NumberFormatException) {
                        die("invalid hex value for \\u escape")
                    }
                    newstr.append(Character.toChars(value))
                    i += j - 1
                }
                'U' -> {
                    if (i + 8 > oldStr.length) {
                        die("string too short for \\U escape")
                    }
                    i++
                    var j: Int
                    j = 0
                    while (j < 8) {

                        /* this also handles the surrogate issue */if (oldStr[i + j]
                                .toInt() > 127
                        ) {
                            die("illegal non-ASCII hex digit in \\U escape")
                        }
                        j++
                    }
                    var value = 0
                    try {
                        value = oldStr.substring(i, i + j).toInt(16)
                    } catch (nfe: NumberFormatException) {
                        die("invalid hex value for \\U escape")
                    }
                    newstr.append(Character.toChars(value))
                    i += j - 1
                }
                else -> {
                    newstr.append('\\')
                    newstr.append(Character.toChars(cp))
                }
            }
            saw_backslash = false
            i++
        }

        /* weird to leave one at the end */if (saw_backslash) {
            newstr.append('\\')
        }
        return newstr.toString()
    }

    private fun die(foa: String) {
        throw IllegalArgumentException(foa)
    }
}

val Any.TAG: String
    get() = this::class.java.simpleName

//fun InputStream.skipWithAdditionalActionInLoop(numBytes: Long, action: () -> Unit): Long {
//    if (numBytes <= 0) {
//        return 0
//    }
//    var n = numBytes.toInt()
//    val bufLen = min(2048, n)
//    val data = ByteArray(bufLen)
//    while (n > 0) {
//        action.invoke()
//
//        val r = read(data, 0, min(bufLen, n))
//        if (r < 0) {
//            break
//        }
//        n -= r
//    }
//    return numBytes - n
//}

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

fun Picasso.smartLoad(url: String?, imageView: ImageView, applyConfig: ((requestCreator: RequestCreator) -> Unit)? = null) {
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

fun Picasso.smartLoad(url: String?, target: Target, applyConfig: ((requestCreator: RequestCreator) -> Unit)? = null) {
    var requestCreator = loadCompat(url)
    applyConfig?.invoke(requestCreator)

    requestCreator.networkPolicy(NetworkPolicy.OFFLINE)
        .into(object : Target {
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                target.onPrepareLoad(placeHolderDrawable)
            }

            override fun onBitmapFailed(e: java.lang.Exception?, errorDrawable: Drawable?) {
                requestCreator = Picasso.get().loadCompat(url)
                applyConfig?.invoke(requestCreator)

                requestCreator.networkPolicy(NetworkPolicy.NO_CACHE)
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .into(object : Target {
                        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}

                        override fun onBitmapFailed(e: java.lang.Exception?, errorDrawable: Drawable?) {
                            target.onBitmapFailed(e, errorDrawable)
                        }

                        override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                            target.onBitmapLoaded(bitmap, from)
                        }
                    })
            }

            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                target.onBitmapLoaded(bitmap, from)
            }
        })
}