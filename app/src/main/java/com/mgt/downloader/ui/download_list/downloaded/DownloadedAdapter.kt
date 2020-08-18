package com.mgt.downloader.ui.download_list.downloaded

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.item_downloaded.view.*
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadAdapter
import com.mgt.downloader.base.BaseDownloadListFragment
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.utils.Utils

class DownloadedAdapter(
    fragment: BaseDownloadListFragment,
    downloadTaskDiffUtil: DownloadTaskDiffUtil
) :
    BaseDownloadAdapter(
        fragment,
        downloadTaskDiffUtil
    ) {
    override fun onCreateCurViewHolder(parent: ViewGroup, viewType: Int): DownloadBaseHolder {
        return DownloadedHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_downloaded, parent, false))
    }

    inner class DownloadedHolder(itemView: View) : DownloadBaseHolder(itemView) {
        override fun bind(position:Int) {
            super.bind(position)
            val downloadTask = currentList[position]
            itemView.apply {
                //bind storage state (file deleted?)
                if (Utils.isDownloadedFileExist(context, downloadTask)) {
                    storageStateTextView.visibility = View.GONE
                    openFileImgView.visibility = View.VISIBLE
                    openFileImgView.setOnClickListener(fragment as View.OnClickListener)
                    retryImgView.visibility = View.GONE
                } else {
                    storageStateTextView.visibility = View.VISIBLE
                    openFileImgView.visibility = View.GONE
                    retryImgView.visibility = View.VISIBLE
                    retryImgView.setOnClickListener(fragment as View.OnClickListener)
                }
            }
        }

        override fun bindListeners() {
            super.bindListeners()
            itemView.deleteImgView.setOnClickListener(fragment as View.OnClickListener)
        }

        override fun onSelected(downloadTask: DownloadTask) {
            itemView.apply {
                openFileImgView.visibility = View.INVISIBLE
                retryImgView.visibility = View.INVISIBLE
                deleteImgView.visibility = View.INVISIBLE
            }
        }

        override fun onNotSelected(downloadTask: DownloadTask) {
            itemView.apply {
                if(Utils.isDownloadedFileExist(context, downloadTask)){
                    openFileImgView.visibility = View.VISIBLE
                    retryImgView.visibility = View.GONE
                }else {
                    openFileImgView.visibility = View.GONE
                    retryImgView.visibility = View.VISIBLE
                }
                deleteImgView.visibility = View.VISIBLE
            }
        }
    }
}