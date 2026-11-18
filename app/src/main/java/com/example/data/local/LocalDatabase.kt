package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.AppLanguage
import com.example.core.CalendarMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clinicDataStore by preferencesDataStore(name = "sakha_clinic_prefs")

class ClinicPreferencesStore(private val context: Context) {
    private val keyLang = stringPreferencesKey("preferred_language")
    private val keyCal = stringPreferencesKey("calendar_mode")
    private val keyLastSync = stringPreferencesKey("last_sync_iso")

    val languageFlow: Flow<AppLanguage> = context.clinicDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLang] ?: AppLanguage.DARI.name)
    }

    val calendarModeFlow: Flow<CalendarMode> = context.clinicDataStore.data.map { prefs ->
        val raw = prefs[keyCal] ?: CalendarMode.DUAL.name
        CalendarMode.entries.firstOrNull { it.name == raw } ?: CalendarMode.DUAL
    }

    val lastSyncFlow: Flow<String> = context.clinicDataStore.data.map { prefs ->
        prefs[keyLastSync] ?: ""
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.clinicDataStore.edit { it[keyLang] = language.name }
    }

    suspend fun setCalendarMode(mode: CalendarMode) {
        context.clinicDataStore.edit { it[keyCal] = mode.name }
    }

    suspend fun setLastSync(iso: String) {
        context.clinicDataStore.edit { it[keyLastSync] = iso }
    }
}

@Entity(tableName = "cached_records")
data class CachedRecordEntity(
    @PrimaryKey val cacheKey: String,
    val category: String,
    val payloadJson: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "pending_sync_actions")
data class PendingSyncActionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val collectionPath: String,
    val documentId: String,
    val operation: String,
    val payloadJson: String,
    val createdAtMillis: Long,
    val syncStatus: String = "PENDING"
)

@Dao
interface ClinicCacheDao {
    @Query("SELECT * FROM cached_records WHERE category = :category")
    fun observeByCategory(category: String): Flow<List<CachedRecordEntity>>

    @Query("SELECT * FROM cached_records WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): CachedRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CachedRecordEntity)

    @Query("SELECT * FROM pending_sync_actions WHERE syncStatus = 'PENDING' ORDER BY createdAtMillis ASC")
    fun observePendingActions(): Flow<List<PendingSyncActionEntity>>

    @Query("SELECT * FROM pending_sync_actions WHERE syncStatus = 'PENDING' ORDER BY createdAtMillis ASC")
    suspend fun getPendingActions(): List<PendingSyncActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAction(action: PendingSyncActionEntity)

    @Query("UPDATE pending_sync_actions SET syncStatus = :status WHERE localId = :id")
    suspend fun updateActionStatus(id: Long, status: String)

    @Query("DELETE FROM pending_sync_actions WHERE syncStatus = 'SYNCED'")
    suspend fun clearSyncedActions()
}

@Database(
    entities = [CachedRecordEntity::class, PendingSyncActionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SakhaLocalDatabase : RoomDatabase() {
    abstract fun cacheDao(): ClinicCacheDao

    companion object {
        @Volatile
        private var INSTANCE: SakhaLocalDatabase? = null

        fun getInstance(context: Context): SakhaLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SakhaLocalDatabase::class.java,
                    "sakha_dental_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
