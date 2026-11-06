package com.example.smsexpensertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** DAO for categorisation rules. */
@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategorisationRule): Long

    @Update
    suspend fun update(rule: CategorisationRule)

    @Query("SELECT * FROM categorisation_rules ORDER BY keyword ASC")
    fun getAllRules(): Flow<List<CategorisationRule>>

    @Query("SELECT * FROM categorisation_rules WHERE id = :id")
    suspend fun getById(id: Long): CategorisationRule?

    @Query("DELETE FROM categorisation_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
