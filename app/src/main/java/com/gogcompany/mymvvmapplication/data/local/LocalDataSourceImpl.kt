package com.gogcompany.mymvvmapplication.data.local

import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import kotlinx.coroutines.flow.Flow

interface LocalDataSourceImpl {


    fun getNotes(search: String, isFavorite: Boolean, sortBy: SortBy): Flow<List<Note>>;

    suspend fun saveNote(note: Note);

    suspend fun updateNote(note: Note);

    suspend fun setStatusFavorite(noteId: Int, noteIsFavorite: Boolean)

    suspend fun deleteNote(note: Note)

}