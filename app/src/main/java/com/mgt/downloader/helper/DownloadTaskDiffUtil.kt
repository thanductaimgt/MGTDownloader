package com.mgt.downloader.helper

import androidx.recyclerview.widget.DiffUtil
import com.mgt.downloader.data_model.DownloadTask

class DownloadTaskDiffUtil : DiffUtil.ItemCallback<DownloadTask>() {
    override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
        return oldItem.fileName == newItem.fileName
    }

    override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: DownloadTask, newItem: DownloadTask): Any? {
        val res = ArrayList<Int>()
        if (oldItem.state != newItem.state) {
            res.add(DownloadTask.PAYLOAD_STATE)
        }
        if (oldItem.downloadedSize != newItem.downloadedSize) {
            res.add(DownloadTask.PAYLOAD_PROGRESS)
        }
//        if (oldItem.totalSize != newItem.totalSize) {
//            res.add(DownloadTask.PAYLOAD_SIZE)
//        }
        return res
    }
}