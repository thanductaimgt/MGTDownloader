package com.mgt.downloader.ui.download_list.downloading

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mgt.downloader.R
import com.mgt.downloader.base.BaseDownloadAdapter
import com.mgt.downloader.base.BaseDownloadFragment
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.serialize_model.DownloadTask
import kotlinx.android.synthetic.main.item_downloading.view.*


class DownloadingAdapter(
    fragment: BaseDownloadFragment,
    downloadTaskDiffUtil: DownloadTaskDiffUtil
) :
    BaseDownloadAdapter(
        fragment,
        downloadTaskDiffUtil
    ) {
    override fun onCreateCurViewHolder(parent: ViewGroup, viewType: Int): DownloadingHolder {
        return DownloadingHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_downloading, parent, false)
        )
    }

    override fun onBindViewHolderChange(
        holder: DownloadBaseHolder,
        position: Int,
        downloadTask: DownloadTask,
        payload: Any
    ) {
        holder as DownloadingHolder
        when (payload) {
            DownloadTask.PAYLOAD_PROGRESS -> holder.bindProgress(downloadTask)
            DownloadTask.PAYLOAD_STATE -> holder.bindState(downloadTask)
        }
    }

    inner class DownloadingHolder(itemView: View) : DownloadBaseHolder(itemView) {
        override fun bind(position: Int) {
            super.bind(position)
            val downloadTask = currentList[position]
            bindFileSize(downloadTask)
            bindProgress(downloadTask)
            bindState(downloadTask)
        }

        fun bindProgress(downloadTask: DownloadTask) {
            itemView.apply {
                if (downloadTask.isFileSizeKnown()) {
                    val downloadProgress = utils.getPercentage(
                        downloadTask.downloadedSize,
                        downloadTask.totalSize
                    ).toInt()

                    progressTextView.text = String.format("%d%%", downloadProgress)
                    progressAnimView.progress = downloadProgress / 100f

                    if (downloadTask.downloadedSize != 0L) {
                        remainingTimeTextView.text = String.format(
                            "%s %s",
                            context.getString(R.string.desc_remaining_time),
                            utils.getFormatTimeDiff(
                                context,
                                utils.getDownloadRemainingTimeInMilli(
                                    downloadTask.elapsedTime,
                                    downloadTask.totalSize,
                                    downloadTask.downloadedSize
                                )
                            )
                        )
                    }
                } else {
                    progressTextView.text = utils.getFormatFileSize(downloadTask.downloadedSize)
                }
            }
        }

        fun bindState(downloadTask: DownloadTask) {
            itemView.apply {
                when {
                    (downloadTask.state == DownloadTask.STATE_PERSISTENT_PAUSED
                            || downloadTask.state == DownloadTask.STATE_TEMPORARY_PAUSE) -> {
                        pauseResumeImgView.setImageResource(R.drawable.resume)
                        pauseResumeImgView.contentDescription =
                            context.getString(R.string.desc_resume)

//                        progressAnimView.pauseAnimation()

                        progressAnimView.visibility = View.INVISIBLE

                        stateTextView.visibility = View.VISIBLE
                    }
                    downloadTask.state == DownloadTask.STATE_DOWNLOADING -> {
                        pauseResumeImgView.setImageResource(R.drawable.pause)
                        pauseResumeImgView.contentDescription =
                            context.getString(R.string.desc_pause)

                        stateTextView.visibility = View.INVISIBLE

                        progressAnimView.visibility = View.VISIBLE
                        if (!downloadTask.isFileSizeKnown()) {
                            progressAnimView.setAnimation(R.raw.progress)
//                            progressAnimView.resumeAnimation()
                        } else {
                            progressAnimView.pauseAnimation()
                        }
                    }
                }
            }
        }

        private fun bindFileSize(downloadTask: DownloadTask) {
            itemView.apply {
                if (downloadTask.isFileSizeKnown()) {
                    remainingTimeTextView.visibility = View.VISIBLE
                    remainingTimeTextView.text = String.format(
                        "%s ...",
                        context.getString(R.string.desc_remaining_time)
                    )
                    stroke.visibility = View.VISIBLE
                } else {
                    remainingTimeTextView.visibility = View.GONE
                    stroke.visibility = View.GONE

                    if (downloadTask.state == DownloadTask.STATE_DOWNLOADING) {
                        progressAnimView.visibility = View.VISIBLE
                    } else {
                        progressAnimView.visibility = View.INVISIBLE
                    }
                }
            }
        }

        override fun bindSelectState(downloadTask: DownloadTask, isSelected: Boolean) {
            itemView.apply {
                if (isSelected) {
                    pauseResumeImgView.visibility = View.INVISIBLE
                    cancelImgView.visibility = View.INVISIBLE
                } else {
                    pauseResumeImgView.visibility = View.VISIBLE
                    cancelImgView.visibility = View.VISIBLE
                }
            }
        }

        override fun bindListeners() {
            super.bindListeners()
            itemView.apply {
                pauseResumeImgView.setOnClickListener(fragment as View.OnClickListener)
                cancelImgView.setOnClickListener(fragment as View.OnClickListener)
            }
        }
    }
}