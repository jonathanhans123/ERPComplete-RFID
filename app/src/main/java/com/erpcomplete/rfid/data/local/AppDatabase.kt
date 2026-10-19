package com.erpcomplete.rfid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PendingSyncEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_sync ADD COLUMN businessUnitId TEXT")
                db.execSQL("ALTER TABLE pending_sync ADD COLUMN teamId TEXT")
                db.execSQL("ALTER TABLE pending_sync ADD COLUMN warehouseId TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "erpcomplete_rfid.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
