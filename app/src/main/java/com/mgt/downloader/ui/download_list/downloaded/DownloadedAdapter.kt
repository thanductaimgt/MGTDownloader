package com.mgt.downloader.ui.download_list.downloaded

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadAdapter
import com.mgt.downloader.base.BaseDownloadFragment
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.serialize_model.DownloadTask
import kotlinx.android.synthetic.main.item_downloaded.view.*

class DownloadedAdapter(
    fragment: BaseDownloadFragment,
    downloadTaskDiffUtil: DownloadTaskDiffUtil
) :
    BaseDownloadAdapter(
        fragment,
        downloadTaskDiffUtil
    ) {
    override fun onCreateCurViewHolder(parent: ViewGroup, viewType: Int): DownloadBaseHolder {
        return DownloadedHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_downloaded, parent, false)
        )
    }

    inner class DownloadedHolder(itemView: View) : DownloadBaseHolder(itemView) {
        override fun bind(position: Int) {
            super.bind(position)
            val downloadTask = currentList[position]
            itemView.apply {
                //bind storage state (file deleted?)
                if (utils.isDownloadedFileExist(downloadTask)) {
                    storageStateTextView.visibility = View.GONE
                } else {
                    storageStateTextView.visibility = View.VISIBLE
                }

                openFileImgView.setOnClickListener(fragment as View.OnClickListener)
                retryImgView.setOnClickListener(fragment as View.OnClickListener)
                shareImgView.setOnClickListener(fragment as View.OnClickListener)
            }
        }

        override fun bindSelectState(downloadTask: DownloadTask, isSelected: Boolean) {
            itemView.apply {
                if (isSelected) {
                    openFileImgView.visibility = View.INVISIBLE
                    retryImgView.visibility = View.INVISIBLE
                    shareImgView.visibility = View.INVISIBLE
                    deleteImgView.visibility = View.INVISIBLE
                } else {
                    if (utils.isDownloadedFileExist(downloadTask)) {
                        openFileImgView.visibility = View.VISIBLE
                        retryImgView.visibility = View.GONE
                        shareImgView.visibility = View.VISIBLE
                    } else {
                        openFileImgView.visibility = View.GONE
                        retryImgView.visibility = View.VISIBLE
                        shareImgView.visibility = View.GONE
                    }
                    deleteImgView.visibility = View.VISIBLE
                }
            }
        }

        override fun bindListeners() {
            super.bindListeners()
            itemView.deleteImgView.setOnClickListener(fragment as View.OnClickListener)
        }
    }
}