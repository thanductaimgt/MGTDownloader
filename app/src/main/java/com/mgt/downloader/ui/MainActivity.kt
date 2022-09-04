package com.mgt.downloader.ui

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mgt.downloader.DownloadService
import com.mgt.downloader.MyApplication
import com.mgt.downloader.R
import com.mgt.downloader.base.CommonJavaScriptInterface
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.extractor.TikTokExtractorV2
import com.mgt.downloader.factory.ViewModelFactory
import com.mgt.downloader.rxjava.SingleObservable
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
    private lateinit var alertDialog: AlertDialog
    var liveDownloadService = MutableLiveData<DownloadService>()
    private val serviceConnection = DownloadServiceConnection()
    private lateinit var filePreviewInfo: FilePreviewInfo
    private lateinit var viewModel: MainViewModel

    private var afterPermissionRequested: (() -> Any?)? = null

    override fun onNewIntent(intent: Intent) {
        logD(TAG, "onNewIntent")
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

        initView()

        if (intent.getStringExtra(Constants.MESSAGE) == Constants.OPEN_DOWNLOAD_LIST) {
            downloadListDialog.show()
        }

        try {
            startService(getStartServiceIntent())
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        MyApplication.liveConnection.observe(this, { isConnected ->
            if (isConnected) {
                networkStateTextView.visibility = View.INVISIBLE
                liveDownloadService.value?.onReconnect()
            } else {
                networkStateTextView.visibility = View.VISIBLE
                liveDownloadService.value?.onDisconnect()
            }
        })

//        val adRequest = AdRequest.Builder().build()
//        adView.loadAd(adRequest)

        checkUpdateRequestHeaders()

        checkUpdateApp()
    }

    private fun checkUpdateRequestHeaders() {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            getRequestHeaders()
        }.subscribe(object : SingleObserver<Map<String, String>>(viewModel) {
            override fun onSuccess(result: Map<String, String>) {
                logD(TAG, "Obtained headers: $result")
                super.onSuccess(result)
                Configurations.requestHeaders = result
            }
        })
    }

    private fun getRequestHeaders(): Map<String, String> {
        val json = Utils.getDontpadContent(Constants.SUBPATH_GENERAL_HEADERS)
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(json, mapType) ?: emptyMap()
    }

    private fun checkUpdateApp() {
        SingleObservable.fromCallable(MyApplication.unboundExecutorService) {
            Utils.getDontpadContent(Constants.SUBPATH_VERSION_CODE).toInt()
        }.subscribe(getNewestVersionCodeObserver)
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
            downloadUnavailableTextView.visibility = View.GONE

            if (URLUtil.isValidUrl(url)) {
                showLoadingAnimation()
                viewModel.getFilePreviewInfo(
                    url,
                    filePreviewInfoObserver
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
        initWebView()

        downloadListDialog =
            DownloadListFragment(
                supportFragmentManager
            )
        fileNameDialog =
            FileNameDialog(supportFragmentManager)
        viewFileDialog =
            ViewFileDialog(supportFragmentManager)
        settingsDialog = SettingsDialog(supportFragmentManager)
        alertDialog = AlertDialog(supportFragmentManager)

        urlEditText.addTextChangedListener(textWatcher)
        val shareUrl = intent.extras?.getString(Intent.EXTRA_TEXT)
        if (shareUrl != null) {
            urlEditText.setText(shareUrl)
        }

        startDownloadAnimView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator) {
                startDownloadAnimView.reverseAnimationSpeed()
            }

            override fun onAnimationEnd(animation: Animator) {
                startDownloadAnimView.reverseAnimationSpeed()
                startDownloadAnimView.visibility = View.INVISIBLE
            }

            override fun onAnimationCancel(animation: Animator) {

            }

            override fun onAnimationStart(animation: Animator) {
            }
        })

        downloadButton.isEnabled = false
        multiThreadDownloadButton.isEnabled = false
        downloadUnavailableTextView.visibility = View.GONE

        showDownloadListLayout.setOnClickListener(this)
        downloadButton.setOnClickListener(this)
        multiThreadDownloadButton.setOnClickListener(this)
        editFileNameImgView.setOnClickListener(this)
        fileNameTextView.setOnClickListener(this)
        viewFileInfoImgView.setOnClickListener(this)
        settingsImgView.setOnClickListener(this)
    }

    private fun initWebView() {
        MainActivity.webView = webView
            .apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }

                addJavascriptInterface(
                    MainActivity.jsInterface,
                    TikTokExtractorV2.JS_INTERFACE_NAME
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        urlNewString: String?
                    ): Boolean {
                        return false
                    }
                }
            }
        reloadWebView()
    }

    companion object {
        lateinit var webView: WebView
        var loadWebTime: Long? = null
        var jsInterface = CommonJavaScriptInterface()

        fun reloadWebView() {
            webView.loadUrl(TikTokExtractorV2.WEB_URL)
            loadWebTime = System.currentTimeMillis()
        }
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
        logD(TAG, "startDownloadTask")

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
        super.onDestroy()
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

        urlEditText?.removeTextChangedListener(textWatcher)
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
            logD(TAG, "onServiceConnected")
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
            logD(TAG, "onServiceDisconnected")
            liveDownloadService.value = null
        }
    }

    val filePreviewInfoObserver by lazy {
        object : SingleObserver<FilePreviewInfo>(viewModel) {
            override fun onSuccess(result: FilePreviewInfo) {
                logD(TAG, "FilePreviewInfo: $result")
                super.onSuccess(result)
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
                        if (result.size != -1L && result.isMultipartSupported) {
                            multiThreadDownloadButton.isEnabled = true
                        } else {
                            downloadUnavailableTextView.visibility = View.VISIBLE
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
                }
            }

            override fun onError(t: Throwable) {
                super.onError(t)
                hideLoadingAnimation()
                showWarning(getString(R.string.cannot_resolve_host))
            }
        }
    }

    private val getNewestVersionCodeObserver by lazy {
        object : SingleObserver<Int>(viewModel) {
            override fun onSuccess(result: Int) {
                super.onSuccess(result)
                val appVersionCode = Utils.getAppVersionCode()
                if (appVersionCode < result) {
                    alertDialog.apply {
                        title = this@MainActivity.getString(R.string.update_app_title)
                        description = this@MainActivity.getString(R.string.update_app_description)
                        positiveButtonText = this@MainActivity.getString(R.string.update)
                        negativeButtonText = this@MainActivity.getString(R.string.later)
                        positiveButtonClickListener = {
                            Utils.navigateToCHPlay(this@MainActivity)
                        }
                        show()
                    }
                }
            }
        }
    }
}
