package com.mgt.downloader.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.mgt.downloader.R
import com.mgt.downloader.utils.TAG
import kotlinx.android.synthetic.main.dialog_alert.*


class AlertDialog : DialogFragment(),
    View.OnClickListener {
    var title: String? = null
    var description: String? = null
    var positiveButtonText = "OK"
    var negativeButtonText = "Cancel"
    var positiveButtonClickListener: (() -> Unit)? = null
    var negativeButtonClickListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return inflater.inflate(R.layout.dialog_alert, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun initView() {
        titleTextView.text = title
        descTextView.text = description
        positiveButton.text = positiveButtonText
        negativeButton.text = negativeButtonText

        positiveButton.setOnClickListener(this)
        negativeButton.setOnClickListener(this)
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.positiveButton -> {
                positiveButtonClickListener?.invoke()
                dismiss()
            }
            R.id.negativeButton -> {
                negativeButtonClickListener?.invoke()
                dismiss()
            }
        }
    }
}