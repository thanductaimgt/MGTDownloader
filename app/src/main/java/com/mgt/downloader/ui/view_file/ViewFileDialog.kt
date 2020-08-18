package com.mgt.downloader.ui.view_file

import android.animation.Animator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieDrawable
import kotlinx.android.synthetic.main.dialog_view_file.view.*
import com.mgt.downloader.R
import com.mgt.downloader.ui.MainActivity
import com.mgt.downloader.factory.ViewModelFactory
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.data_model.ZipNode
import com.mgt.downloader.DownloadService
import com.mgt.downloader.base.ContainsSelectableList
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.Utils
import java.sql.Date
import java.util.*


class ViewFileDialog(private val fm: FragmentManager) : DialogFragment(),
    View.OnClickListener, View.OnLongClickListener,
    ContainsSelectableList {
    private lateinit var filePreviewInfo: FilePreviewInfo
    private lateinit var viewModel: ViewFileViewModel
    var curZipNode: ZipNode? = null
    private lateinit var fileViewAdapter: FileViewAdapter
    private lateinit var filePathAdapter: FilePathAdapter
    var selectedZipNodes = LinkedList<ZipNode>()

    private var downloadService: DownloadService? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun setupDialog(dialog: Dialog, style: Int) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(activity!!, theme) {
            override fun onBackPressed() {
                if (selectedZipNodes.isEmpty()) {
                    if (curZipNode!!.parentNode != null) {
                        setCurrentNode(curZipNode!!.parentNode!!)
                    } else {
                        super.onBackPressed()
                    }
                } else {
                    removeAllItems()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_view_file, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView(view)

        (activity!! as MainActivity).liveDownloadService.observe(
            viewLifecycleOwner,
            Observer { downloadService ->
                this.downloadService = downloadService
            })

        viewModel = ViewModelProvider(this, ViewModelFactory.getInstance(filePreviewInfo)).get(
            ViewFileViewModel::class.java
        )

        viewModel.liveRootNode.observe(viewLifecycleOwner, Observer {
            this.view?.animView?.cancelAnimation()
            this.view?.animView?.visibility = View.GONE
            if (it is ZipNode) {
                setCurrentNode(it)
            } else {
                Toast.makeText(context, getString(R.string.error_occurred), Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun initView(view: View) {
        // dialog full screen
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.apply {
            fileNameTextView.text = filePreviewInfo.name

            fileViewAdapter =
                FileViewAdapter(
                    this@ViewFileDialog,
                    filePreviewInfo.isLocalFile
                )
            fileViewRecyclerView.apply {
                adapter = this@ViewFileDialog.fileViewAdapter
                layoutManager = LinearLayoutManager(context)
                setItemViewCacheSize(30)
            }

            filePathAdapter =
                FilePathAdapter(this@ViewFileDialog)
            filePathRecyclerView.apply {
                adapter = this@ViewFileDialog.filePathAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                setItemViewCacheSize(30)
            }

            animView.repeatCount = LottieDrawable.INFINITE
            animView.visibility = View.VISIBLE
            animView.playAnimation()

            startDownloadAnimView.addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(p0: Animator?) {
                    startDownloadAnimView.reverseAnimationSpeed()
                }

                override fun onAnimationEnd(p0: Animator?) {
                    startDownloadAnimView.reverseAnimationSpeed()
                    startDownloadAnimView.visibility = View.GONE
                }

                override fun onAnimationCancel(p0: Animator?) {
                }

                override fun onAnimationStart(p0: Animator?) {
                }
            })

            titleLayout.setOnClickListener(this@ViewFileDialog)
            selectAllImgView.setOnClickListener(this@ViewFileDialog)
            discardAllImgView.setOnClickListener(this@ViewFileDialog)
            downloadAllImgView.setOnClickListener(this@ViewFileDialog)
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.itemRootView -> {
                val position = this.view!!.fileViewRecyclerView.getChildLayoutPosition(view)
                if (selectedZipNodes.isEmpty()) {
                    val zipNode = fileViewAdapter.zipNodes[position]
                    if (zipNode.entry!!.isDirectory) {
                        setCurrentNode(zipNode)
                    }
                } else {
                    addOrRemoveSelectedZipNode(position)
                }
            }
            R.id.pathTextView -> {
                val position = this.view!!.filePathRecyclerView.getChildLayoutPosition(view)
                val zipNode = filePathAdapter.zipNodes[position]
                setCurrentNode(zipNode)
            }
            R.id.downloadImgView -> {
                val position =
                    this.view!!.fileViewRecyclerView.getChildLayoutPosition(view.parent as View)
                val zipNode = fileViewAdapter.zipNodes[position]
                startDownload(zipNode)
            }
            R.id.fileIconImgView -> {
                val position =
                    this.view!!.fileViewRecyclerView.getChildLayoutPosition(view.parent as View)
                addOrRemoveSelectedZipNode(position)
            }
            R.id.downloadAllImgView -> {
                selectedZipNodes.forEach { startDownload(it) }
                removeAllItems()
            }
            R.id.discardAllImgView -> {
                removeAllItems()
            }
            R.id.selectAllImgView -> {
                selectAllItems()
            }
            R.id.titleLayout -> {
                selectedZipNodes.clear()
                dismiss()
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (!filePreviewInfo.isLocalFile) {
            when (view.id) {
                R.id.itemRootView -> {
                    val position =
                        this.view!!.fileViewRecyclerView.getChildLayoutPosition(view)
                    addOrRemoveSelectedZipNode(position)
                    return true
                }
            }
        }
        return false
    }

    private fun addOrRemoveSelectedZipNode(position: Int) {
        val zipNode = fileViewAdapter.zipNodes[position]
        // if selectedZipNodes not contains zipNode ...
        if (!selectedZipNodes.removeAll { it.entry!!.name == zipNode.entry!!.name }) {
            selectedZipNodes.add(zipNode)
        }
        updateSelectLayout()
        fileViewAdapter.notifyItemChanged(position)
    }

    override fun getSelectableList(): List<*> {
        return selectedZipNodes
    }

    override fun removeAllItems() {
        selectedZipNodes.clear()
        updateSelectLayout()
        fileViewAdapter.notifyDataSetChanged()
    }

    override fun selectAllItems() {
        selectedZipNodes = LinkedList(fileViewAdapter.zipNodes)
        updateSelectLayout()
        fileViewAdapter.notifyDataSetChanged()
    }

    private fun showSelectLayout() {
        view?.selectLayout?.apply {
            if (visibility == View.INVISIBLE) {
                visibility = View.VISIBLE
            }
        }
    }

    private fun hideSelectLayout() {
        view?.selectLayout?.apply {
            if (visibility == View.VISIBLE) {
                visibility = View.INVISIBLE
            }
        }
    }

    private fun updateSelectLayout() {
        if (selectedZipNodes.isEmpty()) {
            hideSelectLayout()
        } else {
            view?.selectCountTextView?.text =
                String.format(getString(R.string.item_count), selectedZipNodes.size)
            showSelectLayout()
        }
    }

    private fun startDownload(zipNode: ZipNode) {
        val filePreviewInfo = FilePreviewInfo(
            Utils.getFileName(zipNode.entry!!.name, "zip"),
            filePreviewInfo.displayUri,
            filePreviewInfo.downloadUri,
            zipNode.size,
            filePreviewInfo.centralDirOffset,
            filePreviewInfo.centralDirSize
        )

        var fileName = Utils.getFileName(zipNode.entry!!.name)

        val stopCondition = { newFileName: String ->
            !(downloadService?.isFileOrDownloadTaskExist(newFileName)?:false)
        }

        if (!stopCondition(fileName)) {
            fileName = Utils.generateNewDownloadFileName(context!!, fileName, stopCondition)
        }

        val downloadTask = DownloadTask(
            fileName = fileName,
            displayUrl = filePreviewInfo.displayUri,
            downloadUrl = filePreviewInfo.downloadUri,
            startTime = Date(System.currentTimeMillis()),
            totalSize = zipNode.size,
            zipEntryName = zipNode.entry!!.name,
            isDirectory = zipNode.entry!!.isDirectory
        )

        (activity as MainActivity).startDownloadTask(downloadTask)

        showStartDownloadAnimation()
    }

    private fun showStartDownloadAnimation() {
        view?.startDownloadAnimView?.apply {
            visibility = View.VISIBLE
            playAnimation()
        }
    }

    fun setCurrentNode(zipNode: ZipNode) {
        curZipNode = zipNode
        fileViewAdapter.zipNodes = zipNode.childNodes.sortedBy { !it.entry!!.isDirectory }
        fileViewAdapter.notifyDataSetChanged()
        filePathAdapter.setCurrentNode(zipNode)
        filePathAdapter.curPosition?.let { view?.filePathRecyclerView?.scrollToPosition(it) }
    }

    fun show(zipPreviewInfo: FilePreviewInfo) {
        this.filePreviewInfo = zipPreviewInfo
        show(fm, TAG)
    }
}