package com.gogcompany.mymvvmapplication.data.local.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.text.DateFormat

@Entity(tableName = "note_table")
@Parcelize
data class Note(

    @PrimaryKey(autoGenerate = true) val id:Int = 0,
    var title: String,
    var description: String ,
    var isFavorite: Boolean = false ,
    var date: Long = System.currentTimeMillis()
) : Parcelable {

    val createDate:String
        get() = DateFormat.getDateInstance().format(date);

}