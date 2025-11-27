package com.gogcompany.mymvvmapplication.di.modules

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.gogcompany.mymvvmapplication.di.scope.ApplicationScope
import com.gogcompany.mymvvmapplication.di.scope.IoDispatcher
import com.gogcompany.mymvvmapplication.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideNoteDataStorePreferences(
        @ApplicationContext context: Context ,
        @IoDispatcher dispatcher: CoroutineDispatcher
    ) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()) ,
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        produceFile = {
            context.preferencesDataStoreFile(Constants.PREFERENCES_NAME)
        }
    )

}
