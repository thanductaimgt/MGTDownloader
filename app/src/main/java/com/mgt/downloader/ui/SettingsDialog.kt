package com.mgt.downloader.ui

import android.app.Dialog
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
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
import com.mgt.downloader.MyApplication
import com.mgt.downloader.R
import com.mgt.downloader.utils.Configurations
import com.mgt.downloader.utils.Statistics
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.Utils
import kotlinx.android.synthetic.main.dialog_settings.*
import kotlinx.android.synthetic.main.dialog_settings.view.*


class SettingsDialog(private val fm: FragmentManager) : DialogFragment(),
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

    override fun setupDialog(dialog: Dialog, style: Int) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
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
        MyApplication.reviewManager.requestReviewFlow()
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
            Configurations.MAX_CONCUR_DOWNLOAD_NUM_KEY
        )
        initSpinner(
            multiThreadDownloadNumSpinner,
            Configurations.MULTI_THREAD_DOWNLOAD_NUM_KEY
        )

        successDownloadNumTextView.text = Statistics.successDownloadNum.toString()
        cancelOrFailDownloadNumTextView.text = Statistics.cancelOrFailDownloadNum.toString()
        totalDownloadNumTextView.text = Statistics.totalDownloadNum.toString()
        totalDownloadSizeTextView.text = Utils.getFormatFileSize(Statistics.totalDownloadSize)

        pathTextView.text = Utils.getDownloadDirPath(requireContext())

        val pInfo: PackageInfo =
            requireContext().packageManager.getPackageInfo(
                requireContext().applicationContext.packageName,
                0
            )
        aboutTextView.text =
            String.format(getString(R.string.desc_about_info), pInfo.versionName)

        noteTextView.visibility =
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) View.VISIBLE else View.GONE

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
                val flow =
                    MyApplication.reviewManager.launchReviewFlow(requireActivity(), reviewInfo!!)
                flow.addOnCompleteListener {
                    onComplete()
                }
            } catch (t: Throwable) {
                Utils.navigateToCHPlay(requireContext())
            }
        }
    }

    private fun initSpinner(spinner: Spinner, key: String) {
        val numbers: List<Int>
        val curValue: Int
        val defaultValue: Int
        when (key) {
            Configurations.MAX_CONCUR_DOWNLOAD_NUM_KEY -> {
                numbers =
                    (Configurations.MAX_CONCUR_DOWNLOAD_NUM_LOWER_BOUND..Configurations.MAX_CONCUR_DOWNLOAD_NUM_UPPER_BOUND).toList()
                curValue = Configurations.maxConcurDownloadNum
                defaultValue = Configurations.DEFAULT_MAX_CONCUR_DOWNLOAD_NUM
            }
            else -> {
                numbers =
                    (Configurations.MULTI_THREAD_DOWNLOAD_NUM_LOWER_BOUND..Configurations.MULTI_THREAD_DOWNLOAD_NUM_UPPER_BOUND).toList()
                curValue = Configurations.multiThreadDownloadNum
                defaultValue = Configurations.DEFAULT_MULTI_THREAD_DOWNLOAD_NUM
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
                    Utils.navigateToCHPlay(requireContext())
                }
            }
        }
    }

    private fun applyChangedConfigs() {
        changedConfigs.forEach {
            when (it) {
                Configurations.MAX_CONCUR_DOWNLOAD_NUM_KEY -> Configurations.setMaxConcurDownloadNum(
                    requireView().maxConcurDownloadNumSpinner.selectedItem as Int,
                    activity as MainActivity
                )
                Configurations.MULTI_THREAD_DOWNLOAD_NUM_KEY -> Configurations.setMultiThreadDownloadNum(
                    requireView().multiThreadDownloadNumSpinner.selectedItem as Int,
                    activity as MainActivity
                )
            }
        }
    }

    fun show() {
        show(fm, TAG)
        selectedMaxConcurDownloadNum = Configurations.maxConcurDownloadNum
        selectedMultiThreadDownloadNum = Configurations.multiThreadDownloadNum
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