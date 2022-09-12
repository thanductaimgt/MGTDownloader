package com.mgt.downloader.base

import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.helper.DownloadTaskDiffUtil
import com.mgt.downloader.serialize_model.DownloadTask
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.smartLoad
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.item_download_base.view.*

abstract class BaseDownloadAdapter(
    val fragment: BaseDownloadFragment,
    downloadTaskDiffUtil: DownloadTaskDiffUtil
) :
    ListAdapter<DownloadTask, BaseDownloadAdapter.DownloadBaseHolder>(
        downloadTaskDiffUtil
    ) {
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadBaseHolder {
        return onCreateCurViewHolder(parent, viewType).apply {
            bindListeners()
        }
    }

    abstract fun onCreateCurViewHolder(parent: ViewGroup, viewType: Int): DownloadBaseHolder

    final override fun onBindViewHolder(holder: DownloadBaseHolder, position: Int) {
        holder.bind(position)
    }

    final override fun onBindViewHolder(
        holder: DownloadBaseHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            (payloads[0] as ArrayList<*>).forEach { payload ->
                val downloadTask = currentList[position]
                when (payload) {
                    DownloadTask.PAYLOAD_SELECT_STATE -> holder.bindSelectState(downloadTask)
                    DownloadTask.PAYLOAD_EXPAND_STATE -> holder.bindExpandState(downloadTask)
                    else -> onBindViewHolderChange(holder, position, downloadTask, payload)
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    open fun onBindViewHolderChange(
        holder: DownloadBaseHolder,
        position: Int,
        downloadTask: DownloadTask,
        payload: Any
    ) {
    }

    abstract inner class DownloadBaseHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        BindableViewHolder {
        override fun bind(position: Int) {
            val downloadTask = currentList[position]
            itemView.apply {
                urlTextView.text = downloadTask.displayUrl
                timeTextView.text = utils.getFormatTimeDiffTillNow(context, downloadTask.startTime)

                //bind file size
                if (downloadTask.isFileSizeKnown()) {
                    totalSizeTextView.text = utils.getFormatFileSize(downloadTask.totalSize)
                } else {
                    totalSizeTextView.text = context.getString(R.string.desc_unknown_size)
                }

                bindFilePreview(downloadTask)
                bindSelectState(downloadTask)
                bindExpandState(downloadTask)
            }
        }

        private fun bindFilePreview(downloadTask: DownloadTask) {
            itemView.apply {
                val fileExtension =
                    if (downloadTask.isDirectory) "dir" else utils.getFileExtension(downloadTask.fileName)

                if (downloadTask.thumbUrl != null) {
                    fileIconImgView.layoutParams =
                        (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                            width = 0
                        }

                    Picasso.get().smartLoad(downloadTask.thumbUrl, fileIconImgView) {
                        if (fileIconImgView.drawable != null) {
                            it.placeholder(fileIconImgView.drawable)
                        }
                    }

                    fileIconImgView.cornerRadius =
                        context.resources.getDimension(R.dimen.sizeRoundCornerRadiusItem)
                } else {
                    fileIconImgView.layoutParams =
                        (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                            width = context.resources.getDimension(R.dimen.sizeDownloadItemFileIcon)
                                .toInt()
                        }

                    val iconUrl = utils.getIconUrlFromFileExtension(fileExtension)

                    Picasso.get().smartLoad(iconUrl, fileIconImgView) {
                        it.placeholder(R.drawable.file)
                        it.error(R.drawable.file)
                    }

                    fileIconImgView.cornerRadius = 0f
                }

                playIcon.visibility = if (fileExtension == "mp4") View.VISIBLE else View.GONE

                fileNameTextView.text = downloadTask.fileName
            }
        }

        fun bindSelectState(downloadTask: DownloadTask) {
            (itemView as CardView).apply {
                val indexOfDownloadTask =
                    fragment.selectedDownloadTasks.indexOfFirst { it.fileName == downloadTask.fileName }
                if (indexOfDownloadTask != -1) {//contains
                    setCardBackgroundColor(ContextCompat.getColor(context, R.color.selectedBg))
                    bindSelectState(downloadTask, true)
                } else {
                    setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                    bindSelectState(downloadTask, false)
                }
            }
        }

        abstract fun bindSelectState(downloadTask: DownloadTask, isSelected: Boolean)

        fun bindExpandState(downloadTask: DownloadTask) {
            itemView.apply {
                if (fragment.expandedDownloadTasksName.contains(downloadTask.fileName)) {
                    urlTextView.maxLines = Constants.MAX_LINES_ITEM_EXPANDED
                    fileNameTextView.maxLines = Constants.MAX_LINES_ITEM_EXPANDED

                    arrowImgView.setImageResource(R.drawable.collapse)
                    arrowImgView.contentDescription = context.getString(R.string.desc_collapse)

                    fileIconImgView.layoutParams =
                        (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                            dimensionRatio =
                                if (downloadTask.thumbUrl == null) "1:1" else "H,${downloadTask.thumbRatio}"
                        }
                } else {
                    urlTextView.maxLines = Constants.MAX_LINES_ITEM_COLLAPSED
                    fileNameTextView.maxLines = Constants.MAX_LINES_ITEM_COLLAPSED

                    arrowImgView.setImageResource(R.drawable.expand)
                    arrowImgView.contentDescription = context.getString(R.string.desc_expand)

                    fileIconImgView.layoutParams =
                        (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                            dimensionRatio = if (utils.isSmallerRatio(
                                    "1:1",
                                    downloadTask.thumbRatio
                                )
                            ) downloadTask.thumbRatio else "1:1"
                        }
                }

                bindFilePreview(downloadTask)
            }
        }

        open fun bindListeners() {
            itemView.apply {
                setOnClickListener(fragment as View.OnClickListener)
                setOnLongClickListener(fragment as View.OnLongClickListener)
                fileIconImgView.setOnClickListener(fragment as View.OnClickListener)
            }
        }
    }

    override fun onViewRecycled(holder: DownloadBaseHolder) {
        holder.itemView.apply {
            fileIconImgView.layoutParams =
                (fileIconImgView.layoutParams as ConstraintLayout.LayoutParams).apply {
                    width = context.resources.getDimension(R.dimen.sizeDownloadItemFileIcon)
                        .toInt()
                    dimensionRatio = "1:1"
                }
            fileIconImgView.setImageDrawable(null)
        }
    }
}