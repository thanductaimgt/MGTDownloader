package com.mgt.downloader.ui.download_list

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.mgt.downloader.R
import com.mgt.downloader.base.ContainsSelectableList
import com.mgt.downloader.di.DI.liveConnection
import com.mgt.downloader.ui.MainActivity
import com.mgt.downloader.utils.TAG
import kotlinx.android.synthetic.main.dialog_download_list.*


class DownloadListFragment : DialogFragment(),
    View.OnClickListener {
    private lateinit var adapter: DownloadPagerAdapter

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
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                // if there are some selected items, discard all
                (childFragmentManager.fragments.firstOrNull { it.TAG == adapter.getTag(viewPager.currentItem) } as ContainsSelectableList?)?.let { fragment ->
                    if (fragment.getSelectableList().isNotEmpty()) {
                        fragment.removeAllItems()
                    } else {
                        super.onBackPressed()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_download_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()

        liveConnection.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                networkStateTextView.visibility = View.GONE
            } else {
                networkStateTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun initView() {
        // dialog full screen
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        adapter = DownloadPagerAdapter(
            this,
            childFragmentManager
        )

        viewPager.apply {
            adapter = this@DownloadListFragment.adapter
            offscreenPageLimit = 3
        }
        tabLayout.setupWithViewPager(viewPager)

        titleTextViewBottomSheetLayout.setOnClickListener(this@DownloadListFragment)

        settingsImgView.setOnClickListener(this@DownloadListFragment)
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.titleTextViewBottomSheetLayout -> dismiss()
            R.id.settingsImgView -> (activity as MainActivity).showSettingsDialog()
        }
    }
}