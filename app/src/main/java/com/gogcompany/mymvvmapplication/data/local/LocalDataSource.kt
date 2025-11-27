package com.gogcompany.mymvvmapplication.data.local

import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.data.local.db.NoteDao
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalDataSource @Inject constructor(
    private val noteDao: NoteDao
) : LocalDataSourceImpl {


    override fun getNotes(search: String, isFavorite: Boolean, sortBy: SortBy): Flow<List<Note>> {
        return noteDao.getNotes(search , isFavorite , sortBy);
    }

    override suspend fun saveNote(note: Note) {
        return noteDao.insertNote(note);
    }

    override suspend fun updateNote(note: Note) {
        return noteDao.updateNote(note)
    }

    override suspend fun setStatusFavorite(noteId: Int, noteIsFavorite: Boolean) {
        return noteDao.setStatusFavorite(noteId , noteIsFavorite)
    }

    override suspend fun deleteNote(note: Note) {
        return noteDao.deleteNote(note);
    }


}