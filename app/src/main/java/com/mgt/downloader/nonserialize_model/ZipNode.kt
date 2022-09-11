package com.mgt.downloader.nonserialize_model

import com.mgt.downloader.App
import com.mgt.downloader.di.DI.utils
import java.util.zip.ZipEntry

data class ZipNode(
    var entry: ZipEntry? = null,
    var parentNode: ZipNode? = null,
    var level: Int = 0,
    var childNodes: ArrayList<ZipNode> = ArrayList(),
    var size: Long = 0
) : NullableZipNode {
    fun insertEntry(entry: ZipEntry, level: Int): ZipNode? {
        return if (level > this.level) {
            ZipNode(entry, this, level).also { childNodes.add(it) }
        } else {
            parentNode?.insertEntry(entry, level)
        }
    }

    fun getPath(): ArrayList<ZipNode> {
        return (parentNode?.getPath() ?: arrayListOf()).apply { add(this@ZipNode) }
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
            val curRootName = utils.getPathRoot(path)
            val newPath =
                if (path.length > curRootName.length) path.drop(curRootName.length + 1) else ""
            childNodes.forEach {
                if (utils.getFileName(it.entry?.name.orEmpty()) == utils.getFileName(curRootName)) {
                    return it.getNode(newPath)
                }
            }
            throw Throwable("entry not found")
        }
    }

    companion object {
        //network uri only, not local
        fun getZipTree(url: String, downloadUrl: String): ZipNode {
            val rootNode = App.zipTreeCaches[url]
            return if (rootNode != null) {
                rootNode
            } else {
                val pair = utils.getZipCentralDirInfo(url)
                val centralDirOffset = pair.first
                val centralDirSize = pair.second

                val fileName = utils.getFileName(url, "zip")

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
            val rootNode = App.zipTreeCaches[zipPreviewInfo.displayUri]
            return if (rootNode != null) {
                rootNode
            } else {
                val zipEntries = utils.getZipEntries(zipPreviewInfo)
                parseZipTree(zipEntries)
            }
        }

        private fun parseZipTree(zipEntries: List<ZipEntry>): ZipNode {
            val rootNode = ZipNode()
            var lastInsertedNode: ZipNode? = rootNode

            zipEntries.forEach { zipEntry ->
                lastInsertedNode = lastInsertedNode?.insertEntry(
                    zipEntry,
                    utils.getPathLevel(zipEntry.name)
                )
            }
            return rootNode.apply { initNodesSize() }
        }
    }
}

interface NullableZipNode

class NullZipNode : NullableZipNode