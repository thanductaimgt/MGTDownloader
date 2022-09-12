package com.mgt.downloader.nonserialize_model

import com.mgt.downloader.App
import com.mgt.downloader.di.DI.utils
import java.util.zip.ZipEntry

data class ZipNode(
    var entry: ZipEntry? = null,
    var size: Long = 0,
    var path: String = "",
    var name: String = "",
) : NullableZipNode {
    var parentNode: ZipNode? = null
    var childNodes: ArrayList<ZipNode> = ArrayList()

    val isDirectory: Boolean
        get() = path.endsWith('/')

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
                if (it.name == utils.getFileName(curRootName)) {
                    return it.getNode(newPath)
                }
            }
            throw Throwable("entry not found")
        }
    }

    private fun insertZipEntryInternal(
        zipEntry: ZipEntry,
        remPath: String = zipEntry.name,
    ) {
        if (remPath.isEmpty()) return

        val preSubPath = remPath.takeWhile { it != '/' }
        val nextRemPath =
            remPath.substring((preSubPath.length + 1).coerceAtMost(remPath.length))

        if (preSubPath.isEmpty()) {
            return insertZipEntryInternal(zipEntry, nextRemPath)
        }

        var positionToInsert = childNodes.size

        childNodes.forEachIndexed { index, childNode ->
            if (preSubPath == childNode.name) {
                childNode.insertZipEntryInternal(zipEntry, nextRemPath)
                return
            } else if (preSubPath < childNode.name) {
                positionToInsert = index
                return@forEachIndexed
            }
        }

        val indexOfSeparator = remPath.indexOf('/')
        if (indexOfSeparator in 0 until remPath.lastIndex) {
            val newNodePath = if (parentNode != null) {
                "$path$preSubPath/"
            } else {
                "$preSubPath/"
            }
            val newNode = ZipNode(
                entry = null,
                size = 0,
                path = newNodePath,
                name = preSubPath,
            ).apply {
                parentNode = this@ZipNode
            }
            childNodes.add(positionToInsert, newNode)
            newNode.insertZipEntryInternal(zipEntry, nextRemPath)
        } else {
            val name = if (zipEntry.isDirectory) {
                zipEntry.name.dropLast(1).takeLastWhile { it != '/' }
            } else {
                zipEntry.name.takeLastWhile { it != '/' }
            }
            childNodes.add(ZipNode(
                entry = zipEntry,
                size = zipEntry.size,
                path = zipEntry.name,
                name = name
            ).apply {
                parentNode = this@ZipNode
            })
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
                val zipEntries = utils.getZipEntries(zipPreviewInfo).sortedBy { it.name }
                val res = parseZipTree(zipEntries)
                App.zipTreeCaches[zipPreviewInfo.displayUri] = res
                res
            }
        }

        private fun parseZipTree(zipEntries: List<ZipEntry>): ZipNode {
            val rootNode = ZipNode(path = "/")

            zipEntries.forEach { zipEntry ->
                rootNode.insertZipEntryInternal(zipEntry)
            }
            return rootNode.apply { initNodesSize() }
        }
    }
}

interface NullableZipNode

class NullZipNode : NullableZipNode