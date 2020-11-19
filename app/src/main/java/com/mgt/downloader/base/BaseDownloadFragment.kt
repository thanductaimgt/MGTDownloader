package com.mgt.downloader.base

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.mgt.downloader.ui.MainActivity
import com.mgt.downloader.R
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.DownloadService
import com.mgt.downloader.ui.view_file.ViewFileDialog
import com.mgt.downloader.utils.Constants
import com.mgt.downloader.utils.Utils
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

abstract class BaseDownloadFragment : Fragment(), View.OnClickListener,
    View.OnLongClickListener,
    ContainsSelectableList {
    abstract val adapter: BaseDownloadAdapter
    protected var downloadService: DownloadService? = null
    protected lateinit var viewFileDialog: ViewFileDialog
    var selectedDownloadTasks = LinkedList<DownloadTask>()
    var expandedDownloadTasksName = HashSet<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()

        (activity!! as MainActivity).liveDownloadService.observe(
            viewLifecycleOwner,
            { downloadService ->
                this.downloadService = downloadService
                this.downloadService?.liveDownloadTasks?.observe(
                    viewLifecycleOwner,
                    { downloadTasks ->
                        this.view?.let { onDownloadListChange(downloadTasks)}
                    })
            })
    }

    abstract fun initView()

    abstract fun onDownloadListChange(downloadTasks: HashMap<String, DownloadTask>)

    final override fun onClick(view: View) {
        when (view.id) {
            R.id.retryAllImgView -> {
                selectedDownloadTasks.forEach {
                    downloadService?.retryDownloadTask(it.fileName)
                }
                removeAllItems()
            }
            R.id.deleteAllImgView -> {
                displayPopupMenu(view, selectedDownloadTasks)
            }
            R.id.discardAllImgView -> {
                removeAllItems()
            }
            R.id.selectAllImgView -> {
                selectAllItems()
            }
            else -> onClickView(view)
        }
    }

    open fun onClickView(view: View) {

    }

    protected fun addOrRemoveSelectedDownloadTask(position: Int) {
        val downloadTask = adapter.currentList[position]
        // if selectedZipNodes not contains zipNode ...
        if (!selectedDownloadTasks.removeAll { it.fileName == downloadTask.fileName }) {
            selectedDownloadTasks.add(downloadTask)
        }
        updateSelectLayout()
        adapter.notifyItemChanged(position, arrayListOf(DownloadTask.PAYLOAD_SELECT_STATE))
    }

    override fun getSelectableList(): List<*> {
        return selectedDownloadTasks
    }

    override fun removeAllItems() {
        selectedDownloadTasks.clear()
        updateSelectLayout()
        adapter.notifyDataSetChanged()
    }

    override fun selectAllItems() {
        selectedDownloadTasks = LinkedList(adapter.currentList)
        updateSelectLayout()
        adapter.notifyDataSetChanged()
    }

    abstract fun updateSelectLayout()

    protected fun displayPopupMenu(view: View, downloadTasks: List<DownloadTask>) {
        //Creating the instance of PopupMenu
        val popupMenu = PopupMenu(context!!, view)

        popupMenu.menu.add(
            0,
            Constants.MENU_ITEM_DELETE_FROM_LIST,
            1,
            getString(R.string.label_delete_from_list)
        )
        //check file existence, if exist add two more options
        if (downloadTasks.any { Utils.isDownloadedFileExist(context!!, it) }) {
            popupMenu.menu.add(
                0,
                Constants.MENU_ITEM_DELETE_FROM_STORAGE,
                2,
                getString(R.string.label_delete_from_storage)
            )
            popupMenu.menu.add(
                0,
                Constants.MENU_ITEM_DELETE_FROM_BOTH,
                3,
                getString(R.string.label_delete_from_both)
            )
        }

        //registering popup with OnMenuItemClickListener
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                Constants.MENU_ITEM_DELETE_FROM_LIST -> {
                    deleteDownloadTasksFromList(downloadTasks)
                }
                Constants.MENU_ITEM_DELETE_FROM_STORAGE -> {
                    deleteFilesFromStorage(downloadTasks)
                }
                Constants.MENU_ITEM_DELETE_FROM_BOTH -> {
                    deleteFilesFromStorage(downloadTasks)
                    deleteDownloadTasksFromList(downloadTasks)
                    removeAllItems()
                }
            }
            true
        }

        popupMenu.show() //showing popup menu
    }

    private fun deleteDownloadTasksFromList(
        downloadTasks: List<DownloadTask>
    ) {
        var areAllDeleteSuccess = true
        var count = 0
        var isToasted = false
        val total = downloadTasks.size
        downloadTasks.forEach { downloadTask ->
            downloadService?.deleteDownloadTaskFromList(downloadTask.fileName) { isCurDeleteSuccess ->
                if (!isCurDeleteSuccess) {
                    if (!isToasted) {
                        Toast.makeText(
                            context!!, getString(R.string.desc_can_not_delete_item),
                            Toast.LENGTH_SHORT
                        ).show()
                        isToasted = true
                    }
                    areAllDeleteSuccess = false
                }
                synchronized(count) {
                    count++
                }
                if (count == total) {
                    if (areAllDeleteSuccess) {
                        Toast.makeText(
                            context!!, getString(R.string.desc_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    removeAllItems()
                }
            }
        }
    }

    private fun deleteFilesFromStorage(
        downloadTasks: List<DownloadTask>
    ) {
        var areAllDeleteSuccess = true
        var count = 0
        var isToasted = false
        val total = downloadTasks.size
        downloadTasks.forEach { downloadTask ->
            downloadService?.deleteFileOrDirFromStorage(downloadTask.fileName) { isCurDeleteSuccess ->
                if (!isCurDeleteSuccess) {
                    if (!isToasted) {
                        Toast.makeText(
                            context!!, getString(R.string.desc_can_not_delete_file),
                            Toast.LENGTH_SHORT
                        ).show()
                        isToasted = true
                    }
                    areAllDeleteSuccess = false
                }
                synchronized(count) {
                    count++
                }
                if (count == total) {
                    if (areAllDeleteSuccess) {
                        Toast.makeText(
                            context!!, getString(R.string.desc_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    removeAllItems()
                }
            }
        }
    }
}