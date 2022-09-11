package com.mgt.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.di.DI.boundExecutorService
import com.mgt.downloader.di.DI.database
import com.mgt.downloader.di.DI.downloadConfig
import com.mgt.downloader.di.DI.extractorProvider
import com.mgt.downloader.di.DI.liveConnection
import com.mgt.downloader.di.DI.statistics
import com.mgt.downloader.di.DI.unboundExecutorService
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.LongObject
import com.mgt.downloader.helper.StopThreadThrowable
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.nonserialize_model.ZipNode
import com.mgt.downloader.rxjava.*
import com.mgt.downloader.rxjava.Observable
import com.mgt.downloader.serialize_model.DownloadTask
import com.mgt.downloader.ui.MainActivity
import com.mgt.downloader.utils.*
import java.io.*
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Date
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import kotlin.math.max
import kotlin.math.min


class DownloadService : Service(), HasDisposable {
    //allow bind to this service
    private val binder = DownloadBinder()

    override val compositeDisposable = CompositeDisposable()
    private var activeDownloadTaskObservables = ConcurrentHashMap<String, Observable>()

    //single access point to all tasks, ui can observe and update ui when this value change
    //use HashMap for quick access and update download task 
    val liveDownloadTasks = MutableLiveData<HashMap<String, DownloadTask>>(HashMap())

    override fun onCreate() {
        logD(TAG, "onCreate")
        super.onCreate()

        // get all download tasks from database
//        executorService.submit<List<DownloadTask>>(GetDownloadTasksObserver()) {
//            IDMApplication.database.downloadTaskDao().getAllTasks()
//        }

//        Single.fromCallable {
//            IDMApplication.database.downloadTaskDao().getAllTasks()
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(
//                GetDownloadTasksObserver()
//            )

        SingleObservable.fromCallable(unboundExecutorService) {
            database.downloadTaskDao().getAllTasks()
        }.subscribe(GetDownloadTasksObserver())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //component can push message when start service
        val message = intent?.getStringExtra(Constants.MESSAGE)
        if (message != null) {
            when (message) {
                //start service as foreground, usually when app killed and there are some download in progress
                Constants.START_FOREGROUND_SERVICE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        createNotificationChannel()
                    }

                    val notificationIntent = Intent(this, MainActivity::class.java).apply {
                        putExtra(Constants.MESSAGE, Constants.OPEN_DOWNLOAD_LIST)
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_ONE_SHOT/*PendingIntent.FLAG_UPDATE_CURRENT*/ or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher_foreground)
                        .setContentTitle(getString(R.string.desc_foreground_title))
                        .setContentText(getString(R.string.desc_foreground_content))
                        .setContentIntent(pendingIntent).build()

                    startForeground(Constants.FOREGROUND_ID, notification)
                }
            }
        } else {
            //stop foreground if needed when no message found
            stopForeground(true)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    //helper function to create notification channel, use when start service in foreground
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val chan = NotificationChannel(
            Constants.CHANNEL_ID,
            Constants.CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        )
        chan.lightColor = getColor(R.color.lightPrimary)
        chan.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
    }

    //allow binding
    override fun onBind(intent: Intent): IBinder? {
        logD(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        logD(TAG, "onDestroy")
        //dispose all observers before destroyed
        this.compositeDisposable.clear()
    }

    //pause a running download task
    fun pauseDownloadTask(fileName: String) {
        //set this download task state to STATE_TEMPORARY_PAUSE so that when downloading, worker will check and pause it
        //also notify observers
        liveDownloadTasks.value = liveDownloadTasks.value?.apply {
            get(fileName)?.state = DownloadTask.STATE_TEMPORARY_PAUSE
        }
    }

    fun onReconnect() {
        val tasksToResume = (liveDownloadTasks.value ?: return).values.filter {
            it.state == DownloadTask.STATE_DOWNLOADING
        }.sortedWith(compareBy({ it.startTime }, { it.fileName }))

        val tasksToResumeFirst = tasksToResume.take(downloadConfig.maxConcurDownloadNum)
        val tasksToResumeLater = tasksToResume.drop(downloadConfig.maxConcurDownloadNum)

        tasksToResumeFirst.forEach { resumeDownloadTask(it.fileName) }
        Handler(Looper.getMainLooper()).postDelayed(
            { tasksToResumeLater.forEach { resumeDownloadTask(it.fileName) } },
            1000
        )
    }

    //save all tasks to db with paused state, clear download tasks list
    fun onDisconnect() {
        val tasksToSave = activeDownloadTaskObservables.keys.mapNotNull {
            liveDownloadTasks.value?.get(it)?.copy()
                ?.apply { state = DownloadTask.STATE_PERSISTENT_PAUSED }
        }

        CompletableObservable.fromCallable(unboundExecutorService) {
            database.downloadTaskDao()
                .insertDownloadTasks(tasksToSave)
        }.subscribe(InsertDownloadTasksObserver(tasksToSave))

        activeDownloadTaskObservables.clear()

        App.resetDownloadExecutorService()
    }

    //resume a paused download task
    fun resumeDownloadTask(fileName: String) {
        //set this download task state to STATE_DOWNLOADING so that when downloading, worker will check and resume it instantly
        //also notify observers
        liveDownloadTasks.value = liveDownloadTasks.value?.apply {
            get(fileName)?.state = DownloadTask.STATE_DOWNLOADING
        }

        //if no thread for this task is running...
        if (!activeDownloadTaskObservables.keys.any { it == fileName } && liveConnection.isConnected) {
            //load download task from database and resume slowly (open connection again, additional time for skipping to leaved point)
            //handle result with a GetDownloadTaskObserver
//            Single.fromCallable {
//                IDMApplication.database.downloadTaskDao().getDownloadTask(fileName)
//            }
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    GetDownloadTaskObserver(fileName) { downloadTask ->
//                        executeDownloadTask(downloadTask, true)
//                    }
//                )
            SingleObservable.fromCallable(unboundExecutorService) {
                database.downloadTaskDao().getDownloadTask(fileName)
            }.subscribe(
                GetDownloadTaskObserver(fileName) { downloadTask ->
                    executeDownloadTask(downloadTask, true)
                }
            )
        }
    }

    //cancel a pause or running download task
    fun cancelDownloadTask(fileName: String) {
        liveDownloadTasks.value?.get(fileName)?.let { taskToBeCanceled ->

            //Set this download task state to STATE_CANCEL_OR_FAIL so that when in STATE_TEMPORARY_PAUSE,
            //worker will check and cancel it
            //Also notify observers
            taskToBeCanceled.state = DownloadTask.STATE_CANCEL_OR_FAIL
            liveDownloadTasks.value = liveDownloadTasks.value

            //insert or update download task in database
//        Completable.fromCallable {
//            IDMApplication.database.downloadTaskDao().insertDownloadTask(taskToBeCanceled)
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(InsertDownloadTaskObserver(taskToBeCanceled))
            CompletableObservable.fromCallable(unboundExecutorService) {
                database.downloadTaskDao().insertDownloadTask(taskToBeCanceled)
            }.subscribe(InsertDownloadTaskObserver(taskToBeCanceled))
        }
    }

    //retry a canceled or fail download task
    fun retryDownloadTask(taskName: String) {
        liveDownloadTasks.value?.get(taskName)?.let { downloadTask ->

            // downloadUrl may be expired so regenerate it
            if (downloadTask.displayUrl != downloadTask.downloadUrl) {
                liveDownloadTasks.value = liveDownloadTasks.value?.apply {
                    put(
                        downloadTask.fileName,
                        downloadTask.apply { state = DownloadTask.STATE_DOWNLOADING })
                }

                getDownloadUrl(downloadTask.displayUrl) { downloadUrl ->
                    retryDownloadTaskInternal(downloadTask.apply { this.downloadUrl = downloadUrl })
                }
            } else {
                retryDownloadTaskInternal(downloadTask)
            }
        }
    }

    private fun retryDownloadTaskInternal(downloadTask: DownloadTask) {
        executeDownloadTask(
            downloadTask.copy(
                startTime = Date(System.currentTimeMillis()),
                downloadedSize = 0,
                state = DownloadTask.STATE_DOWNLOADING,
                elapsedTime = 0
            ).apply {
                if (downloadTask.partsDownloadedSize.isNotEmpty()) {
                    if (utils.isDownloadedFileExist(downloadTask)) {
                        downloadedSize = downloadTask.downloadedSize
                        partsDownloadedSize = downloadTask.partsDownloadedSize
                    } else {
                        partsDownloadedSize =
                            utils.getBlankArrayList(downloadTask.partsDownloadedSize.size)
                    }
                }
            },
            true
        )
    }

    private fun getDownloadUrl(url: String, onComplete: (downloadUrl: String) -> Unit) {
        extractorProvider.provideExtractor(this, url)
            .extract(url, object : SingleObserver<FilePreviewInfo>(this) {
                override fun onSuccess(result: FilePreviewInfo) {
                    super.onSuccess(result)
                    onComplete(result.downloadUri)
                }
            })
    }

    //delete a downloaded, canceled or fail task
    fun deleteDownloadTaskFromList(
        fileName: String,
        callback: ((isSuccess: Boolean) -> Unit)? = null
    ) {
        //Delete download task from liveData
        //Also notify observers
        liveDownloadTasks.value = liveDownloadTasks.value?.apply {
            remove(fileName)
        }

        //Delete download task from database
//        Completable.fromCallable {
//            IDMApplication.database.downloadTaskDao().deleteDownloadTask(fileName)
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(DeleteDownloadTaskObserver(fileName, callback))
        CompletableObservable.fromCallable(unboundExecutorService) {
            database.downloadTaskDao().deleteDownloadTask(fileName)
        }.subscribe(DeleteDownloadTaskObserver(fileName, callback))
    }

    //delete a downloaded, canceled or fail task. Return true if success
    fun deleteFileOrDirFromStorage(
        fileName: String,
        callback: ((isSuccess: Boolean) -> Unit)? = null
    ) {
        CompletableObservable.fromCallable(unboundExecutorService) {
            utils.deleteFileOrDir(this, fileName)
        }.subscribe(DeleteFileOrDirObserver(fileName, callback))
    }

    //start or resume a download task, every download must go through this function
    fun executeDownloadTask(downloadTask: DownloadTask, isResume: Boolean) {
        //add download task to list, set state = STATE_DOWNLOADING
        //also notify observers
        liveDownloadTasks.value = liveDownloadTasks.value?.apply {
            put(
                downloadTask.fileName,
                downloadTask.apply { state = DownloadTask.STATE_DOWNLOADING })
        }

        //execute download task in background with a ExecuteDownloadTaskObserver to observe events
//        Flowable.create<null>({emitter ->
//            executeFileDownloadTask(downloadTask, emitter, isResume)
//        },BackpressureStrategy.LATEST)
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribeWith(ExecuteDownloadTaskObserver(downloadTask.fileName))
        //save download task to database
        val taskToSave = downloadTask.copy().apply { state = DownloadTask.STATE_PERSISTENT_PAUSED }

//        Completable.fromCallable {
//            IDMApplication.database.downloadTaskDao().insertDownloadTask(taskToSave)
//        }.subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(InsertDownloadTaskObserver(taskToSave))

        CompletableObservable.fromCallable(unboundExecutorService) {
            database.downloadTaskDao().insertDownloadTask(taskToSave)
        }.subscribe(InsertDownloadTaskObserver(taskToSave))

        StreamObservable.create<DownloadTask>(boundExecutorService) { emitter ->
            when {
                downloadTask.zipEntryName != null -> // case: download a part of zip
                    executeZipPartDownloadTask(downloadTask, emitter, isResume)
                downloadTask.partsDownloadedSize.isNotEmpty() -> executeMultiThreadFileDownloadTask(
                    downloadTask,
                    emitter,
                    downloadTask.partsDownloadedSize.size
                )
                else -> executeFileDownloadTask(downloadTask, emitter, isResume)
            }
        }.also {
            // add to list of active download tasks
            activeDownloadTaskObservables[downloadTask.fileName] = it
        }.subscribe(ExecuteDownloadTaskObserver(downloadTask.fileName))

//        Observable.create<null> { emitter ->
//            // case: download a part of zip
//            if (downloadTask.zipEntryName != null) {
//                executeZipPartDownloadTask(downloadTask, emitter, isResume)
//            } else {
//                executeFileDownloadTask(downloadTask, emitter, isResume)
//            }
//        }.subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(ExecuteDownloadTaskObserver(downloadTask.fileName))
    }

    //internal func to start or resume a download task
    private fun executeFileDownloadTask(
        downloadTask: DownloadTask,
        emitter: StreamEmitter<DownloadTask>,
        isResume: Boolean
    ) {
        logD(TAG, "executeFileDownloadTask: $downloadTask")

        var input: InputStream? = null
        var output: OutputStream? = null
        var connection: HttpURLConnection? = null
        try {
            //resume case
            val file = utils.getDownloadFile(downloadTask.fileName)
            if (isResume && file.exists() && file.isFile
                && (file.length() < downloadTask.totalSize || downloadTask.totalSize == -1L)
            ) {
                downloadTask.downloadedSize = file.length()
                emitter.onNext(null)
            }

            connection = utils.openConnection(downloadTask.downloadUrl, downloadTask.downloadedSize)

            logD(TAG, "readTimeout: ${connection.readTimeout}")

            var prevTimePoint = System.currentTimeMillis()

            if (connection.responseCode != Constants.HTTP_RANGE_NOT_SATISFIABLE) {
                input = connection.inputStream
                output = FileOutputStream(file, isResume)
                val data = ByteArray(4096)

//            if server not support partial content, skip to downloadedSize
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    //if responseCode is HTTP_OK, contentLength is the whole file size
//                    downloadTask.totalSize = connection.contentLength.toLong()
                    emitter.onNext(null)

                    logD(TAG, "fileLength: ${downloadTask.totalSize}, task: $downloadTask")

                    //skip to downloadedSize, while skipping check if user press pause and handle
                    skipDownloadedBytes(input, downloadTask, emitter)
                }

                //helper lambda functions
                val getProgress =
                    if (downloadTask.isFileSizeKnown()) {
                        {
                            utils.getPercentage(
                                downloadTask.downloadedSize,
                                downloadTask.totalSize
                            ).toLong()
                        }
                    } else {
                        { downloadTask.downloadedSize }
                    }

                val isProgressChangeSignificantly = if (downloadTask.isFileSizeKnown()) {
                    { prevProgress: Long, curProgress: Long -> prevProgress != curProgress }
                } else {
                    { prevSize: Long, curSize: Long ->
                        utils.isSizeChangeSignificantly(
                            prevSize,
                            curSize
                        )
                    }
                }

                //
                var prevProgress = getProgress()
                var curProgress: Long

                var count = input.read(data)
                while (count != -1) {
                    output.write(data, 0, count)

                    //for statistics
                    statistics.increaseTotalDownloadSize(count)

                    downloadTask.downloadedSize += count

                    downloadTask.elapsedTime += System.currentTimeMillis() - prevTimePoint

                    curProgress = getProgress()

                    //dispatch update only when percentage or file size format string change
                    if (isProgressChangeSignificantly(prevProgress, curProgress)) {
                        emitter.onNext(null)
                        prevProgress = curProgress
                    }

                    //before continue next loop, check if user press pause and handle
                    checkForInterruptSignal(
                        emitter,
                        downloadTask
                    )

                    prevTimePoint = System.currentTimeMillis()

                    try {
                        count = input.read(data)
                    } catch (e: Throwable) {
                        recordNonFatalException(e)
                        return
                    }
                }
            }
            if (downloadTask.totalSize == -1L) {
                downloadTask.totalSize = downloadTask.downloadedSize
            }
            setImageDownloadTaskThumb(downloadTask)
            downloadTask.state = DownloadTask.STATE_SUCCESS
            emitter.onComplete()
            database.downloadTaskDao().insertDownloadTask(downloadTask)
        } catch (e: StopThreadThrowable) {
            return
        } catch (e: Throwable) {
            downloadTask.state = DownloadTask.STATE_CANCEL_OR_FAIL
            emitter.onError(e)
            database.downloadTaskDao().insertDownloadTask(downloadTask)
        } finally {
            logD(TAG, "finally block")

            //close all resources
            output?.close()
            input?.close()
            connection?.disconnect()
        }
    }

    private fun skipDownloadedBytes(
        inputStream: InputStream,
        downloadTask: DownloadTask,
        emitter: StreamEmitter<DownloadTask>,
        threadIndex: Int = 0
    ): Long {
        var prevTime = System.currentTimeMillis()

        val numBytes = downloadTask.downloadedSize
        if (numBytes <= 0) {
            return 0
        }
        var n = numBytes.toInt()
        val bufLen = min(2048, n)
        val data = ByteArray(bufLen)
        while (n > 0) {
            downloadTask.elapsedTime += System.currentTimeMillis() - prevTime
            checkForInterruptSignal(
                emitter,
                downloadTask,
                threadIndex
            )
            prevTime = System.currentTimeMillis()

            val r = inputStream.read(data, 0, min(bufLen, n))
            if (r < 0) {
                break
            }
            n -= r
        }

        downloadTask.elapsedTime += System.currentTimeMillis() - prevTime

        return numBytes - n
    }

    private fun executeZipPartDownloadTask(
        downloadTask: DownloadTask,
        emitter: StreamEmitter<DownloadTask>,
        isResume: Boolean
    ) {
        val rootNode = ZipNode.getZipTree(downloadTask.displayUrl, downloadTask.downloadUrl)
        val nodeToDownload = rootNode.getNode(downloadTask.zipEntryName ?: return)

//        downloadTask.totalSize = nodeToDownload.size

        try {
            val downloadDirPath = utils.getDownloadDirPath()
                ?: throw RuntimeException("Can not get download dir path")

            val isConnectionLost = executeZipPartDownloadTaskInternal(
                downloadTask,
                emitter,
                isResume,
                nodeToDownload,
                downloadTask.downloadedSize,
                downloadDirPath
            )
            if (isConnectionLost) {
                return
            }
            if (downloadTask.totalSize == -1L) {
                downloadTask.totalSize = downloadTask.downloadedSize
            }
            downloadTask.state = DownloadTask.STATE_SUCCESS
            emitter.onComplete()
            database.downloadTaskDao().insertDownloadTask(downloadTask)
        } catch (throwable: Throwable) {
            downloadTask.state = DownloadTask.STATE_CANCEL_OR_FAIL
            emitter.onError(throwable)
            database.downloadTaskDao().insertDownloadTask(downloadTask)
        }
    }

    //internal func to start or resume a download task
    private fun executeZipPartDownloadTaskInternal(
        downloadTask: DownloadTask,
        emitter: StreamEmitter<DownloadTask>,
        isResume: Boolean,
        zipNode: ZipNode,
        curDownloadedBytes: Long,
        parentPath: String
    ): Boolean {// return true if connection lost while downloading this zipNode or any of its child, false else
        if (zipNode.size <= curDownloadedBytes) {
            return false
        } else {
            // if top level dir, use downloadTask.fileName
            val fileName = if (parentPath == utils.getDownloadDirPath())
                downloadTask.fileName
            else utils.getFileName(
                zipNode.entry?.name.orEmpty()
            )

            if (zipNode.entry?.isDirectory == true) {
                val curDirPath =
                    "$parentPath${File.separator}$fileName"

                var newCurSize = curDownloadedBytes
                zipNode.childNodes.forEach {
                    val isConnectionLost = executeZipPartDownloadTaskInternal(
                        downloadTask,
                        emitter,
                        isResume,
                        it,
                        newCurSize,
                        curDirPath
                    )
                    if (isConnectionLost) {
                        return true
                    }
                    newCurSize = max(newCurSize - it.size, 0)
                }
            } else {
                val localHeaderRelativeOffsetExtra =
                    utils.getExtraBytes(
                        zipNode.entry?.extra ?: ByteArray(0),
                        Constants.RELATIVE_OFFSET_LOCAL_HEADER
                    )
                val localHeaderRelativeOffset = ByteBuffer
                    .wrap(localHeaderRelativeOffsetExtra)
                    .order(ByteOrder.LITTLE_ENDIAN).int

                var connection: HttpURLConnection? = null
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                try {
                    var prevTime = System.currentTimeMillis()
//                    //resume case
                    var downloadedSize = 0L
                    val curFile =
                        File("${parentPath}${File.separator}${utils.getFileName(zipNode.entry?.name.orEmpty())}")
                    if (isResume && curFile.exists() && curFile.isFile) {
                        downloadedSize = curFile.length()
                        downloadTask.downloadedSize += downloadedSize - curDownloadedBytes
                        if (curFile.length() == zipNode.size) {
                            return false
                        }
                    }

                    connection = utils.openConnection(
                        downloadTask.downloadUrl,
                        localHeaderRelativeOffset.toLong()
                    )

                    inputStream = connection.inputStream

                    val data = ByteArray(4096)
                    var count = inputStream.read(data, 0, 30)
                    if (count != 30) {
                        throw Throwable("Cannot even read 30 bytes !")
                    }

                    val wrapped = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                    if (!utils.isHeader(data.copyOfRange(0, 4), Constants.LOCAL_FILE_HEADER)) {
                        throw Throwable("Not a local file header")
                    }

                    val fileNameLength = wrapped.getShort(26)
                    val extraFieldLength = wrapped.getShort(28)

                    // skip header
                    inputStream =
                        inputStream.apply { skip((fileNameLength + extraFieldLength).toLong()) }
                            .let {
                                // if use DEFLATED compression
                                if (zipNode.entry?.method == ZipEntry.DEFLATED)
                                    InflaterInputStream(inputStream, Inflater(true))
                                else
                                    it
                            } //skip to downloadedSize, while skipping check if user press pause and handle
                            .also {
                                downloadTask.elapsedTime += System.currentTimeMillis() - prevTime
                                skipDownloadedBytes(
                                    it,
                                    downloadTask,
                                    emitter
                                )
                                prevTime = System.currentTimeMillis()
                            }

                    // create parent folders if not exist
                    val parentDir = File(parentPath)
                    if (!parentDir.exists() && !parentDir.mkdirs()) {
                        emitter.onError(Throwable("Fail to create directory $parentDir"))
                    }

                    outputStream =
                        FileOutputStream(
                            "$parentPath${File.separator}$fileName",
                            isResume
                        )

                    //helper lambda functions
                    val getProgress =
                        if (downloadTask.isFileSizeKnown()) {
                            {
                                utils.getPercentage(
                                    downloadTask.downloadedSize,
                                    downloadTask.totalSize
                                ).toLong()
                            }
                        } else {
                            { downloadTask.downloadedSize }
                        }

                    val isProgressChangeSignificantly = if (downloadTask.isFileSizeKnown()) {
                        { prevProgress: Long, curProgress: Long -> prevProgress != curProgress }
                    } else {
                        { prevSize: Long, curSize: Long ->
                            utils.isSizeChangeSignificantly(
                                prevSize,
                                curSize
                            )
                        }
                    }

                    //

                    count = inputStream.read(data)
                    while (downloadedSize < (zipNode.entry?.size ?: 0L) && count != -1) {
                        outputStream.write(data, 0, count)

                        val prevProgress = getProgress()

                        //for statistics
                        statistics.increaseTotalDownloadSize(count)

                        downloadedSize += count
                        downloadTask.downloadedSize += count

                        downloadTask.elapsedTime += System.currentTimeMillis() - prevTime

                        val curProgress = getProgress()

                        //dispatch update only when percentage or file size format string change
                        if (isProgressChangeSignificantly(prevProgress, curProgress)) {
                            emitter.onNext(null)
                        }

                        //before continue next loop, check if user press pause and handle
                        checkForInterruptSignal(
                            emitter,
                            downloadTask
                        )

                        prevTime = System.currentTimeMillis()

                        try {
                            count = inputStream.read(data)
                        } catch (t: Throwable) {
                            return true
                        }
                    }
                } catch (t: StopThreadThrowable) {
                    return true
                } finally {
                    logD(TAG, "inner finally block")

                    inputStream?.close()
                    outputStream?.close()
                    connection?.disconnect()
                }
            }
            return false
        }
    }

    //internal func to start or resume a download task
    private fun executeMultiThreadFileDownloadTask(
        downloadTask: DownloadTask,
        emitter: StreamEmitter<DownloadTask>,
        threadNum: Int
    ) {
        val lessWorkDownloadSize = downloadTask.totalSize / threadNum
        val moreWorkDownloadSize = lessWorkDownloadSize + 1

        val lessWorkThreadNum = (moreWorkDownloadSize * threadNum - downloadTask.totalSize).toInt()
        val moreWorkThreadNum = threadNum - lessWorkThreadNum

        logD(TAG, "lesswork: $lessWorkThreadNum, $lessWorkDownloadSize")
        logD(TAG, "morework: $moreWorkThreadNum, $moreWorkDownloadSize")

        var numThreadComplete = 0
        var overallDownloadState = Constants.DOWNLOAD_STATE_SUCCESS

        val lock = Any()

        //helper func
        val onThreadComplete = { downloadState: Int ->
            synchronized(lock) {
                if (downloadState != Constants.DOWNLOAD_STATE_SUCCESS) {
                    overallDownloadState = downloadState
                }
                numThreadComplete++
            }
        }
        //

        val lastDownloadedSize =
            LongObject(downloadTask.downloadedSize)

        for (threadIndex in 0 until moreWorkThreadNum) {
            CompletableObservable.fromCallable(unboundExecutorService) {
                val startPosition = threadIndex * moreWorkDownloadSize
                executeMultiThreadFileDownloadTaskInternal(
                    downloadTask,
                    emitter,
                    threadIndex,
                    startPosition,
                    moreWorkDownloadSize,
                    lastDownloadedSize,
                    onThreadComplete
                )
            }
        }
        for (threadIndex in 0 until lessWorkThreadNum) {
            CompletableObservable.fromCallable(unboundExecutorService) {
                val startPosition =
                    moreWorkThreadNum * moreWorkDownloadSize + threadIndex * lessWorkDownloadSize
                executeMultiThreadFileDownloadTaskInternal(
                    downloadTask,
                    emitter,
                    moreWorkThreadNum + threadIndex,
                    startPosition,
                    lessWorkDownloadSize,
                    lastDownloadedSize,
                    onThreadComplete
                )
            }
        }

        val lock2 = Any()

        var prevTime = System.currentTimeMillis()
        var curTime: Long

        while (numThreadComplete < threadNum && overallDownloadState == Constants.DOWNLOAD_STATE_SUCCESS) {
            synchronized(lock2) {
                /*invalidate cache*/
                if (downloadTask.state == DownloadTask.STATE_DOWNLOADING) {
                    curTime = System.currentTimeMillis()
                    if (curTime - prevTime > Constants.ONE_SECOND_IN_MILLISECOND) {
                        downloadTask.elapsedTime += curTime - prevTime
                        prevTime = curTime
                    }
                } else {
                    prevTime = System.currentTimeMillis()
                }
            }
            // flush cache to main
        }

        if (overallDownloadState != Constants.DOWNLOAD_STATE_INTERRUPT) {
            if (overallDownloadState == Constants.DOWNLOAD_STATE_SUCCESS) {
                if (downloadTask.totalSize == -1L) {
                    downloadTask.totalSize = downloadTask.downloadedSize
                }

                setImageDownloadTaskThumb(downloadTask)

                downloadTask.state = DownloadTask.STATE_SUCCESS
                emitter.onComplete()
            } else {
                downloadTask.state = DownloadTask.STATE_CANCEL_OR_FAIL
                emitter.onError(Throwable("download fail"))
            }

            database.downloadTaskDao().insertDownloadTask(downloadTask)
        }
    }

    private fun setImageDownloadTaskThumb(downloadTask: DownloadTask) {
        val fileExtension = utils.getFileExtension(downloadTask.fileName)
        if (fileExtension == "png" || fileExtension == "jpg" || fileExtension == "gif" || fileExtension == "jpeg") {
            downloadTask.thumbUrl = utils.getDownloadFilePath(downloadTask.fileName)
        }
    }

    //internal func to start or resume a download task
    private fun executeMultiThreadFileDownloadTaskInternal(
        downloadTask: DownloadTask,
        emitter: StreamEmitter<DownloadTask>,
        threadIndex: Int,
        startPosition: Long,
        sizeToDownload: Long,
        lastDownloadedSize: LongObject,
        onComplete: (downloadState: Int) -> Any?
    ) {
        logD(
            TAG,
            "executeMultiThreadFileDownloadTaskInternal: threadIndex $threadIndex $downloadTask"
        )

        var input: InputStream? = null
        var output: RandomAccessFile? = null
        var connection: HttpURLConnection? = null
        try {
            val continuePosition =
                startPosition + downloadTask.partsDownloadedSize[threadIndex]

            connection = utils.openConnection(downloadTask.downloadUrl, continuePosition)

            if (connection.responseCode != Constants.HTTP_RANGE_NOT_SATISFIABLE) {
                input = connection.inputStream
                output = RandomAccessFile(utils.getDownloadFilePath(downloadTask.fileName), "rw")
                output.seek(continuePosition)
                val data = ByteArray(4096)

                var count = input.read(data)
                while (count != -1 && downloadTask.partsDownloadedSize[threadIndex] < sizeToDownload) {
                    output.write(data, 0, count)

                    //for statistics
                    statistics.increaseTotalDownloadSize(count)

                    downloadTask.increaseDownloadedSize(count)

                    //dispatch update only when percentage or file size format string change
                    synchronized(lastDownloadedSize) {
                        val lastPublishedProgress = utils.getPercentage(
                            lastDownloadedSize.value,
                            downloadTask.totalSize
                        ).toLong()

                        val curProgress = utils.getPercentage(
                            downloadTask.downloadedSize,
                            downloadTask.totalSize
                        ).toLong()

                        if (lastPublishedProgress != curProgress) {
                            logD(
                                TAG,
                                "thread $threadIndex: onNext, last: $lastPublishedProgress, cur:$curProgress; $downloadTask"
                            )
                            emitter.onNext(downloadTask)
                            lastDownloadedSize.value = downloadTask.downloadedSize
                        }
                    }

                    downloadTask.partsDownloadedSize.apply {
                        set(
                            threadIndex,
                            get(threadIndex) + count
                        )
                    }

                    //before continue next loop, check if user press pause and handle
                    checkForInterruptSignal(
                        emitter,
                        downloadTask,
                        threadIndex
                    )

                    try {
                        count = input.read(data)
                    } catch (e: Throwable) {
                        recordNonFatalException(e)
                        return
                    }
                }
            }
            onComplete(Constants.DOWNLOAD_STATE_SUCCESS)
        } catch (e: StopThreadThrowable) {
            logD(TAG, "threadIndex $threadIndex: StopThreadThowable")
            onComplete(Constants.DOWNLOAD_STATE_INTERRUPT)
        } catch (e: Throwable) {
            logD(TAG, "thread $threadIndex error")
            recordNonFatalException(e)
            onComplete(Constants.DOWNLOAD_STATE_CANCEL_OR_FAIL)
        } finally {
            logD(TAG, "thread $threadIndex finally block: $downloadTask")

            //close all resources
            output?.close()
            input?.close()
            connection?.disconnect()
        }
    }

    /*
        private fun executeZipPartDownloadTask(
            downloadTask: DownloadTask,
            emitter: StreamEmitter<Any>,
            isResume: Boolean
        ) {
            val rootNode = ZipNode.getZipTree(downloadTask.url)
            val nodeToDownload = rootNode.getNode(downloadTask.zipEntryName!!)

            downloadTask.downloadedSize = 0
            downloadTask.totalSize = nodeToDownload.size

            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                val pq = PriorityQueue<Pair<Int, ZipNode>>(20) { pair1, pair2 ->
                    pair1.first.compareTo(pair2.first)
                }

                buildDownloadQueue(downloadTask, nodeToDownload, downloadTask.downloadedSize, pq)

                var curNode: ZipNode
                var localOffset = 0
                var queuePair: Pair<Int, ZipNode>
                var consumedSize = 0
                var streamOffset=0L

                while (pq.isNotEmpty()) {
                    queuePair = pq.poll()

                    val actualSkipped =
                        inputStream?.skip(queuePair.first.toLong() - (localOffset + consumedSize))
                    streamOffset+=actualSkipped?:0L

                    if (actualSkipped != null && actualSkipped != queuePair.first.toLong() - (localOffset + consumedSize)) {
                        throw Throwable("cannot skip even ${queuePair.first.toLong() - (localOffset + consumedSize)} bytes !")
                    }

                    localOffset = queuePair.first
                    curNode = queuePair.second

                    if (inputStream == null) {
                        connection = Utils.openConnection(
                            downloadTask.url,
                            localOffset.toLong()
                        )

                        inputStream = connection.inputStream!!

                        streamOffset=localOffset.toLong()
                    }

                    consumedSize = executeZipPartDownloadTaskInternal(
                        downloadTask,
                        emitter,
                        isResume,
                        curNode,
                        inputStream
                    )

                    streamOffset+=consumedSize
                }

                downloadTask.state = DownloadTask.STATE_SUCCESS
                emitter.onComplete()
            } catch (t: Throwable) {
                downloadTask.state = DownloadTask.STATE_CANCEL_OR_FAIL
                emitter.onError(throwable)
            } finally {
                logD(TAG, "outer finally block")

                inputStream?.close()
                connection?.disconnect()

                //save download task to database
                IDMApplication.database.downloadTaskDao().insertDownloadTask(downloadTask)
            }
        }

        private fun buildDownloadQueue(
            downloadTask: DownloadTask,
            zipNode: ZipNode,
            curDownloadedBytes: Long,
            pq: PriorityQueue<Pair<Int, ZipNode>>
        ) {
            if (zipNode.size <= curDownloadedBytes) {
                downloadTask.downloadedSize += zipNode.size
            } else {
                if (zipNode.entry!!.isDirectory) {
                    var newCurSize = curDownloadedBytes
                    zipNode.childNodes.forEach {
                        buildDownloadQueue(
                            downloadTask,
                            it,
                            newCurSize,
                            pq
                        )
                        newCurSize = max(newCurSize - it.size, 0)
                    }
                } else {
                    val localHeaderRelativeOffsetExtra =
                        Utils.getExtraBytes(
                            zipNode.entry!!.extra,
                            Constants.RELATIVE_OFFSET_LOCAL_HEADER
                        )
                    val localHeaderRelativeOffset = ByteBuffer
                        .wrap(localHeaderRelativeOffsetExtra)
                        .order(ByteOrder.LITTLE_ENDIAN).int

                    pq.offer(Pair(localHeaderRelativeOffset, zipNode))
                }
            }
        }

        //internal func to start or resume a download task
        private fun executeZipPartDownloadTaskInternal(
            downloadTask: DownloadTask,
            emitter: StreamEmitter<Any>,
            isResume: Boolean,
            zipNode: ZipNode,
            iStream: InputStream,
            parentPath: String = Utils.getDownloadDirPath()
        ): Int {
            var inputStream = iStream
            var outputStream: OutputStream? = null
            try {
    //          resume case
                var downloadedBytes = 0L
                val curFile =
                    File("${parentPath}${File.separator}${Utils.getFileName(zipNode.entry!!.name)}")
                if (isResume && curFile.exists() && curFile.isFile
                    && (curFile.length() < zipNode.size)
                ) {
                    downloadedBytes = curFile.length()
                    downloadTask.downloadedSize += downloadedBytes
                }

                var prevTimePoint = System.currentTimeMillis()

                val data = ByteArray(4096)

                var count = inputStream.read(data, 0, 30)
                if (count != 30) {
                    throw Throwable("Cannot even read 30 bytes !")
                }

                val wrapped = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                if (!Utils.isHeader(data.copyOfRange(0, 4), Constants.LOCAL_FILE_HEADER)) {
                    throw Throwable("Not a local file header")
                }

                val fileNameLength = wrapped.getShort(26)
                val extraFieldLength = wrapped.getShort(28)

                // skip header
                inputStream =
                    inputStream.apply {
                        if(skip((fileNameLength + extraFieldLength).toLong())!=(fileNameLength + extraFieldLength).toLong()){
                            throw Throwable("Cannot even skip ${fileNameLength + extraFieldLength} bytes !")
                        }
                    }
                        .let {
                            // if use DEFLATED compression
                            if (zipNode.entry!!.method == ZipEntry.DEFLATED)
                                InflaterInputStream(inputStream, Inflater(true))
                            else
                                inputStream
                        } //skip to downloadedSize, while skipping check if user press pause and handle
                        .apply {
                            skipWithAdditionalActionInLoop(
                                numBytes = downloadedBytes,
                                action = {
                                    checkForInterruptSignal(
                                        emitter,
                                        downloadTask,
                                        callWhileWaitingForResume = {
                                            prevTimePoint = System.currentTimeMillis()
                                        }
                                    )
                                }
                            )
                        }

                // create parent folders if not exist
                val parentDir = File(parentPath)
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    throw Throwable("Fail to create directory $parentDir")
                }

                // if top level dir, use downloadTask.fileName
                val fileName = if (parentPath == Utils.getDownloadDirPath())
                    downloadTask.fileName
                else Utils.getFileName(
                    zipNode.entry!!.name
                )

                outputStream =
                    FileOutputStream(
                        "$parentPath${File.separator}$fileName",
                        isResume
                    )

                count = inputStream.read(data)
                while (downloadedBytes < zipNode.entry!!.size && count != -1) {
                    downloadedBytes += count
                    outputStream.write(data, 0, count)

                    val prevProgress =
                        if (downloadTask.isFileSizeKnown())
                            Utils.getPercentage(
                                downloadTask.downloadedSize,
                                downloadTask.totalSize
                            ).toLong()
                        else
                            downloadTask.downloadedSize

                    downloadTask.downloadedSize += count

                    downloadTask.elapsedTime += System.currentTimeMillis() - prevTimePoint
                    prevTimePoint = System.currentTimeMillis()

                    val curProgress =
                        if (downloadTask.isFileSizeKnown())
                            Utils.getPercentage(
                                downloadTask.downloadedSize,
                                downloadTask.totalSize
                            ).toLong()
                        else
                            downloadTask.downloadedSize

                    //dispatch update only when percentage or file size format string change
                    if (Utils.getFormatFileSize(prevProgress) != Utils.getFormatFileSize(
                            curProgress
                        )
                    ) {
                        emitter.onNext(null)
                    }

                    //before continue next loop, check if user press pause and handle
                    checkForInterruptSignal(
                        emitter,
                        downloadTask,
                        //prevTimePoint = timing when user press resume (if pause happened)
                        callWhileWaitingForResume = {
                            prevTimePoint = System.currentTimeMillis()
                        }
                    )

                    count = when {
                        zipNode.entry!!.size - downloadedBytes == 0L -> -1
                        zipNode.entry!!.size - downloadedBytes < data.size -> inputStream.read(data, 0, (zipNode.entry!!.size - downloadedBytes).toInt())
                        else -> inputStream.read(data)
                    }
                }

                return 30 + fileNameLength + extraFieldLength + zipNode.entry!!.compressedSize.toInt()
            } finally {
                logD(TAG, "inner finally block")

                outputStream?.close()
            }
        }
        */
    private fun checkForInterruptSignal(
        emitter: StreamEmitter<DownloadTask>,
        downloadTask: DownloadTask,
        threadIndex: Int = 0
    ) {
        //if user press pause ...
        if (downloadTask.state == DownloadTask.STATE_TEMPORARY_PAUSE) {
            logD(TAG, "threadIndex $threadIndex: pause")
            synchronized(emitter) {
                logD(TAG, "threadIndex $threadIndex: synchronized")
                if (downloadTask.state == DownloadTask.STATE_TEMPORARY_PAUSE) {
                    //emit pause state
                    logD(TAG, "onNext pause")
                    emitter.onNext(downloadTask)

                    //copy this download task, set state to STATE_PERSISTENT_PAUSED, save to database
                    val taskToSave =
                        downloadTask.copy().apply { state = DownloadTask.STATE_PERSISTENT_PAUSED }

                    CompletableObservable.fromCallable(unboundExecutorService) {
                        database.downloadTaskDao().insertDownloadTask(taskToSave)
                    }.subscribe(InsertDownloadTaskObserver(taskToSave))

                    while (downloadTask.state == DownloadTask.STATE_TEMPORARY_PAUSE) {
                        if (getDownloadQueue().isNotEmpty()) {
                            downloadTask.state = DownloadTask.STATE_PERSISTENT_PAUSED
                            throw StopThreadThrowable()
                        }
                    }

                    //emit resume state
                    logD(TAG, "onNext resume")
                    emitter.onNext(downloadTask)
                }
            }
            logD(TAG, "threadIndex $threadIndex: resume")
        }

        if (downloadTask.state == DownloadTask.STATE_PERSISTENT_PAUSED) {
            throw StopThreadThrowable()
        } else if (downloadTask.state == DownloadTask.STATE_CANCEL_OR_FAIL) {
            throw Throwable("Task Canceled")
        }
    }

    private fun getDownloadQueue(): Queue<Runnable> {
        return boundExecutorService.queue
    }

    fun isAnyActiveDownloadTask(): Boolean {
        return activeDownloadTaskObservables.isNotEmpty()
    }

    fun isFileOrDownloadTaskExist(fileName: String): Boolean {
        return utils.getDownloadFile(fileName)
            .exists() || liveDownloadTasks.value?.values?.any { it.fileName == fileName } == true
    }

    fun getFileSizeOfDownloadedTask(url: String): Long {
        liveDownloadTasks.value?.values?.forEach {
            if (it.displayUrl == url) {
                return it.totalSize
            }
        }
        return -1
    }

    /**
     * Observers
     */

    //observer for reading all download tasks from database
    inner class GetDownloadTasksObserver :
        SingleObserver<List<DownloadTask>>(this) {
        override fun onSuccess(result: List<DownloadTask>) {
            logD(TAG, "onSuccess: $result")
            super.onSuccess(result)
            // transform result from List to HashMap
            liveDownloadTasks.value = HashMap<String, DownloadTask>().apply {
                result.forEach {
                    this[it.fileName] = it.apply {
                        if (state == DownloadTask.STATE_TEMPORARY_PAUSE) {
                            state = DownloadTask.STATE_PERSISTENT_PAUSED
                        }
                        utils.getFileOrDirSize(fileName).let { size ->
                            if (size != -1L) {
                                downloadedSize = size
                            }
                        }
                    }
                }
            }
        }

        override fun onError(t: Throwable) {
            logE(TAG, "onError")
            super.onError(t)
        }
    }

    //observer for reading a download task from database
    inner class GetDownloadTaskObserver(
        private val fileName: String,
        private val callback: (downloadTask: DownloadTask) -> Unit
    ) : SingleObserver<DownloadTask>(this) {
        override fun onSuccess(result: DownloadTask) {
            logD(TAG, "onSuccess: $result")
            super.onSuccess(result)
            //return result to callback
            callback.invoke(result)
        }

        override fun onSubscribe(disposable: Disposable) {
            logD(TAG, "onSubscribe: $fileName")
            super.onSubscribe(disposable)
        }

        override fun onError(t: Throwable) {
            logE(TAG, "onError: $fileName")
            super.onError(t)
        }
    }

    //observer for inserting a download task to database
    inner class InsertDownloadTaskObserver(private val downloadTask: DownloadTask) :
        CompletableObserver(this) {
        override fun onComplete() {
            logD(TAG, "onComplete: $downloadTask")
            super.onComplete()
        }

        override fun onSubscribe(disposable: Disposable) {
            super.onSubscribe(disposable)
            logD(TAG, "onSubscribe: $downloadTask")
        }

        override fun onError(t: Throwable) {
            logE(TAG, "onError: $downloadTask")
            super.onError(t)
        }
    }

    //observer for inserting a download task to database
    inner class InsertDownloadTasksObserver(private val downloadTasks: List<DownloadTask>) :
        CompletableObserver(this) {
        override fun onComplete() {
            logD(TAG, "onComplete: $downloadTasks")
            super.onComplete()
        }

        override fun onSubscribe(disposable: Disposable) {
            logD(TAG, "onSubscribe: $downloadTasks")
            super.onSubscribe(disposable)
        }

        override fun onError(t: Throwable) {
            logE(TAG, "onError: $downloadTasks")
            super.onError(t)
        }
    }

    //observer for deleting a download task from database
    inner class DeleteDownloadTaskObserver(
        private val fileName: String,
        private val callback: ((isSuccess: Boolean) -> Unit)? = null
    ) : CompletableObserver(this) {
        override fun onComplete() {
            logD(TAG, "onComplete: $fileName")
            super.onComplete()
            callback?.invoke(true)
        }

        override fun onSubscribe(disposable: Disposable) {
            logD(TAG, "onSubscribe: $fileName")
            super.onSubscribe(disposable)
        }

        override fun onError(t: Throwable) {
            logE(TAG, "onError: $fileName")
            super.onError(t)
            callback?.invoke(false)
        }
    }

    //observer for deleting a file or directory from storage
    inner class DeleteFileOrDirObserver(
        private val fileName: String,
        private val callback: ((isSuccess: Boolean) -> Unit)? = null
    ) : CompletableObserver(this) {
        override fun onComplete() {
            logD(TAG, "onComplete: $fileName")
            super.onComplete()
            callback?.invoke(true)
        }

        override fun onSubscribe(disposable: Disposable) {
            logD(TAG, "onSubscribe: $fileName")
            super.onSubscribe(disposable)
        }

        override fun onError(t: Throwable) {
            logE(TAG, "onError: $fileName")
            super.onError(t)
            callback?.invoke(false)
        }
    }

    //observer for executing a download task
//    inner class ExecuteDownloadTaskObserver(private val fileName: String) : FlowableSubscriber<null> {
//        override fun onSubscribe(s: Subscription) {
//            logD(TAG, "onSubscribe: $fileName")
//            downloadDisposables[fileName] = s
//        }
//
//        //call whenever downloadTask change (progress, state, ...)
//        override fun onNext(null: null) {
//            logD(TAG, "onNext: ${liveDownloadTasks.value!![fileName]}")
//            //notify observers
//            liveDownloadTasks.value = liveDownloadTasks.value
//        }
//
//        //call when downloadTask success
//        override fun onComplete() {
//            logD(TAG, "onComplete: $fileName")
//            //update this downloadTask with success state in liveDownloadTasks
//            liveDownloadTasks.value = liveDownloadTasks.value!!.apply {
//                get(fileName)!!.state = DownloadTask.STATE_SUCCESS
//            }
//        }
//
//        //call when downloadTask fail
//        override fun onError(e: Throwable) {
//            Log.e(TAG, "onError: $fileName")
//            e.printStackTrace()
//            //update this downloadTask with fail state in liveDownloadTasks
//            liveDownloadTasks.value = liveDownloadTasks.value!!.apply {
//                get(fileName)!!.state = DownloadTask.STATE_CANCEL_OR_FAIL
//            }
//        }
//    }
    inner class ExecuteDownloadTaskObserver(private val fileName: String) :
        StreamObserver<DownloadTask>(this) {
        override fun onSubscribe(disposable: Disposable) {
            logD(TAG, "onSubscribe: $fileName")
            super.onSubscribe(disposable)
        }

        //call whenever downloadTask change (progress, state, ...)
        override fun onNext(item: DownloadTask?) {
            logD(TAG, "onNext: $item")
            if (item != null) {
                liveDownloadTasks.value = liveDownloadTasks.value?.apply { set(fileName, item) }
            } else {
                liveDownloadTasks.value = liveDownloadTasks.value
            }
        }

        //call when downloadTask success
        override fun onComplete() {
            logD(TAG, "onComplete: $fileName")
            super.onComplete()
            //update this downloadTask with success state in liveDownloadTasks
            liveDownloadTasks.value = liveDownloadTasks.value

            //increase success download number
            statistics.increaseSuccessDownloadNum()

            finalize()
        }

        //call when downloadTask fail
        override fun onError(t: Throwable) {
            logE(TAG, "onError: $fileName")
            super.onError(t)
            //update this downloadTask with fail state in liveDownloadTasks
            liveDownloadTasks.value = liveDownloadTasks.value

            //increase cancel-or-fail download number
            statistics.increaseCanceledOrFailDownloadNum()

            finalize()
        }

        private fun finalize() {
            activeDownloadTaskObservables.remove(fileName)
            if (!isAnyActiveDownloadTask()) {
                stopForeground(true)
            }
        }
    }

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService {
            logD(TAG, "getService")
            return this@DownloadService
        }
    }
}
