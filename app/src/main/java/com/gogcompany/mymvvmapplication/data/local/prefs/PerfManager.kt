package com.gogcompany.mymvvmapplication.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gogcompany.mymvvmapplication.data.local.model.helper.SortBy
import com.gogcompany.mymvvmapplication.data.local.prefs.PerfManager.PreferenceKey.favorite
import com.gogcompany.mymvvmapplication.data.local.prefs.PerfManager.PreferenceKey.sort
import com.gogcompany.mymvvmapplication.utils.Constants
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

import javax.inject.Inject

class PerfManager @Inject constructor(
    private val appDataStore: DataStore<Preferences>
) {


    private object PreferenceKey{
        val sort = stringPreferencesKey(Constants.PREFERENCES_SORT_KEY)
        val favorite = booleanPreferencesKey(Constants.PREFERENCES_FAVORITE_KEY)
    }

    suspend fun saveSortOrder(sortBy: SortBy) = appDataStore.edit { preferences ->
        preferences[sort] = sortBy.name
    }
    suspend fun saveFavorite(favoriteState: Boolean) = appDataStore.edit { preferences ->
        preferences[favorite] = favoriteState
    }



    val readSortOrder = appDataStore.data.catch {
        exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }.map {
        preferences-> preferences[sort] ?: SortBy.Date.name
    }



    val readFavorite = appDataStore.data.catch {
        exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }.map {
        preferences-> preferences[favorite] ?: Constants.FAVORITE
    }




    val readSearchNote = appDataStore.data.catch {
            exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }.map {
        preferences ->
        FilterPrefs(
            SortBy.valueOf(preferences[sort] ?: SortBy.Name.name) ,
            preferences[favorite] ?: Constants.FAVORITE
        )
    }
}

data class FilterPrefs(val sortBy: SortBy , val isFavorite: Boolean)
