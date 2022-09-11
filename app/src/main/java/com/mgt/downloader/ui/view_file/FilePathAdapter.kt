package com.mgt.downloader.ui.view_file

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.ZipNode
import kotlinx.android.synthetic.main.item_path.view.*

class FilePathAdapter(private val fragment: Fragment) :
    RecyclerView.Adapter<FilePathAdapter.FilePathViewHolder>() {
    var zipNodes: ArrayList<ZipNode> = ArrayList()
    var curPosition: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilePathViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_path, parent, false)
        return FilePathViewHolder(view)
    }

    override fun getItemCount(): Int {
        return zipNodes.size
    }

    override fun onBindViewHolder(holder: FilePathViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class FilePathViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int) {
            val fileName = zipNodes[position].entry?.name?.let { utils.getFileName(it) }
                ?: fragment.getString(R.string.root)
            itemView.apply {
                pathTextView.apply {
                    text = when (position) {
                        0 -> "$fileName  > "
                        zipNodes.lastIndex -> " $fileName"
                        else -> " $fileName  > "
                    }
                    if (position == curPosition) {
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(context, R.color.lightPrimary))
                    } else {
                        setTypeface(null, Typeface.NORMAL)
                        setTextColor(ContextCompat.getColor(context, R.color.lightPrimaryDark))
                    }
                }

                setOnClickListener(fragment as View.OnClickListener)
            }
        }
    }

    fun setCurrentNode(zipNode: ZipNode) {
        var position = zipNodes.indexOf(zipNode)
        if (position == -1) {
            zipNodes = zipNode.getPath()
            position = zipNodes.lastIndex
        }
        curPosition = position
        notifyDataSetChanged()
    }
}