package com.mgt.downloader.ui

import android.app.Dialog
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.play.core.review.ReviewInfo
import com.mgt.downloader.BuildConfig
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.downloadConfig
import com.mgt.downloader.di.DI.reviewManager
import com.mgt.downloader.di.DI.statistics
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.utils.DownloadConfig
import com.mgt.downloader.utils.Prefs
import com.mgt.downloader.utils.TAG
import kotlinx.android.synthetic.main.dialog_settings.*
import kotlinx.android.synthetic.main.dialog_settings.view.*


class SettingsDialog : DialogFragment(),
    View.OnClickListener {
    var selectedMaxConcurDownloadNum: Int = 0
    var selectedMultiThreadDownloadNum: Int = 0
    var changedConfigs = HashSet<String>()
    private var reviewInfo: ReviewInfo? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()

        // pre-load reviewInfo
        reviewManager.requestReviewFlow()
            .addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    reviewInfo = request.result
                }
            }
    }

    private fun initView() {
        // dialog full screen
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        initSpinner(
            maxConcurDownloadNumSpinner,
            Prefs.MAX_CONCUR_DOWNLOAD_NUM_KEY
        )
        initSpinner(
            multiThreadDownloadNumSpinner,
            Prefs.MULTI_THREAD_DOWNLOAD_NUM_KEY
        )

        successDownloadNumTextView.text = statistics.successDownloadNum.toString()
        cancelOrFailDownloadNumTextView.text = statistics.cancelOrFailDownloadNum.toString()
        totalDownloadNumTextView.text = statistics.totalDownloadNum.toString()
        totalDownloadSizeTextView.text = utils.getFormatFileSize(statistics.totalDownloadSize)

        pathTextView.text = utils.getDownloadDirRelativePath()
            ?: getString(R.string.path_not_available)

        val pInfo: PackageInfo =
            requireContext().packageManager.getPackageInfo(
                requireContext().applicationContext.packageName,
                0
            )
        aboutTextView.text =
            String.format(getString(R.string.desc_about_info), pInfo.versionName)

//        noteTextView.visibility =
//            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) View.VISIBLE else View.GONE
        noteTextView.visibility = View.GONE

        titleLayout.setOnClickListener(this@SettingsDialog)
        applyButton.setOnClickListener(this@SettingsDialog)
        rateTextView.setOnClickListener(this@SettingsDialog)
    }

    private fun showInAppRatingBottomSheet() {
        val onComplete = {
            Toast.makeText(context, R.string.thanks_for_rating, Toast.LENGTH_SHORT).show()
        }
        if (BuildConfig.DEBUG) {
            onComplete()
        } else {
            try {
                reviewInfo?.let {
                    reviewManager.launchReviewFlow(requireActivity(), it)
                        .addOnCompleteListener {
                            onComplete()
                        }
                } ?: onComplete()
            } catch (t: Throwable) {
                utils.navigateToCHPlay(requireContext())
            }
        }
    }

    private fun initSpinner(spinner: Spinner, key: String) {
        val numbers: List<Int>
        val curValue: Int
        val defaultValue: Int
        when (key) {
            Prefs.MAX_CONCUR_DOWNLOAD_NUM_KEY -> {
                numbers =
                    (DownloadConfig.MAX_CONCUR_DOWNLOAD_NUM_LOWER_BOUND..DownloadConfig.MAX_CONCUR_DOWNLOAD_NUM_UPPER_BOUND).toList()
                curValue = downloadConfig.maxConcurDownloadNum
                defaultValue = DownloadConfig.DEFAULT_MAX_CONCUR_DOWNLOAD_NUM
            }
            else -> {
                numbers =
                    (DownloadConfig.MULTI_THREAD_DOWNLOAD_NUM_LOWER_BOUND..DownloadConfig.MULTI_THREAD_DOWNLOAD_NUM_UPPER_BOUND).toList()
                curValue = downloadConfig.multiThreadDownloadNum
                defaultValue = DownloadConfig.DEFAULT_MULTI_THREAD_DOWNLOAD_NUM
            }
        }

        val spinnerAdapter = SelectNumberAdapter(numbers, spinner, curValue, defaultValue)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        spinner.setSelection(spinnerAdapter.getPosition(curValue))

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    changedConfigs.remove(key)
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (spinnerAdapter.getItem(position) == curValue) {
                        changedConfigs.remove(key)
                        if (changedConfigs.isEmpty()) {
                            applyButton.visibility = View.GONE
                        }
                    } else {
                        changedConfigs.add(key)
                        applyButton.visibility = View.VISIBLE
                    }
                }
            }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.titleLayout -> dismiss()
            R.id.applyButton -> {
                applyChangedConfigs()
                dismiss()
            }
            R.id.rateTextView -> {
                if (reviewInfo != null) {
                    showInAppRatingBottomSheet()
                } else {
                    utils.navigateToCHPlay(requireContext())
                }
            }
        }
    }

    private fun applyChangedConfigs() {
        changedConfigs.forEach {
            when (it) {
                Prefs.MAX_CONCUR_DOWNLOAD_NUM_KEY -> downloadConfig.setMaxConcurDownloadNum(
                    requireView().maxConcurDownloadNumSpinner.selectedItem as Int,
                    activity as MainActivity
                )
                Prefs.MULTI_THREAD_DOWNLOAD_NUM_KEY -> {
                    downloadConfig.multiThreadDownloadNum =
                        requireView().multiThreadDownloadNumSpinner.selectedItem as Int
                }
            }
        }
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
        selectedMaxConcurDownloadNum = downloadConfig.maxConcurDownloadNum
        selectedMultiThreadDownloadNum = downloadConfig.multiThreadDownloadNum
    }

    override fun onResume() {
        super.onResume()
        view?.apply {
            maxConcurDownloadNumSpinner.setSelection(
                (maxConcurDownloadNumSpinner.adapter as SelectNumberAdapter).getPosition(
                    selectedMaxConcurDownloadNum
                )
            )
            multiThreadDownloadNumSpinner.setSelection(
                (multiThreadDownloadNumSpinner.adapter as SelectNumberAdapter).getPosition(
                    selectedMultiThreadDownloadNum
                )
            )
        }
    }

    override fun onPause() {
        super.onPause()
        view?.apply {
            selectedMaxConcurDownloadNum = maxConcurDownloadNumSpinner.selectedItem as Int
            selectedMultiThreadDownloadNum = multiThreadDownloadNumSpinner.selectedItem as Int
        }
    }

    inner class SelectNumberAdapter(
        numbers: List<Int>,
        private val spinner: Spinner,
        private val curValue: Int,
        private val defaultValue: Int
    ) : ArrayAdapter<Int>(
        requireContext(),
        android.R.layout.simple_spinner_item,
        numbers
    ) {
        override fun getDropDownView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val res = super.getDropDownView(
                position,
                convertView,
                parent
            )
            val textView = res.findViewById<View>(android.R.id.text1) as TextView
            if (getItem(position) == defaultValue) {
                textView.text = String.format(
                    "%s (%s)",
                    textView.text,
                    getString(R.string.desc_default)
                )
            }
            when {
                position == spinner.selectedItemPosition -> {
                    textView.setTypeface(null, Typeface.BOLD)
                    textView.setTextColor(ContextCompat.getColor(context, R.color.lightPrimary))
                }
                getItem(position) == curValue -> textView.setTypeface(
                    null,
                    Typeface.BOLD
                )
                else -> {
                    textView.setTypeface(null, Typeface.NORMAL)
                    textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                }
            }
            return res
        }
    }
}