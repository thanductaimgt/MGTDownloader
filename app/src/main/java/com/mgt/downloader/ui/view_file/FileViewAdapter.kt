package com.mgt.downloader.ui.view_file

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_file.view.*
import com.mgt.downloader.R
import com.mgt.downloader.data_model.ZipNode
import com.mgt.downloader.utils.Utils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


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
            val zipEntry = zipNode.entry!!
            itemView.apply {
                val fileName = Utils.getFileName(zipEntry.name)
                val fileExtension =
                    if (zipEntry.isDirectory) "dir" else Utils.getFileExtension(fileName)
                fileNameTextView.text = fileName

                val formatSize = Utils.getFormatFileSize(zipNode.size)

                fileSizeTextView.text = formatSize
                if (zipEntry.isDirectory) {
                    stroke.visibility = View.VISIBLE
                    itemCountTextView.visibility = View.VISIBLE
                    itemCountTextView.text = String.format(
                        context.getString(R.string.item_count),
                        zipNode.childNodes.size
                    )
                }else{
                    stroke.visibility = View.GONE
                    itemCountTextView.visibility = View.GONE
                }

                fileIconImgView.setImageResource(
                    Utils.getResIdFromFileExtension(
                        context,
                        fileExtension
                    )
                )

                val lastModifiedDate = Date(zipEntry.time)
                fileTimeTextView.text = String.format(
                    context.getString(R.string.time_format),
                    SimpleDateFormat.getDateInstance().format(lastModifiedDate),
                    SimpleDateFormat.getTimeInstance().format(lastModifiedDate)
                )

                if (isLocalFile) {
                    downloadImgView.visibility = View.INVISIBLE
                } else {
                    downloadImgView.setOnClickListener(fragment as View.OnClickListener)
                    fileIconImgView.setOnClickListener(fragment as View.OnClickListener)
                    setOnLongClickListener(fragment as View.OnLongClickListener)
                }

                setOnClickListener(fragment as View.OnClickListener)

                val indexOfZipNode = (fragment as ViewFileDialog).selectedZipNodes.indexOfFirst { it.entry!!.name == zipEntry.name }
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