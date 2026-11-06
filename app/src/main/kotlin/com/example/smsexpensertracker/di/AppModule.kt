package com.example.smsexpensertracker.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the WorkerFactory for WorkManager and any generic dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /** Bind Hilt's generated WorkerFactory so WorkManager can create @HiltWorker classes. */
    @Binds
    abstract fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory

    // Example of a generic dependency (used elsewhere in the codebase)
    @Provides
    @Singleton
    fun provideExampleDependency(): String = "example"
}
