package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BookingDao
import com.example.data.dao.DriverDao
import com.example.data.dao.DriverPaymentDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.VehicleDao
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.DriverPaymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        DriverEntity::class,
        BookingEntity::class,
        ExpenseEntity::class,
        DriverPaymentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class RoadBookDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun driverDao(): DriverDao
    abstract fun bookingDao(): BookingDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun driverPaymentDao(): DriverPaymentDao

    companion object {
        @Volatile
        private var INSTANCE: RoadBookDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_remoteId ON vehicles(remoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_businessId ON vehicles(businessId)")

                db.execSQL("ALTER TABLE drivers ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE drivers ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE drivers ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_drivers_remoteId ON drivers(remoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_drivers_businessId ON drivers(businessId)")

                db.execSQL("ALTER TABLE bookings ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookings_remoteId ON bookings(remoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookings_businessId ON bookings(businessId)")

                db.execSQL("ALTER TABLE expenses ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN driverId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_remoteId ON expenses(remoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_businessId ON expenses(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_driverId ON expenses(driverId)")

                db.execSQL("ALTER TABLE driver_payments ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE driver_payments ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE driver_payments ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_driver_payments_remoteId ON driver_payments(remoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_driver_payments_businessId ON driver_payments(businessId)")
            }
        }

        fun getDatabase(context: Context): RoadBookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoadBookDatabase::class.java,
                    "roadbook_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(context: Context): RoadBookDatabase = getDatabase(context)
    }
}
