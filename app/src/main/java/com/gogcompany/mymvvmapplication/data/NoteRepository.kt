package com.gogcompany.mymvvmapplication.data

import com.gogcompany.mymvvmapplication.data.local.LocalDataSourceImpl
import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NoteRepository @Inject constructor(
    private val localDataSourceImpl: LocalDataSourceImpl
) {

    fun getNotes(search:String , isFavorite:Boolean , sortBy: SortBy) : Flow<List<Note>>{
        return localDataSourceImpl.getNotes(search , isFavorite , sortBy);
    }

    suspend fun saveNote(note: Note){
        localDataSourceImpl.saveNote(note);
    }

    suspend fun updateNote(note: Note){
        return localDataSourceImpl.updateNote(note);
    }

    suspend fun setStatusFavorite(noteId:Int , noteIsFavorite:Boolean){
        return localDataSourceImpl.setStatusFavorite(noteId , noteIsFavorite);
    }

    suspend fun deleteNote(note: Note) {
        return localDataSourceImpl.deleteNote(note);
    }

}