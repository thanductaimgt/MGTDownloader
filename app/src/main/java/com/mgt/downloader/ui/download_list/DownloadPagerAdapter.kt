package com.mgt.downloader.ui.download_list

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.mgt.downloader.R
import com.mgt.downloader.ui.download_list.cancel_fail.CancelFailFragment
import com.mgt.downloader.ui.download_list.downloaded.DownloadedFragment
import com.mgt.downloader.ui.download_list.downloading.DownloadingFragment

class DownloadPagerAdapter(private val fragment: Fragment, fm: FragmentManager) :
    FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    private val bundles = ArrayList<Bundle>()

    init {
        addItem(DownloadingFragment::class.java, R.string.label_in_progress)
        addItem(DownloadedFragment::class.java, R.string.label_done)
        addItem(CancelFailFragment::class.java, R.string.label_canceled_or_fail)
    }

    private fun addItem(jClass:Class<*>, titleResId:Int){
        bundles.add(Bundle().apply {
            putSerializable(KEY_CLASS, jClass)
            putInt(KEY_TITLE_RES_ID, titleResId)
        })
    }

    override fun getItem(position: Int): Fragment {
        return (bundles[position].getSerializable(KEY_CLASS) as Class<*>).newInstance() as Fragment
    }

    override fun getCount(): Int {
        return bundles.size
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return fragment.getString(bundles[position].getInt(KEY_TITLE_RES_ID))
    }

    fun getTag(position: Int):String{
        return (bundles[position].getSerializable(KEY_CLASS) as Class<*>).simpleName
    }

    companion object{
        const val KEY_CLASS = "class"
        const val KEY_TITLE_RES_ID = "titleResId"
    }
}