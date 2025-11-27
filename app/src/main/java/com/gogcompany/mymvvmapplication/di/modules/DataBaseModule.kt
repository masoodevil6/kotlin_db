package com.gogcompany.mymvvmapplication.di.modules

import android.app.Application
import androidx.room.Room
import com.gogcompany.mymvvmapplication.data.NoteRepository
import com.gogcompany.mymvvmapplication.data.local.LocalDataSource
import com.gogcompany.mymvvmapplication.data.local.model.NoteDataBase
import com.gogcompany.mymvvmapplication.data.local.db.NoteDao
import com.gogcompany.mymvvmapplication.di.scope.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DataBaseModule {

    @Provides
    @Singleton
    fun provideDataBase(application: Application , callback: NoteDataBase.Callback) : NoteDataBase =
        Room.databaseBuilder(application , NoteDataBase::class.java , "mvvm_database")
            .addCallback(callback)
            .build();


    @Provides
    fun provideNoteDao(noteDataBase: NoteDataBase) = noteDataBase.noteDao();

    @Provides
    fun provideLocalDataSource(noteDao: NoteDao) = LocalDataSource(noteDao);

    @Provides
    fun provideRepository(localDataSource: LocalDataSource) = NoteRepository(localDataSource)

    @Singleton
    @Provides
    @ApplicationScope
    fun provideApplicationScope() = CoroutineScope(SupervisorJob())



}