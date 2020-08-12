package com.mgt.downloader.data_model

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.NonNull
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.sql.Date

@Entity
data class DownloadTask(
    @NonNull @PrimaryKey var fileName: String,
    var displayUrl: String,
    var downloadUrl: String,
    var downloadedSize: Long = 0,
    var totalSize: Long = -1,
    @Volatile var state: Int = STATE_DOWNLOADING,
    var startTime: Date,
    var elapsedTime: Long = 0,
    var zipEntryName: String? = null, // for zip part only
    var isDirectory: Boolean = false,
    var partsDownloadedSize: ArrayList<Long> = ArrayList(), // for multi-thread download
    var thumbUrl:String?=null,
    var thumbRatio:String="1:1"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readSerializable() as Date,
        parcel.readLong(),
        parcel.readString(),
        parcel.readByte() != 0.toByte(),
        parcel.readSerializable() as ArrayList<Long>,
        parcel.readString(),
        parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(fileName)
        parcel.writeString(displayUrl)
        parcel.writeString(downloadUrl)
        parcel.writeLong(downloadedSize)
        parcel.writeLong(totalSize)
        parcel.writeInt(state)
        parcel.writeSerializable(startTime)
        parcel.writeLong(elapsedTime)
        parcel.writeString(zipEntryName)
        parcel.writeByte(if (isDirectory) 1 else 0)
        parcel.writeSerializable(partsDownloadedSize)
        parcel.writeString(thumbUrl)
        parcel.writeString(thumbRatio)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun isFileSizeKnown(): Boolean {
        return totalSize != -1L
    }


    @Ignore
    private val downloadedSizeLock = Any()
    fun increaseDownloadedSize(number: Number) {
        synchronized(downloadedSizeLock) {
            downloadedSize += number.toLong()
        }
    }

    companion object CREATOR : Parcelable.Creator<DownloadTask> {
        override fun createFromParcel(parcel: Parcel): DownloadTask {
            return DownloadTask(parcel)
        }

        override fun newArray(size: Int): Array<DownloadTask?> {
            return arrayOfNulls(size)
        }

        const val STATE_DOWNLOADING = 0
        const val STATE_PERSISTENT_PAUSED = 1//paused and saved to db, thread die
        const val STATE_SUCCESS = 2
        const val STATE_CANCEL_OR_FAIL = 3
        const val STATE_TEMPORARY_PAUSE = 4//paused, not saved to db, thread in infinite while loop
//        const val STATE_PENDING = 5//in queue

        const val PAYLOAD_PROGRESS = 10
        const val PAYLOAD_STATE = 11
        const val PAYLOAD_SELECT_STATE = 12
        const val PAYLOAD_EXPAND_STATE = 13
    }
}