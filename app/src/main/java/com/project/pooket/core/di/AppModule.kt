package com.project.pooket.core.di
import android.content.Context
import androidx.room.Room
import com.project.pooket.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

//    @Provides
//    @Singleton
//    fun provideNavigationManager(): NavigationManager {
//        return NavigationManager()
//    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase{
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pooket_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideBookDao(db: AppDatabase) = db.bookDao()

    @Provides
    @Singleton
    fun provideNoteDao(db: AppDatabase) = db.noteDao()
}