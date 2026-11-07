// path: app/src/main/kotlin/com/example/smsexpensertracker/di/DatabaseModule.kt
package com.example.smsexpensertracker.di

import android.content.Context
import androidx.room.Room
import com.example.smsexpensertracker.data.AppDatabase
import com.example.smsexpensertracker.data.BudgetAlertTracker
import com.example.smsexpensertracker.data.BudgetDao
import com.example.smsexpensertracker.data.BudgetNotifier
import com.example.smsexpensertracker.data.BudgetRepository
import com.example.smsexpensertracker.data.BudgetRepositoryImpl
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.PreferencesBudgetAlertTracker
import com.example.smsexpensertracker.data.RuleDao
import com.example.smsexpensertracker.data.SmsSourceStateDao
import com.example.smsexpensertracker.data.SystemBudgetNotifier
import com.example.smsexpensertracker.data.TransactionDao
import com.example.smsexpensertracker.data.TransactionRepository
import com.example.smsexpensertracker.data.TransactionRepositoryImpl
import com.example.smsexpensertracker.parser.ParserFactory
import com.example.smsexpensertracker.parser.SmsParser
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideSmsSourceStateDao(db: AppDatabase): SmsSourceStateDao = db.smsSourceStateDao()

    @Provides
    @Singleton
    fun provideSmsParser(): SmsParser = ParserFactory.create()

    @Provides
    @Singleton
    fun provideBudgetAlertTracker(@ApplicationContext context: Context): BudgetAlertTracker =
        PreferencesBudgetAlertTracker(context)

    @Provides
    @Singleton
    fun provideBudgetNotifier(@ApplicationContext context: Context): BudgetNotifier =
        SystemBudgetNotifier(context)

    @Provides
    @Singleton
    fun provideBudgetRepository(
        budgetDao: BudgetDao,
        transactionDao: TransactionDao,
        categoryDao: CategoryDao,
        alertTracker: BudgetAlertTracker,
        notifier: BudgetNotifier
    ): BudgetRepository = BudgetRepositoryImpl(
        budgetDao = budgetDao,
        transactionDao = transactionDao,
        categoryDao = categoryDao,
        alertTracker = alertTracker,
        notifier = notifier
    )

    @Provides
    @Singleton
    fun provideTransactionRepository(
        transactionDao: TransactionDao,
        categoryDao: CategoryDao,
        ruleDao: RuleDao
    ): TransactionRepository = TransactionRepositoryImpl(
        transactionDao = transactionDao,
        categoryDao = categoryDao,
        ruleDao = ruleDao
    )
}
