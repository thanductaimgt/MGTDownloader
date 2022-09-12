package com.mgt.downloader.ui.view_file

import android.animation.Animator
import android.app.Dialog
import android.content.DialogInterface
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
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieDrawable
import com.mgt.downloader.DownloadService
import com.mgt.downloader.R
import com.mgt.downloader.base.ContainsSelectableList
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.nonserialize_model.NullZipNode
import com.mgt.downloader.nonserialize_model.ZipNode
import com.mgt.downloader.serialize_model.DownloadTask
import com.mgt.downloader.ui.MainActivity
import com.mgt.downloader.utils.TAG
import kotlinx.android.synthetic.main.dialog_view_file.view.*
import java.sql.Date
import java.util.*


class ViewFileDialog : DialogFragment(),
    View.OnClickListener, View.OnLongClickListener,
    ContainsSelectableList {
    private lateinit var filePreviewInfo: FilePreviewInfo
    private val viewModel by viewModels<ViewFileViewModel>()
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                if (selectedZipNodes.isEmpty()) {
                    curZipNode?.parentNode?.let {
                        setCurrentNode(it)
                    } ?: super.onBackPressed()
                } else {
                    removeAllItems()
                }
            }
        }.also {
            it.requestWindowFeature(Window.FEATURE_NO_TITLE)
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

        (requireActivity() as MainActivity).liveDownloadService.observe(
            viewLifecycleOwner
        ) { downloadService ->
            this.downloadService = downloadService
        }

        viewModel.liveRootNode.observe(viewLifecycleOwner) {
            if (it == null) return@observe

            this.view?.animView?.cancelAnimation()
            this.view?.animView?.visibility = View.GONE
            if (it is ZipNode) {
                setCurrentNode(it)
            } else if (it is NullZipNode) {
                Toast.makeText(context, getString(R.string.error_occurred), Toast.LENGTH_SHORT)
                    .show()
            }
        }
        viewModel.buildZipTree(filePreviewInfo)
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
                override fun onAnimationRepeat(animator: Animator) {
                    startDownloadAnimView.reverseAnimationSpeed()
                }

                override fun onAnimationEnd(animator: Animator) {
                    startDownloadAnimView.reverseAnimationSpeed()
                    startDownloadAnimView.visibility = View.GONE
                }

                override fun onAnimationCancel(p0: Animator) {
                }

                override fun onAnimationStart(p0: Animator) {
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
                val position = requireView().fileViewRecyclerView.getChildLayoutPosition(view)
                if (selectedZipNodes.isEmpty()) {
                    val zipNode = fileViewAdapter.zipNodes[position]
                    if (zipNode.isDirectory) {
                        setCurrentNode(zipNode)
                    }
                } else {
                    addOrRemoveSelectedZipNode(position)
                }
            }
            R.id.pathTextView -> {
                val position = requireView().filePathRecyclerView.getChildLayoutPosition(view)
                val zipNode = filePathAdapter.zipNodes[position]
                setCurrentNode(zipNode)
            }
            R.id.downloadImgView -> {
                val position =
                    requireView().fileViewRecyclerView.getChildLayoutPosition(view.parent as View)
                val zipNode = fileViewAdapter.zipNodes[position]
                startDownload(zipNode)
            }
            R.id.fileIconImgView -> {
                val position =
                    requireView().fileViewRecyclerView.getChildLayoutPosition(view.parent as View)
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
                        requireView().fileViewRecyclerView.getChildLayoutPosition(view)
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
        if (!selectedZipNodes.removeAll { it.path == zipNode.path }) {
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
        var fileName = zipNode.name

        val stopCondition = { newFileName: String ->
            !(downloadService?.isFileOrDownloadTaskExist(newFileName) ?: false)
        }

        if (!stopCondition(fileName)) {
            fileName = utils.generateNewDownloadFileName(fileName, stopCondition)
        }

        val downloadTask = DownloadTask(
            fileName = fileName,
            displayUrl = filePreviewInfo.displayUri,
            downloadUrl = filePreviewInfo.downloadUri,
            startTime = Date(System.currentTimeMillis()),
            totalSize = zipNode.size,
            zipEntryName = zipNode.path,
            isDirectory = zipNode.isDirectory
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
        fileViewAdapter.zipNodes = zipNode.childNodes.sortedBy { !it.isDirectory }
        fileViewAdapter.notifyDataSetChanged()
        filePathAdapter.setCurrentNode(zipNode)
        filePathAdapter.curPosition?.let { view?.filePathRecyclerView?.scrollToPosition(it) }
    }

    fun show(fragmentManager: FragmentManager, zipPreviewInfo: FilePreviewInfo) {
        this.filePreviewInfo = zipPreviewInfo
        show(fragmentManager, TAG)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.liveRootNode.value = null
    }
}
