package com.project.pooket.data.local.book

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryDataStore by preferencesDataStore("library_prefs")

@Singleton
class LibraryPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.libraryDataStore

    private object Keys {
        val SCANNED_FOLDERS = stringSetPreferencesKey("scanned_folders")
    }

    val scannedFolders: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.SCANNED_FOLDERS] ?: emptySet() }

    suspend fun addFolder(uriString: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SCANNED_FOLDERS] ?: emptySet()
            prefs[Keys.SCANNED_FOLDERS] = current + uriString
        }
    }
}