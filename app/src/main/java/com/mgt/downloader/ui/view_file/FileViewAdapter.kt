package com.mgt.downloader.ui.view_file

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.ZipNode
import com.mgt.downloader.utils.smartLoad
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.item_file.view.*
import java.text.SimpleDateFormat
import java.util.*


class FileViewAdapter(private val fragment: Fragment, private val isLocalFile: Boolean) :
    RecyclerView.Adapter<FileViewAdapter.FileViewViewHolder>() {
    var zipNodes: List<ZipNode> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewViewHolder(view)
    }

    override fun getItemCount(): Int {
        return zipNodes.size
    }

    override fun onBindViewHolder(holder: FileViewViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class FileViewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int) {
            val zipNode = zipNodes[position]
            val zipEntry = zipNode.entry
            itemView.apply {
                val fileName = zipNode.name
                val fileExtension =
                    if (zipNode.isDirectory) "dir" else utils.getFileExtension(fileName)
                fileNameTextView.text = fileName

                val formatSize = utils.getFormatFileSize(zipNode.size)

                fileSizeTextView.text = formatSize
                if (zipNode.isDirectory) {
                    stroke.visibility = View.VISIBLE
                    itemCountTextView.visibility = View.VISIBLE
                    itemCountTextView.text = String.format(
                        context.getString(R.string.item_count),
                        zipNode.childNodes.size
                    )
                } else {
                    stroke.visibility = View.GONE
                    itemCountTextView.visibility = View.GONE
                }

                val iconUrl = utils.getIconUrlFromFileExtension(fileExtension)
                Picasso.get().smartLoad(iconUrl, fileIconImgView) {
                    it.placeholder(R.drawable.file)
                    it.error(R.drawable.file)
                }

                if (zipEntry != null) {
                    val lastModifiedDate = Date(zipEntry.time)
                    fileTimeTextView.text = String.format(
                        context.getString(R.string.time_format),
                        SimpleDateFormat.getDateInstance().format(lastModifiedDate),
                        SimpleDateFormat.getTimeInstance().format(lastModifiedDate)
                    )
                } else {
                    fileTimeTextView.text = null
                }

                if (isLocalFile) {
                    downloadImgView.visibility = View.INVISIBLE
                } else {
                    downloadImgView.setOnClickListener(fragment as View.OnClickListener)
                    fileIconImgView.setOnClickListener(fragment as View.OnClickListener)
                    setOnLongClickListener(fragment as View.OnLongClickListener)
                }

                setOnClickListener(fragment as View.OnClickListener)

                val indexOfZipNode =
                    (fragment as ViewFileDialog).selectedZipNodes.indexOfFirst { it.path == zipNode.path }
                if (indexOfZipNode != -1) {//contains
                    setBackgroundColor(ContextCompat.getColor(context, R.color.selectedBg))
                    downloadImgView.visibility = View.INVISIBLE
                } else {
                    background = null
                    if (!isLocalFile) {
                        downloadImgView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}