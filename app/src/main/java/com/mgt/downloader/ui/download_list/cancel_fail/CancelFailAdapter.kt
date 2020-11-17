package com.mgt.downloader.ui.download_list.cancel_fail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.item_cancel_or_fail.view.*
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadAdapter
import com.mgt.downloader.base.BaseDownloadFragment
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.utils.Utils

class CancelFailAdapter(
    fragment: BaseDownloadFragment,
    downloadTaskDiffUtil: DownloadTaskDiffUtil
) :
    BaseDownloadAdapter(
        fragment,
        downloadTaskDiffUtil
    ) {
    override fun onCreateCurViewHolder(parent: ViewGroup, viewType: Int): DownloadBaseHolder {
        return CancelFailHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_cancel_or_fail, parent, false))
    }

    inner class CancelFailHolder(itemView: View) : DownloadBaseHolder(itemView) {
        override fun bind(position:Int) {
            super.bind(position)
            val downloadTask = currentList[position]
            itemView.apply {
                //bind storage state (file deleted?)
                if (Utils.isDownloadedFileExist(context, downloadTask)) {
                    storageStateTextView.visibility = View.GONE
                    downloadedSizeTextView.text =
                        if (downloadTask.partsDownloadedSize.isNotEmpty()) {
                            Utils.getFormatFileSize(downloadTask.downloadedSize)
                        } else {
                            Utils.getFileOrDirSize(context, downloadTask.fileName).let {
                                if (it == -1L) {
                                    context.getString(R.string.desc_unknown_size)
                                } else {
                                    Utils.getFormatFileSize(it)
                                }
                            }
                        }

                    downloadedSizeTextView.visibility = View.VISIBLE
                    downloadedSizeTitleTextView.visibility = View.VISIBLE
                } else {
                    storageStateTextView.visibility = View.VISIBLE
                    downloadedSizeTextView.visibility = View.GONE
                    downloadedSizeTitleTextView.visibility = View.GONE
                }
            }
        }

        override fun bindListeners() {
            super.bindListeners()
            itemView.apply {
                retryImgView.setOnClickListener(fragment as View.OnClickListener)
                deleteImgView.setOnClickListener(fragment as View.OnClickListener)
            }
        }

        override fun onSelected(downloadTask: DownloadTask) {
            itemView.apply {
                retryImgView.visibility = View.INVISIBLE
                deleteImgView.visibility = View.INVISIBLE
            }
        }

        override fun onNotSelected(downloadTask: DownloadTask) {
            itemView.apply {
                retryImgView.visibility = View.VISIBLE
                deleteImgView.visibility = View.VISIBLE
            }
        }
    }
}