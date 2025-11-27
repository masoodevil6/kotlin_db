package com.gogcompany.mymvvmapplication.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.gogcompany.mymvvmapplication.data.NoteRepository
import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import com.gogcompany.mymvvmapplication.data.local.prefs.PerfManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NoteRepository ,
    private val state: SavedStateHandle ,
    private val perfManager: PerfManager
) : ViewModel() {


    private val _homeEventChanel = Channel<HomeEvent>();
    val homeEventChanel = _homeEventChanel.receiveAsFlow();

    private val _showFavorite = MutableStateFlow(false);
    val showFavorite : StateFlow<Boolean> = _showFavorite;

    val searchQuery = state.getLiveData("search" , "")

    private val notes = combine(
        searchQuery.asFlow() ,
        perfManager.readSearchNote
    ){
        query , filters -> Pair(query , filters)
    }.flatMapLatest {
        (query , filters)-> repository.getNotes(query , filters.isFavorite , filters.sortBy)
    }

    init {
        giveNeFavoriteStateAction();
    }

    private fun giveNeFavoriteStateAction() {
        viewModelScope.launch{
            perfManager.readFavorite.collectLatest {
                _showFavorite.emit(it);
            }
        }
    }


//    private val _notes = MutableStateFlow<List<Note>>(emptyList())
//    val notes = _notes.asStateFlow();


   /* init {
        getNotes();
    }*/


    /*private fun getNotes(){
        viewModelScope.launch {
            repository.getNotes().collectLatest {
                _notes.emit(it);
            }
        }

    }*/



    fun fabClicked(){
        viewModelScope.launch {
            _homeEventChanel.send(HomeEvent.fabNewNoteClicked)
        }
    }

    fun getNotes() {
        viewModelScope.launch { 
            notes.collectLatest { 
                _homeEventChanel.send(HomeEvent.SendNotes(it))
            }
        }
    }

    fun onFavoriteSelected(checked: Boolean) {
        viewModelScope.launch {
            perfManager.saveFavorite(checked);

        }
    }

    fun onSortBySelected(sortType: SortBy) {
        viewModelScope.launch {
            perfManager.saveSortOrder(sortType)
        }
    }

    fun onItemClicked(note: Note) {
        viewModelScope.launch {
            _homeEventChanel.send(HomeEvent.NavigateToNoteFragment(note))
        }
    }

    fun onFavoriteItemClicked(note: Note, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setStatusFavorite(note.id , isFavorite)
        }
    }

    fun onItemSwaiped(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note);
            _homeEventChanel.send(HomeEvent.showUndoDeleteMEssage(note))
        }
    }

    fun onUndoDeleteClick(note: Note) {
        viewModelScope.launch{
            repository.saveNote(note);
        }
    }


    sealed class HomeEvent(){
        data object fabNewNoteClicked : HomeEvent();
        data class NavigateToNoteFragment(val note:Note) :HomeEvent();
        data class SendNotes(val notes:List<Note>) :HomeEvent();
        data class showUndoDeleteMEssage(val note: Note) : HomeEvent();
    }



}