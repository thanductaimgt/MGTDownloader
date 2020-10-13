package com.mgt.downloader.ui

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.mgt.downloader.DownloadService
import com.mgt.downloader.MyApplication
import com.mgt.downloader.R
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.factory.ViewModelFactory
import com.mgt.downloader.rxjava.CompositeDisposable
import com.mgt.downloader.rxjava.Disposable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.ui.download_list.DownloadListFragment
import com.mgt.downloader.ui.view_file.ViewFileDialog
import com.mgt.downloader.utils.*
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import java.sql.Date

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var downloadListDialog: DownloadListFragment
    private lateinit var fileNameDialog: FileNameDialog
    private lateinit var viewFileDialog: ViewFileDialog
    private lateinit var settingsDialog: SettingsDialog
    var liveDownloadService = MutableLiveData<DownloadService>()
    private val serviceConnection = DownloadServiceConnection()
    private lateinit var filePreviewInfo: FilePreviewInfo
    private val compositeDisposable = CompositeDisposable()
    private lateinit var viewModel: MainViewModel

    private var afterPermissionRequested: (() -> Any?)? = null

    override fun onNewIntent(intent: Intent) {
        Utils.log(TAG, "onNewIntent")
        super.onNewIntent(intent)
//        this.intent.putExtra(Constants.MESSAGE, intent.getStringExtra(Constants.MESSAGE))
        intent.extras?.let { this.intent.putExtras(it) }
        urlEditText?.let {
            val shareUrl = intent.extras?.getString(Intent.EXTRA_TEXT)
            if (shareUrl != null) {
                it.setText(shareUrl)
                try {
                    downloadListDialog.dismiss()
                } catch (t: Throwable) {
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this, ViewModelFactory.getInstance()).get(
            MainViewModel::class.java
        )
        viewModel.getFilePreviewInfo("", object : SingleObserver<FilePreviewInfo> {
            override fun onSubscribe(disposable: Disposable) {

            }

            override fun onError(t: Throwable) {
            }

            override fun onSuccess(result: FilePreviewInfo) {
            }
        })

        initView()

        if (intent.getStringExtra(Constants.MESSAGE) == Constants.OPEN_DOWNLOAD_LIST) {
            downloadListDialog.show()
        }

        try {
            startService(getStartServiceIntent())
        } catch (t: Throwable) {
        }

        MyApplication.liveConnection.observe(this, Observer { isConnected ->
            if (isConnected) {
                networkStateTextView.visibility = View.INVISIBLE
                liveDownloadService.value?.onReconnect()
            } else {
                networkStateTextView.visibility = View.VISIBLE
                liveDownloadService.value?.onDisconnect()
            }
        })
    }

    private fun getStartServiceIntent(): Intent {
        return Intent(this, DownloadService::class.java)
    }

    private fun requestStoragePermissionsIfNeeded() {
        if (!hasPermissions(Constants.PERMISSIONS_ID)) {
            ActivityCompat.requestPermissions(
                this,
                Constants.PERMISSIONS_ID,
                Constants.REQUEST_PERMISSIONS
            )
        } else {
            afterPermissionRequested?.invoke()
            afterPermissionRequested = null
        }
    }

    private fun hasPermissions(
        permissions: Array<String>
    ): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == Constants.REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                afterPermissionRequested?.invoke()
                afterPermissionRequested = null
            } else {
                Toast.makeText(this, R.string.desc_permission_denied, Toast.LENGTH_LONG).apply {
                    setGravity(Gravity.TOP, 0, 50)
                    show()
                }
            }
        }
    }

    override fun onResume() {
        bindService(
            getStartServiceIntent(),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        super.onResume()
    }

    override fun onPause() {
        unbindService(serviceConnection)
        super.onPause()
    }

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(editable: Editable) {
            val url = editable.toString()
            downloadButton.isEnabled = false
            multiThreadDownloadButton.isEnabled = false
            multiThreadDownloadUnavailableDescTextView.visibility = View.GONE

            if (URLUtil.isValidUrl(url)) {
                showLoadingAnimation()
                viewModel.getFilePreviewInfo(
                    url,
                    GetFilePreviewInfoObserver()
                )
            } else if (url != "") {
                showWarning(getString(R.string.desc_invalid_url))
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            hideFilePreview()
            hideWarning()
            hideLoadingAnimation()
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initView() {
        downloadListDialog =
            DownloadListFragment(
                supportFragmentManager
            )
        fileNameDialog =
            FileNameDialog(supportFragmentManager)
        viewFileDialog =
            ViewFileDialog(supportFragmentManager)
        settingsDialog = SettingsDialog(supportFragmentManager)

        urlEditText.addTextChangedListener(textWatcher)
        val shareUrl = intent.extras?.getString(Intent.EXTRA_TEXT)
        if (shareUrl != null) {
            urlEditText.setText(shareUrl)
        }

        startDownloadAnimView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(p0: Animator?) {
                startDownloadAnimView.reverseAnimationSpeed()
            }

            override fun onAnimationEnd(p0: Animator?) {
                startDownloadAnimView.reverseAnimationSpeed()
                startDownloadAnimView.visibility = View.INVISIBLE
            }

            override fun onAnimationCancel(p0: Animator?) {

            }

            override fun onAnimationStart(p0: Animator?) {
            }
        })

        downloadButton.isEnabled = false
        multiThreadDownloadButton.isEnabled = false
        multiThreadDownloadUnavailableDescTextView.visibility = View.GONE

        showDownloadListLayout.setOnClickListener(this)
        downloadButton.setOnClickListener(this)
        multiThreadDownloadButton.setOnClickListener(this)
        editFileNameImgView.setOnClickListener(this)
        fileNameTextView.setOnClickListener(this)
        viewFileInfoImgView.setOnClickListener(this)
        settingsImgView.setOnClickListener(this)
    }

    private fun showFilePreview(filePreviewInfo: FilePreviewInfo) {
        fileNameTextView.visibility = View.VISIBLE
        fileSizeTextView.visibility = View.VISIBLE
        fileIconImgView.visibility = View.VISIBLE
        editFileNameImgView.visibility = View.VISIBLE
        if (filePreviewInfo.centralDirOffset >= 0 || filePreviewInfo.centralDirSize >= 0) {
            viewFileInfoImgView.visibility = View.VISIBLE
        }

        if (filePreviewInfo.thumbUri != null) {
            fileIconImgView.layoutParams =
                (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                    dimensionRatio = filePreviewInfo.thumbRatio
                    height = resources.getDimension(R.dimen.sizeVideoThumbHeight).toInt()
                }

            Picasso.get().smartLoad(filePreviewInfo.thumbUri, fileIconImgView) {
                it.fit().centerCrop()
            }

            fileIconImgView.cornerRadius = resources.getDimension(R.dimen.sizeRoundCornerRadiusItem)

            fileNameTextView.maxLines = 2
        } else {
            fileIconImgView.layoutParams =
                (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                    dimensionRatio = "1:1"
                    height = resources.getDimension(R.dimen.sizeFileExtensionHeight).toInt()
                }

            fileIconImgView.setImageResource(
                Utils.getResIdFromFileExtension(
                    this,
                    Utils.getFileExtension(filePreviewInfo.name)
                )
            )

            fileIconImgView.cornerRadius = 0f

            fileNameTextView.maxLines = 1
        }

        fileNameTextView.text = filePreviewInfo.name

        if (filePreviewInfo.size != -1L) {
            fileSizeTextView.text = String.format(
                "%s",
                Utils.getFormatFileSize(filePreviewInfo.size)
            )
        } else {
            fileSizeTextView.text =
                String.format(
                    "${getString(R.string.desc_size)} ${
                        getString(
                            R.string.desc_unknown_size
                        )
                    }"
                )
        }
    }

    private fun hideFilePreview() {
        fileNameTextView.visibility = View.GONE
        fileSizeTextView.visibility = View.GONE
        fileIconImgView.visibility = View.GONE
        editFileNameImgView.visibility = View.GONE
        viewFileInfoImgView.visibility = View.GONE
    }

    private fun showWarning(message: CharSequence) {
        warningTextView.text = message
        warningTextView.visibility = View.VISIBLE
    }

    private fun hideWarning() {
        warningTextView.visibility = View.GONE
    }

    private fun showLoadingAnimation() {
        loadingAnimView.visibility = View.VISIBLE
        loadingAnimView.playAnimation()
    }

    private fun hideLoadingAnimation() {
        loadingAnimView.cancelAnimation()
        loadingAnimView.visibility = View.GONE
    }

    private fun showStartDownloadAnimation() {
        startDownloadAnimView.visibility = View.VISIBLE
        startDownloadAnimView.playAnimation()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.downloadButton -> {
                afterPermissionRequested = {
                    startDownloadTask(filePreviewInfo)
                    Utils.hideKeyboard(this, rootView)
                }
                requestStoragePermissionsIfNeeded()
            }
            R.id.showDownloadListLayout -> downloadListDialog.show()
            R.id.editFileNameImgView -> fileNameDialog.show(filePreviewInfo)
            R.id.fileNameTextView -> fileNameDialog.show(filePreviewInfo)
            R.id.viewFileInfoImgView -> viewFileDialog.show(filePreviewInfo)
            R.id.settingsImgView -> settingsDialog.show()
            R.id.multiThreadDownloadButton -> {
                afterPermissionRequested = {
                    startDownloadTask(
                        filePreviewInfo,
                        Configurations.multiThreadDownloadNum
                    )
                    Utils.hideKeyboard(this, rootView)
                }
                requestStoragePermissionsIfNeeded()
            }
        }
    }

    fun startDownloadTask(filePreviewInfo: FilePreviewInfo, threadNum: Int = 1) {
        val downloadTask = DownloadTask(
            fileName = filePreviewInfo.name,
            displayUrl = filePreviewInfo.displayUri,
            downloadUrl = filePreviewInfo.downloadUri,
            startTime = Date(System.currentTimeMillis()),
            totalSize = filePreviewInfo.size,
            partsDownloadedSize = if (threadNum > 1) Utils.getBlankArrayList(threadNum) else ArrayList(),
            thumbUrl = filePreviewInfo.thumbUri,
            thumbRatio = filePreviewInfo.thumbRatio
        )

        showStartDownloadAnimation()
        updateFileNamePreview(filePreviewInfo.name)

        startDownloadTask(downloadTask)
    }

    fun startDownloadTask(downloadTask: DownloadTask) {
        Utils.log(TAG, "startDownloadTask")

        liveDownloadService.value!!.executeDownloadTask(downloadTask, false)
    }

    // test_db-master.zip -> test_db-master (1).zip
    private fun updateFileNamePreview(curFileName: String) {
        if (curFileName == filePreviewInfo.name) {
            val stopCondition = { newFileName: String ->
                !(liveDownloadService.value?.isFileOrDownloadTaskExist(newFileName) ?: false)
            }
            filePreviewInfo.name = Utils.generateNewDownloadFileName(
                this,
                curFileName,
                stopCondition
            )
        }
        fileNameTextView.text = filePreviewInfo.name
    }

    override fun onDestroy() {
        // if any download task in progress, continue service in foreground, else stop service
        if (liveDownloadService.value?.isAnyActiveDownloadTask()
            == true
        ) {
            startService(getStartServiceIntent().apply {
                putExtra(Constants.MESSAGE, Constants.START_FOREGROUND_SERVICE)
            })
        } else {
            stopService(getStartServiceIntent())
        }

        compositeDisposable.clear()
        urlEditText?.removeTextChangedListener(textWatcher)

        super.onDestroy()
    }

    fun showSettingsDialog() {
        settingsDialog.show()
    }

    fun applyCurrentConfigs() {
        // decrease case
        if (MyApplication.boundExecutorService.maximumPoolSize > Configurations.maxConcurDownloadNum) {
            liveDownloadService.value?.onDisconnect()
            liveDownloadService.value?.onReconnect()
        } else {
            MyApplication.boundExecutorService.maximumPoolSize =
                Configurations.maxConcurDownloadNum
            MyApplication.boundExecutorService.corePoolSize =
                Configurations.maxConcurDownloadNum
        }
    }

    inner class DownloadServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            Utils.log(TAG, "onServiceConnected")
            liveDownloadService.value = (binder as DownloadService.DownloadBinder).getService()

            //open app from notification
            intent.getStringExtra(Constants.MESSAGE)?.let {
                when (it) {
                    Constants.OPEN_DOWNLOAD_LIST -> {
//                        downloadListDialog.show()
                        intent.removeExtra(Constants.MESSAGE)
                    }
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Utils.log(TAG, "onServiceDisconnected")
            liveDownloadService.value = null
        }
    }

    inner class GetFilePreviewInfoObserver : SingleObserver<FilePreviewInfo> {
        override fun onSuccess(result: FilePreviewInfo) {
            result.name = result.name.replace('/', '|')

            if (result.displayUri == urlEditText.text.toString()) {
                hideLoadingAnimation()
                hideWarning()
                hideFilePreview()

                if (result.size == Constants.ERROR.toLong()) {
                    showWarning(getString(R.string.cannot_resolve_host))
                } else {
                    //get cached size
                    if (result.size == -1L) {
                        result.size =
                            liveDownloadService.value!!.getFileSizeOfDownloadedTask(result.displayUri)
                    }

                    MyApplication.fileInfoCaches[result.displayUri] = result.copy()

                    val stopCondition = { newFileName: String ->
                        !(liveDownloadService.value?.isFileOrDownloadTaskExist(newFileName)
                            ?: false)
                    }
                    if (!stopCondition(result.name)) {
                        result.name = Utils.generateNewDownloadFileName(
                            this@MainActivity,
                            result.name,
                            stopCondition
                        )
                    }

                    this@MainActivity.filePreviewInfo = result
                    downloadButton.isEnabled = true
                    if (result.size != -1L) {
                        multiThreadDownloadButton.isEnabled = true
                    } else {
                        multiThreadDownloadUnavailableDescTextView.visibility = View.VISIBLE
                    }

                    showFilePreview(result)
                }
            } else if (result.size != Constants.ERROR.toLong() && result.centralDirOffset != Constants.ERROR && result.centralDirSize != Constants.ERROR) {
                //get cached size
                if (result.size == -1L) {
                    result.size =
                        liveDownloadService.value!!.getFileSizeOfDownloadedTask(result.displayUri)
                }

                MyApplication.fileInfoCaches[result.displayUri] = result.copy()

                val stopCondition = { newFileName: String ->
                    !(liveDownloadService.value?.isFileOrDownloadTaskExist(newFileName) ?: false)
                }
                if (!stopCondition(result.name)) {
                    result.name = Utils.generateNewDownloadFileName(
                        this@MainActivity,
                        result.name,
                        stopCondition
                    )
                }
            }
        }

        override fun onSubscribe(disposable: Disposable) {
            compositeDisposable.add(disposable)
        }

        override fun onError(t: Throwable) {
            hideLoadingAnimation()
            showWarning(getString(R.string.cannot_resolve_host))
            t.printStackTrace()
        }
    }
}
