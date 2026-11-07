// path: app/src/main/kotlin/com/example/smsexpensertracker/di/AppModule.kt
package com.example.smsexpensertracker.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the WorkerFactory for WorkManager.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /** Bind Hilt's generated WorkerFactory so WorkManager can create @HiltWorker classes. */
    @Binds
    @Singleton
    abstract fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory
}
