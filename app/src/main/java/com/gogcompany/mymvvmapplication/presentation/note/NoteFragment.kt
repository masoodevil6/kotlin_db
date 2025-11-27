package com.gogcompany.mymvvmapplication.presentation.note

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.gogcompany.mymvvmapplication.R
import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.databinding.FragmentNoteBinding
import com.gogcompany.mymvvmapplication.utils.readyMenu
import com.gogcompany.mymvvmapplication.utils.toggleStatusMenuItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteFragment : Fragment(R.layout.fragment_note) {

    private lateinit var binding: FragmentNoteBinding;
    private val viewModel: NoteViewModel by viewModels();
    private var note : Note? = null;

    private var _isFavorite = false;
   /* private lateinit var menuHost: MenuHost;
    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_note , menu);
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return true;
        }

    }*/

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentNoteBinding.bind(view);

        if (viewModel.note != null) {
            note = viewModel.note;
            _isFavorite = note?.isFavorite ?: false;
        }




        val activity = requireActivity();
        if (activity is AppCompatActivity){
            activity.setSupportActionBar(binding.noteFragmentToolbar);
        }


//        menuHost = requireActivity();
//        menuHost.addMenuProvider(menuProvider , viewLifecycleOwner , Lifecycle.State.RESUMED);


//        binding.noteFragmentEditTextNoteTitle.setText(viewModel.note?.title);
//        binding.noteFragmentEditTextNoteDescription.setText(viewModel.note?.description);
        with(binding){

            readyToolBar();
            setStatusActionFavorite();


            noteFragmentEditTextNoteTitle.setText(viewModel.note?.title);
            noteFragmentEditTextNoteDescription.setText(viewModel.note?.description)
            noteFragmentEditBtnSubmit.setOnClickListener{
                val noteTitle = noteFragmentEditTextNoteTitle.text?.toString().orEmpty();
                val noteDescription = noteFragmentEditTextNoteDescription.text?.toString().orEmpty();
                viewModel.saveNoteData(noteTitle , noteDescription , _isFavorite);
            }
           /* viewModel.note?.isFavorite.let {
                setStatusActionFavorite();
            }*/

            observeEvents();
        }




    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch{
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED)
            {
                viewModel.noteEventChanel.collect{
                    event->
                    when(event){
                        NoteViewModel.NoteEvents.OnFinishNoteSave -> {
                            findNavController().popBackStack();
                        }
                    }
                }
            }
        }
    }

   /* private fun saveNote() {
        with(binding){
            val noteTitle = noteFragmentEditTextNoteTitle.text?.toString().orEmpty();
            val noteDescription = noteFragmentEditTextNoteDescription.text?.toString().orEmpty();

            viewModel.saveNoteData(noteTitle, noteDescription, _isFavorite);
        }
    }*/


    private fun readyToolBar(){
        readyMenu(
            menuId = R.menu.menu_note
        )
    }

    private fun setStatusActionFavorite(){
        toggleStatusMenuItem(
            menuItemId =      R.id.note_menu_action_favorite ,
            defaultValue =    this._isFavorite ,
            iconTrue =        R.drawable.ic_fav_fill ,
            iconFalse =       R.drawable.ic_favorite_empty,
            onFavoriteClick = {
                    isFavorite->
                this._isFavorite = isFavorite;
            }
        )
    }




}