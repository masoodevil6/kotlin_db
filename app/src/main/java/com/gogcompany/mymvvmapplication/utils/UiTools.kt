package com.gogcompany.mymvvmapplication.utils

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.gogcompany.mymvvmapplication.R
import com.google.android.material.button.MaterialButton


inline fun SearchView.onQueryTextChanged(crossinline listener: (String) -> Unit){
    this.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
        override fun onQueryTextSubmit(query: String?): Boolean  = true;

        override fun onQueryTextChange(newText: String?): Boolean {
            listener.invoke(newText.orEmpty());
            return true;
        }

    })
}




fun MaterialButton.toggleFavoriteIcon(initialValue: Boolean= false , onFavoriteClick:(Boolean)->Unit):Boolean{

    var isFavorite = initialValue;
    tag= isFavorite;

    setIconResource(if (isFavorite) R.drawable.ic_fav_fill else R.drawable.ic_favorite_empty);

    setOnClickListener{
        isFavorite = !isFavorite;
        tag = isFavorite;
        setIconResource(if (isFavorite) R.drawable.ic_fav_fill else R.drawable.ic_favorite_empty);
        onFavoriteClick.invoke(isFavorite);
    }

    return isFavorite
}



/*fun Fragment.readyMenu(
    menuId: Int ,
){
    requireActivity().addMenuProvider(object: MenuProvider{

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(menuId , menu);
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return true
        }

    }, viewLifecycleOwner, Lifecycle.State.RESUMED)
}*/

/*

fun Fragment.toggleStatusMenuItem(
    menuItemId: Int ,
    defaultValue: Boolean ,
    iconFalse : Int ,
    iconTrue: Int ,
    onFavoriteClick: (Boolean) -> Unit
){
    var isFavorite = defaultValue;
    var menuItemRef: MenuItem? = null;

    requireActivity().addMenuProvider(object: MenuProvider{

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

        }

        override fun onPrepareMenu(menu: Menu) {
            menuItemRef = menu.findItem(menuItemId);
            updateIcon();
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return if (menuItem.itemId == menuItemId){
                isFavorite = !isFavorite;
                updateIcon();
                onFavoriteClick.invoke(isFavorite);
                true;
            }
            else false;
        }

        fun updateIcon() {
            menuItemRef?.icon = if (isFavorite) {
                ContextCompat.getDrawable(requireContext(), iconTrue)
            } else {
                ContextCompat.getDrawable(requireContext(), iconFalse)
            }
        }
    }, viewLifecycleOwner, Lifecycle.State.RESUMED)

}
*/



/*fun MenuItem.toggleFavoriteIcon(
    defaultValue: Boolean = false,
    context: Context,
    onFavoriteClick: (Boolean) -> Unit
): Boolean {

    this.setActionView(null)
    this.isCheckable = true

    this.isChecked = defaultValue

    fun updateIcon() {
        this.icon = if (this.isChecked) {
            ContextCompat.getDrawable(context, R.drawable.ic_fav_fill)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_favorite_empty)
        }
    }

    updateIcon();

    this.setOnMenuItemClickListener {
        this.isChecked = !this.isChecked
        updateIcon();
        onFavoriteClick.invoke(this.isChecked)
        true
    }

    return this.isChecked;
}*/


/*
fun MenuItem.toggleFavoriteIcon(
    defaultValue: Boolean = false,
    context: Context,
    onFavoriteClick: (Boolean)->Unit
): Boolean {

    var isFavorite = defaultValue
    this.icon = if (isFavorite) {
        ContextCompat.getDrawable(context, R.drawable.ic_fav_fill)
    } else {
        ContextCompat.getDrawable(context, R.drawable.ic_favorite_empty)
    }

    this.actionView?.setOnClickListener {
        isFavorite = !isFavorite

        this.icon = if (isFavorite) {
            ContextCompat.getDrawable(context, R.drawable.ic_fav_fill)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_favorite_empty)
        }

        onFavoriteClick(isFavorite)
    }

    return isFavorite;
}*/
