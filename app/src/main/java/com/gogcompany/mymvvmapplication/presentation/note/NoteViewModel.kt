package com.gogcompany.mymvvmapplication.presentation.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gogcompany.mymvvmapplication.data.NoteRepository
import com.gogcompany.mymvvmapplication.data.local.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository ,
    private val state: SavedStateHandle
) : ViewModel() {


    private val _noteEventChanel = Channel<NoteEvents>();
    val noteEventChanel = _noteEventChanel.receiveAsFlow();

    var note = state.get<Note>("note")



    fun saveNoteData(noteTitle: String, noteDescription: String, noteIsFavorite: Boolean) {
        viewModelScope.launch {
            if (note == null){
                note = Note(
                    title = noteTitle ,
                    description =  noteDescription ,
                    isFavorite = noteIsFavorite
                );
                repository.saveNote(note!!);
            }
            else{
                note?.title = noteTitle;
                note?.description = noteDescription;
                note?.isFavorite = noteIsFavorite;
                repository.updateNote(note!!);
            }

            _noteEventChanel.send(NoteEvents.OnFinishNoteSave)
        }
    }


    sealed class NoteEvents{
        data object OnFinishNoteSave : NoteEvents();
    }
}