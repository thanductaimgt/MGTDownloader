package com.mgt.downloader.data_model

import com.mgt.downloader.MyApplication
import com.mgt.downloader.utils.Utils
import java.util.zip.ZipEntry

data class ZipNode(
    var entry: ZipEntry? = null,
    var parentNode: ZipNode? = null,
    var level: Int = 0,
    var childNodes: ArrayList<ZipNode> = ArrayList(),
    var size: Long = 0
) : NullableZipNode {
    fun insertEntry(entry: ZipEntry, level: Int): ZipNode {
        return if (level > this.level) {
            ZipNode(entry, this, level).also { childNodes.add(it) }
        } else {
            parentNode!!.insertEntry(entry, level)
        }
    }

    fun getPath(): ArrayList<ZipNode> {
        return if (parentNode == null) {
            ArrayList()
        } else {
            parentNode!!.getPath()
        }.apply { add(this@ZipNode) }
    }

    fun initNodesSize(): Long {
        return if (this.childNodes.isEmpty()) {
            (this.entry?.size ?: 0).apply { size = this }
        } else {
            var curNodeSize = 0L
            childNodes.forEach {
                curNodeSize += it.initNodesSize()
            }
            curNodeSize.apply { size = this }
        }
    }

    fun getNode(path: String): ZipNode {
        return if (path == "") {
            this
        } else {
            val curRootName = Utils.getPathRoot(path)
            val newPath = if (path.length > curRootName.length) path.drop(curRootName.length + 1) else ""
            childNodes.forEach {
                if (Utils.getFileName(it.entry!!.name) == Utils.getFileName(curRootName)) {
                    return it.getNode(newPath)
                }
            }
            throw Throwable("entry not found")
        }
    }

    companion object {
        //network uri only, not local
        fun getZipTree(url: String, downloadUrl:String): ZipNode {
            val rootNode = MyApplication.zipTreeCaches[url]
            return if (rootNode != null) {
                rootNode
            } else {
                val pair = Utils.getZipCentralDirInfo(url)
                val centralDirOffset = pair.first
                val centralDirSize = pair.second

                val fileName = Utils.getFileName(url, "zip")

                val filePreviewInfo = FilePreviewInfo(
                    fileName,
                    url,
                    downloadUrl,
                    centralDirOffset = centralDirOffset,
                    centralDirSize = centralDirSize
                )
                getZipTree(filePreviewInfo).also { it.initNodesSize() }
            }
        }

        fun getZipTree(zipPreviewInfo: FilePreviewInfo): ZipNode {
            val rootNode = MyApplication.zipTreeCaches[zipPreviewInfo.displayUri]
            return if (rootNode != null) {
                rootNode
            } else {
                val zipEntries = Utils.getZipEntries(zipPreviewInfo)
                parseZipTree(zipEntries)
            }
        }

        private fun parseZipTree(zipEntries: List<ZipEntry>): ZipNode {
            val rootNode = ZipNode()
            var lastInsertedNode = rootNode

            zipEntries.forEach { zipEntry ->
                lastInsertedNode = lastInsertedNode.insertEntry(
                    zipEntry,
                    Utils.getPathLevel(zipEntry.name)
                )
            }
            return rootNode.apply { initNodesSize() }
        }
    }
}

interface NullableZipNode

class NullZipNode : NullableZipNode