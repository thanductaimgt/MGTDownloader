package com.mgt.downloader.ui

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.mgt.downloader.DownloadService
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.utils.TAG
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_file_name.view.*
import kotlin.math.max


class FileNameDialog : DialogFragment(),
    View.OnClickListener {
    private lateinit var filePreviewInfo: FilePreviewInfo
    private var downloadService: DownloadService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return inflater.inflate(R.layout.dialog_file_name, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView(view)

        (requireActivity() as MainActivity).liveDownloadService.observe(
            viewLifecycleOwner
        ) { downloadService ->
            this.downloadService = downloadService
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun initView(view: View) {
        view.apply {
            urlTextView.text = filePreviewInfo.displayUri

            nameEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(editable: Editable) {
                    val fileName = editable.toString()
                    when {
                        fileName == "" -> {
                            warningTextView.visibility = View.GONE
                            downloadButton.isEnabled = false
                        }
                        fileName.contains('/') -> {
                            warningTextView.text = getString(R.string.desc_invalid_file_name)
                            warningTextView.visibility = View.VISIBLE
                            downloadButton.isEnabled = false
                        }
                        isAnyFileOrDownloadTaskWithSameName(fileName) -> {
                            warningTextView.text = getString(R.string.desc_file_name_exists)
                            warningTextView.visibility = View.VISIBLE
                            downloadButton.isEnabled = false
                        }
                        else -> {
                            warningTextView.visibility = View.GONE
                            downloadButton.isEnabled = true
                        }
                    }
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

            })

            nameEditText.setOnFocusChangeListener { _, b ->
                if (b) {
                    nameEditText.post {
                        utils.showKeyboard(context, nameEditText)
                    }
                } else {
                    utils.hideKeyboard(context, this)
                }
            }
            nameEditText.requestFocus()

            downloadButton.isEnabled = false

            downloadButton.setOnClickListener(this@FileNameDialog)
            cancelImgView.setOnClickListener(this@FileNameDialog)
            cancelImgView.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        view?.apply {
            nameEditText.setText(filePreviewInfo.name)
            nameEditText.setSelection(
                0,
                max(
                    0,
                    filePreviewInfo.name.length - utils.getFileExtension(filePreviewInfo.name).length.let { if (it > 0) it + 1 else 0 }
                )
            )
        }
    }

    override fun onPause() {
        super.onPause()
        filePreviewInfo.name = view?.nameEditText?.text.toString()
    }

    fun show(fragmentManager: FragmentManager, filePreviewInfo: FilePreviewInfo) {
        this.filePreviewInfo = filePreviewInfo.copy()
        show(fragmentManager, TAG)
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.downloadButton -> startDownloadTaskAndDismiss()
            R.id.cancelImgView -> dismiss()
        }
    }

    private fun isAnyFileOrDownloadTaskWithSameName(fileName: String): Boolean {
        return downloadService?.isFileOrDownloadTaskExist(fileName) ?: false
    }

    private fun startDownloadTaskAndDismiss() {
        (activity as MainActivity).startDownloadTask(filePreviewInfo.apply {
            name = view?.nameEditText?.text.toString()
        })
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        utils.hideKeyboard(requireContext(), (activity as MainActivity).rootView)
        super.onDismiss(dialog)
    }
}