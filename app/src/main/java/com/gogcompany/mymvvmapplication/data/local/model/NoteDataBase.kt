package com.gogcompany.mymvvmapplication.data.local.model

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gogcompany.mymvvmapplication.data.local.db.NoteDao
import com.gogcompany.mymvvmapplication.di.scope.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@Database(
    entities =
    [
    Note::class
    ] ,
    version = 1
)
abstract class NoteDataBase :RoomDatabase(){

    abstract fun noteDao(): NoteDao;


    class Callback @Inject constructor(
        private val database: Provider<NoteDataBase>,
        @ApplicationScope private val applicationScope: CoroutineScope
    ): RoomDatabase.Callback(){
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val notDao = database.get().noteDao()
            applicationScope.launch {
                notDao.insertNote(
                    Note(title= "title test1" , description = "description test1" , isFavorite = false)
                )
                notDao.insertNote(
                    Note(title= "title test2" , description = "description test2" , isFavorite = true)
                )
                notDao.insertNote(
                    Note(title= "title test3" , description = "description test3" , isFavorite = false)
                )
                notDao.insertNote(
                    Note(title= "title test4" , description = "description test4" , isFavorite = true)
                )
            }
        }
    }

}