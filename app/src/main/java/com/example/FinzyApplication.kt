package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinzyApplication : Application(), Configuration.Provider {

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        TransactionRepository(database.transactionDao(), database.categoryDao())
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Ensure baseline categories exist on first access
        CoroutineScope(Dispatchers.IO).launch {
            repository.getCategoriesList()
        }
    }

    companion object {
        lateinit var instance: FinzyApplication
            private set
    }
}
