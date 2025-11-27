package com.gogcompany.mymvvmapplication.utils

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment

class NoteMenuProvider(
    private val fragment: Fragment,
    private val menuId: Int
) : MenuProvider {

    private var menuRef: Menu? = null

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(menuId, menu)
        menuRef = menu
    }

    override fun onPrepareMenu(menu: Menu) {}

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false
    }

    fun updateItemIcon(menuItemId: Int, isActive: Boolean, iconFalse: Int, iconTrue: Int) {
        val context = fragment.context ?: return   // خطر Crash رفع شد
        val menu = menuRef ?: return              // منو هنوز نساخته

        val item = menu.findItem(menuItemId)
        item?.icon = ContextCompat.getDrawable(
            context,
            if (isActive) iconTrue else iconFalse
        )
    }
}

