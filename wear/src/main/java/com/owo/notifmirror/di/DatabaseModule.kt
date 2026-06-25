package com.found404.sidelink.di

import android.content.Context
import com.found404.sidelink.data.database.NotificationDao
import com.found404.sidelink.data.database.WearDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WearDatabase {
        return WearDatabase.getDatabase(context)
    }

    @Provides
    fun provideNotificationDao(database: WearDatabase): NotificationDao {
        return database.notificationDao()
    }
}
