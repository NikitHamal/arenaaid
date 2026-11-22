package com.arena.aid.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.arena.aid.data.local.ArenaDao
import com.arena.aid.data.local.ArenaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArenaModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideArenaDatabase(
        @ApplicationContext context: Context
    ): ArenaDatabase = Room.databaseBuilder(
        context,
        ArenaDatabase::class.java,
        "arena_native.db"
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(false)
        .build()

    @Provides
    @Singleton
    fun provideArenaDao(db: ArenaDatabase): ArenaDao = db.dao()
}
