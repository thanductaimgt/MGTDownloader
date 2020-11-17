package com.mgt.downloader.ui.download_list.downloaded

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_downloaded.*
import kotlinx.android.synthetic.main.fragment_downloaded.discardAllImgView
import kotlinx.android.synthetic.main.fragment_downloaded.recyclerView
import kotlinx.android.synthetic.main.fragment_downloaded.selectAllImgView
import kotlinx.android.synthetic.main.fragment_downloaded.selectCountTextView
import kotlinx.android.synthetic.main.fragment_downloaded.selectLayout
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadFragment
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.ui.view_file.ViewFileDialog
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.Utils
import com.mgt.downloader.utils.logD


class DownloadedFragment : BaseDownloadFragment() {
    override lateinit var adapter: DownloadedAdapter

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
            view!!.apply {
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
        }

        viewFileDialog =
            ViewFileDialog(childFragmentManager)

        selectAllImgView.setOnClickListener(this)
        discardAllImgView.setOnClickListener(this)
        deleteAllImgView.setOnClickListener(this)
        retryAllImgView.setOnClickListener(this)
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
                        context!!,
                        context!!.getString(R.string.desc_can_not_open_file),
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
            if (selectedDownloadTasks.any { Utils.isDownloadedFileExist(context!!, it) }) {
                retryAllImgView.visibility = View.GONE
            } else {
                retryAllImgView.visibility = View.VISIBLE
            }
            selectLayout.visibility = View.VISIBLE
        }
    }

    private fun openFile(downloadTask: DownloadTask) {
        val file = Utils.getFile(context!!, downloadTask.fileName)

        if (file.extension == "zip") {
            openZipFile(downloadTask)
        } else {
            context!!.startActivity(Intent(Intent.ACTION_VIEW).apply {
                val fileUri = FileProvider.getUriForFile(
                    context!!,
                    "${context!!.applicationContext.packageName}.${Constants.FILE_PROVIDER_AUTH}",
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
        val uri = Utils.getFilePath(context!!, downloadTask.fileName)
        val filePreviewInfo = FilePreviewInfo(
            downloadTask.fileName,
            uri,
            uri,
            isLocalFile = true
        )

        viewFileDialog.show(filePreviewInfo)
    }
}