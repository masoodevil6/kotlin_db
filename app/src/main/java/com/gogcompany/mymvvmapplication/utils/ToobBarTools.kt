package com.gogcompany.mymvvmapplication.utils

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle

private var noteMenuProvider: NoteMenuProvider? = null

fun Fragment.readyMenu(menuId: Int) {
    val provider = NoteMenuProvider(this, menuId)
    requireActivity().addMenuProvider(provider, viewLifecycleOwner, Lifecycle.State.RESUMED)

    noteMenuProvider = provider
}

fun Fragment.toggleStatusMenuItem(
    menuItemId: Int,
    defaultValue: Boolean,
    iconFalse: Int,
    iconTrue: Int,
    onFavoriteClick: (Boolean) -> Unit
) {
    var isFavorite = defaultValue
    val provider = noteMenuProvider ?: return




    requireActivity().addMenuProvider(object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            provider.updateItemIcon(menuItemId, isFavorite, iconFalse, iconTrue)
        }
        override fun onPrepareMenu(menu: Menu) {}
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return if (menuItem.itemId == menuItemId) {
                isFavorite = !isFavorite
                provider.updateItemIcon(menuItemId, isFavorite, iconFalse, iconTrue)
                onFavoriteClick.invoke(isFavorite)
                true
            } else false
        }


    }, viewLifecycleOwner, Lifecycle.State.RESUMED)

}
