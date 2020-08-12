package com.mgt.downloader.ui.download_list.cancel_fail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_canceled_or_fail.*
import kotlinx.android.synthetic.main.fragment_canceled_or_fail.discardAllImgView
import kotlinx.android.synthetic.main.fragment_canceled_or_fail.recyclerView
import kotlinx.android.synthetic.main.fragment_canceled_or_fail.selectAllImgView
import kotlinx.android.synthetic.main.fragment_canceled_or_fail.selectCountTextView
import kotlinx.android.synthetic.main.fragment_canceled_or_fail.selectLayout
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadListFragment
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.Utils

class CancelFailFragment : BaseDownloadListFragment(){
    override lateinit var adapter: CancelFailAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_canceled_or_fail, container, false)
    }

    override fun onDownloadListChange(downloadTasks: HashMap<String, DownloadTask>) {
        adapter.submitList(
            downloadTasks.values
                .filter { it.state == DownloadTask.STATE_CANCEL_OR_FAIL }
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
        Utils.log(TAG, "initView")
        adapter = CancelFailAdapter(
            this,
            DownloadTaskDiffUtil()
        )

        recyclerView.apply {
            adapter = this@CancelFailFragment.adapter
            layoutManager = LinearLayoutManager(context)
            setItemViewCacheSize(30)
        }

        selectAllImgView.setOnClickListener(this)
        discardAllImgView.setOnClickListener(this)
        retryAllImgView.setOnClickListener(this)
        deleteAllImgView.setOnClickListener(this)
    }

    override fun onClickView(view: View) {
        when (view.id) {
            R.id.retryImgView -> {
                val position = recyclerView.getChildLayoutPosition(view.parent.parent as View)
                val downloadTask = adapter.currentList[position]
                downloadService?.retryDownloadTask(downloadTask.fileName)
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
            selectLayout.visibility = View.VISIBLE
        }
    }
}