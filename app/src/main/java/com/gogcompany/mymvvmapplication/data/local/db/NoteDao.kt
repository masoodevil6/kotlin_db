package com.gogcompany.mymvvmapplication.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import kotlinx.coroutines.flow.Flow


@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note);

//    @Query("select * from note_table")
//    fun getNotes(search: String, isFavorite: Boolean, sortBy: SortBy): Flow<List<Note>>;


    fun getNotes(search: String, isFavorite: Boolean, sortBy: SortBy) =
        when(sortBy){
            SortBy.Name->  getNotesByName(search , isFavorite)
            SortBy.Date->  getNotesByDate(search , isFavorite)
        }

    @Query(
        "select * from note_table where (isFavorite= case when :favorite then 1 else isFavorite end) " +
                "and (title like '%' || :search || '%' ) and (description like '%' || :search || '%' )" +
                "order by title desc"
    )
    fun getNotesByName(search: String, favorite: Boolean) : Flow<List<Note>>

    @Query(
        "select * from note_table where (isFavorite= case when :favorite then 1 else isFavorite end) " +
                "and (title like '%' || :search || '%' ) and (description like '%' || :search || '%' )" +
                "order by date desc"
    )
    fun getNotesByDate(search: String, favorite: Boolean) : Flow<List<Note>>


    @Update
    suspend fun updateNote(note: Note);

    @Query("update note_table set isFavorite= :noteIsFavorite where id=:noteId")
    suspend fun setStatusFavorite(noteId: Int, noteIsFavorite: Boolean)

    @Delete
    suspend fun deleteNote(note: Note)


}