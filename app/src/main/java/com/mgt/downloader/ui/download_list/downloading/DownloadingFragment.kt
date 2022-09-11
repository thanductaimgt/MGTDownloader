package com.mgt.downloader.ui.download_list.downloading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadFragment
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.serialize_model.DownloadTask
import kotlinx.android.synthetic.main.fragment_downloading.*

class DownloadingFragment : BaseDownloadFragment() {
    override lateinit var adapter: DownloadingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_downloading, container, false)
    }

    override fun onDownloadListChange(downloadTasks: HashMap<String, DownloadTask>) {
        val previousDownloadTasks = adapter.currentList
        adapter.submitList(
            downloadTasks.values
                .filter {
                    it.state == DownloadTask.STATE_DOWNLOADING
                            || it.state == DownloadTask.STATE_PERSISTENT_PAUSED
                            || it.state == DownloadTask.STATE_TEMPORARY_PAUSE
                }
                .map { it.copy() }
                .sortedWith(compareBy({ it.startTime }, { it.fileName }))
        ) {
            if (previousDownloadTasks.size > adapter.currentList.size) {
                selectedDownloadTasks.removeAll { downloadTask -> adapter.currentList.all { it.fileName != downloadTask.fileName } }
                expandedDownloadTasksName.removeAll { fileName -> adapter.currentList.all { it.fileName != fileName } }
                updateSelectLayout()
            }
        }
    }

    override fun initView() {
        adapter = DownloadingAdapter(
            this,
            DownloadTaskDiffUtil()
        )

        recyclerView.apply {
            adapter = this@DownloadingFragment.adapter
            layoutManager = LinearLayoutManager(context)
            setItemViewCacheSize(30)
        }

        pauseAllImgView.setOnClickListener(this)
        resumeAllImgView.setOnClickListener(this)
        cancelAllImgView.setOnClickListener(this)
        selectAllImgView.setOnClickListener(this)
        discardAllImgView.setOnClickListener(this)
    }

    override fun onClickView(view: View) {
        when (view.id) {
            R.id.pauseResumeImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                if (downloadTask.state == DownloadTask.STATE_DOWNLOADING) {
                    downloadService?.pauseDownloadTask(downloadTask.fileName)
                } else {
                    downloadService?.resumeDownloadTask(downloadTask.fileName)
                }
            }
            R.id.cancelImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                downloadService?.cancelDownloadTask(downloadTask.fileName)
            }
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
            R.id.pauseAllImgView -> {
                selectedDownloadTasks.forEach {
                    downloadService?.pauseDownloadTask(it.fileName)
                }
            }
            R.id.resumeAllImgView -> {
                selectedDownloadTasks.forEach {
                    downloadService?.resumeDownloadTask(it.fileName)
                }
            }
            R.id.cancelAllImgView -> {
                selectedDownloadTasks.forEach {
                    downloadService?.cancelDownloadTask(it.fileName)
                }
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

    private fun showSelectLayout() {
        selectLayout.apply {
            if (visibility == View.GONE) {
                visibility = View.VISIBLE
            }
        }
    }

    private fun hideSelectLayout() {
        selectLayout.apply {
            if (visibility == View.VISIBLE) {
                visibility = View.GONE
            }
        }
    }

    override fun updateSelectLayout() {
        if (selectedDownloadTasks.isEmpty()) {
            hideSelectLayout()
        } else {
            selectCountTextView.text =
                String.format(getString(R.string.item_count), selectedDownloadTasks.size)
            showSelectLayout()
        }
    }
}