package com.gogcompany.mymvvmapplication.presentation.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gogcompany.mymvvmapplication.R
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import com.gogcompany.mymvvmapplication.databinding.FragmentHomeBinding
import com.gogcompany.mymvvmapplication.utils.onQueryTextChanged
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {


    private lateinit var binding: FragmentHomeBinding;
    private val viewModel:HomeViewModel by viewModels();
    private lateinit var homeAdapter: HomeAdapter;
    private lateinit var menuHost: MenuHost;
    private lateinit var searchView: SearchView;

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_home , menu);

            implementSearchView(menu);
            implementFavoriteState(menu);
        }

        private fun implementFavoriteState(menu: Menu){

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED){
                    viewModel.showFavorite.collectLatest {
                        menu.findItem(R.id.home_menu_action_show_favorites).isChecked = it;
                    }
                }
            }
        }

        private fun implementSearchView(menu: Menu,){
            val searchItem = menu.findItem(R.id.home_menu_action_search);
            searchView = searchItem.actionView as SearchView;
            val pendingQuery = viewModel.searchQuery.value;
            if (pendingQuery != null && pendingQuery!=""){
                searchItem.expandActionView();
                searchView.setQuery(pendingQuery , false);
            }

            searchView.onQueryTextChanged {
                viewModel.searchQuery.value = it;
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when(menuItem.itemId){
                R.id.home_menu_action_show_favorites ->{
                    menuItem.isChecked = !menuItem.isChecked;
                    viewModel.onFavoriteSelected(menuItem.isChecked)
                    true;
                }
                R.id.home_menu_action_sort_name ->{
                    viewModel.onSortBySelected(SortBy.Name);
                    true;
                }
                R.id.home_menu_action_sort_date ->{
                    viewModel.onSortBySelected(SortBy.Date);
                    true;
                }
                R.id.home_menu_action_search ->{

                    true;
                }
                else->false
            }
        }


    };



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentHomeBinding.bind(view);
        val activity = requireActivity();
        if (activity is AppCompatActivity){
            activity.setSupportActionBar(binding.homeFragmentToolbar);
        }

        menuHost = requireActivity();
        menuHost.addMenuProvider(menuProvider , viewLifecycleOwner , Lifecycle.State.RESUMED);

        viewModel.getNotes()
        homeAdapter = HomeAdapter(
            { note->
                viewModel.onItemClicked(note)
            } ,
            { note , isFavovrite ->
                viewModel.onFavoriteItemClicked(note , isFavovrite)
            }
        );
        binding.homeFragmentRecyclerNotes.apply {
            adapter = homeAdapter
            layoutManager = LinearLayoutManager(binding.root.context)
        }
        //homeAdapter.submitList(viewModel.getNotes());

        //observers();


        /*binding.homeFragmentFloatButtonAddNote.setOnClickListener{
           findNavController().navigate(HomeFragmentDirections.actionHomeFragmentBasicIdToNoteFragmentBasicId())
      }*/
        observeEvents();
        binding.homeFragmentFloatButtonAddNote.setOnClickListener{
            viewModel.fabClicked();
        }


        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0 , ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT){
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean  = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val note = homeAdapter.currentList[viewHolder.adapterPosition];
                viewModel.onItemSwaiped(note);
            }
        }).attachToRecyclerView(binding.homeFragmentRecyclerNotes)

    }

    private fun observeEvents(){
        viewLifecycleOwner.lifecycleScope.launch{
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED){
                viewModel.homeEventChanel.collect{
                    event->
                    when(event){
                        is HomeViewModel.HomeEvent.fabNewNoteClicked -> {
                            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentBasicIdToNoteFragmentBasicId(null))
                        }

                        is HomeViewModel.HomeEvent.SendNotes-> homeAdapter.submitList(event.notes)

                        is HomeViewModel.HomeEvent.NavigateToNoteFragment -> {
                            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentBasicIdToNoteFragmentBasicId(event.note))

                        }

                        is HomeViewModel.HomeEvent.showUndoDeleteMEssage -> {
                            Snackbar.make(requireView() , "Note Deleted" , Snackbar.LENGTH_LONG)
                                .setAction("Undo"){
                                    viewModel.onUndoDeleteClick(event.note)
                                }
                                .show();
                        }
                    }
                }
            }
        }
    }

   /* private fun observers() {
        viewLifecycleOwner.lifecycleScope.launch{
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED){
                viewModel.notes.collectLatest {
                    homeAdapter.submitList(it)
                }
            }
        }
    }*/


    override fun onDestroy() {
        super.onDestroy()
        if (searchView != null){
            searchView.setOnQueryTextListener(null);
        }
    }
}