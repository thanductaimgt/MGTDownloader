package com.mgt.downloader.ui.download_list.downloaded

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadFragment
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.serialize_model.DownloadTask
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.logD
import kotlinx.android.synthetic.main.fragment_downloaded.*
import java.io.File


class DownloadedFragment : BaseDownloadFragment() {
    override lateinit var adapter: DownloadedAdapter

    private val shareLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
//        if(it.resultCode== Activity.RESULT_OK){
            removeAllItems()
//            Toast.makeText(requireContext(), R.string.share_success, Toast.LENGTH_SHORT).show()
//        }else{
//            Toast.makeText(requireContext(), R.string.share_fail, Toast.LENGTH_SHORT).show()
//        }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_downloaded, container, false)
    }

    override fun onDownloadListChange(downloadTasks: HashMap<String, DownloadTask>) {
        adapter.submitList(
            downloadTasks.values
                .filter { it.state == DownloadTask.STATE_SUCCESS }
                .map { it.copy() }
                .sortedWith(compareBy({ it.startTime }, { it.fileName }))
                .reversed()
        ) {
            if (recyclerView.isNotEmpty()) {
                // save index and top position
                val layoutManager =
                    (recyclerView.layoutManager as LinearLayoutManager)
                val index = layoutManager.findFirstVisibleItemPosition()
                val firstView = recyclerView[0]
                val top = firstView.top - recyclerView.paddingTop
                layoutManager.scrollToPositionWithOffset(index, top)
            }
        }
    }

    override fun initView() {
        logD(TAG, "initView")
        adapter = DownloadedAdapter(
            this,
            DownloadTaskDiffUtil()
        )

        recyclerView.apply {
            adapter = this@DownloadedFragment.adapter
            layoutManager = LinearLayoutManager(context)
            setItemViewCacheSize(30)
            setHasFixedSize(true)
        }

        selectAllImgView.setOnClickListener(this)
        discardAllImgView.setOnClickListener(this)
        deleteAllImgView.setOnClickListener(this)
        retryAllImgView.setOnClickListener(this)
        shareAllImgView.setOnClickListener(this)
    }

    override fun onClickView(view: View) {
        when (view.id) {
            R.id.retryImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                downloadService?.retryDownloadTask(downloadTask.fileName)
            }
            R.id.openFileImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                try {
                    openFile(downloadTask)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        requireContext(),
                        requireContext().getString(R.string.desc_can_not_open_file),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            R.id.deleteImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                displayPopupMenu(view, ArrayList<DownloadTask>().apply { add(downloadTask) })
            }
            //toggle item expanded state
            R.id.itemRootView -> {
                if (selectedDownloadTasks.isEmpty()) {
                    val position = recyclerView.getChildAdapterPosition(view)
                    val downloadTask = adapter.currentList[position]

                    if (expandedDownloadTasksName.contains(downloadTask.fileName)) {
                        expandedDownloadTasksName.remove(downloadTask.fileName)
                    } else {
                        expandedDownloadTasksName.add(downloadTask.fileName)
                    }

                    adapter.notifyItemChanged(
                        position,
                        arrayListOf(DownloadTask.PAYLOAD_EXPAND_STATE)
                    )
                } else {
                    val position = recyclerView.getChildLayoutPosition(view)
                    addOrRemoveSelectedDownloadTask(position)
                }
            }
            R.id.fileIconImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                addOrRemoveSelectedDownloadTask(position)
            }
            R.id.shareAllImgView -> {
                shareAllItems()
            }
            R.id.shareImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                shareItem(downloadTask)
            }
        }
    }

    private fun shareAllItems() {
        val uris = ArrayList(selectedDownloadTasks.map { getShareFileUri(it) })
        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
        sendIntent.type = "*/*"
        sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)

        val chooserIntent =
            Intent.createChooser(sendIntent, requireContext().getString(R.string.share_message))

        shareLauncher.launch(chooserIntent)
    }

    private fun shareItem(downloadTask: DownloadTask) {
        val uri = getShareFileUri(downloadTask)
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "*/*"
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri)

        val chooserIntent =
            Intent.createChooser(sendIntent, requireContext().getString(R.string.share_message))

        shareLauncher.launch(chooserIntent)
    }

    private fun getShareFileUri(downloadTask: DownloadTask): Uri {
        val filePath = utils.getDownloadFilePath(downloadTask.fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val file = File(filePath)
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().applicationContext.packageName}.${Constants.FILE_PROVIDER_AUTH}",
                file
            )
        } else {
            Uri.parse(filePath)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.itemRootView -> {
                val position = recyclerView.getChildLayoutPosition(view)
                addOrRemoveSelectedDownloadTask(position)
                return true
            }
        }
        return false
    }

    override fun updateSelectLayout() {
        if (selectedDownloadTasks.isEmpty()) {
            selectLayout.visibility = View.GONE
        } else {
            selectCountTextView.text =
                String.format(getString(R.string.item_count), selectedDownloadTasks.size)
            if (selectedDownloadTasks.any { utils.isDownloadedFileExist(it) }) {
                retryAllImgView.visibility = View.GONE
            } else {
                retryAllImgView.visibility = View.VISIBLE
            }
            if (selectedDownloadTasks.all { utils.isDownloadedFileExist(it) }) {
                shareAllImgView.visibility = View.VISIBLE
            } else {
                shareAllImgView.visibility = View.GONE
            }
            selectLayout.visibility = View.VISIBLE
        }
    }

    private fun openFile(downloadTask: DownloadTask) {
        val file = utils.getDownloadFile(downloadTask.fileName)

        if (file.extension == "zip") {
            openZipFile(downloadTask)
        } else {
            requireContext().startActivity(Intent(Intent.ACTION_VIEW).apply {
                val fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().applicationContext.packageName}.${Constants.FILE_PROVIDER_AUTH}",
                    file
                )
                val mimeType = if (downloadTask.isDirectory)
                    DocumentsContract.Document.MIME_TYPE_DIR
                else
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)

                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    private fun openZipFile(downloadTask: DownloadTask) {
        val uri = utils.getDownloadFilePath(downloadTask.fileName)
        val filePreviewInfo = FilePreviewInfo(
            downloadTask.fileName,
            uri,
            uri,
            isLocalFile = true
        )

        viewFileDialog.show(parentFragmentManager, filePreviewInfo)
    }

    companion object {
        const val SHARE_REQUEST_CODE = 1998
    }
}