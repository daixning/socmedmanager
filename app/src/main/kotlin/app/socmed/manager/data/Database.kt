package app.socmed.manager.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM content WHERE deletedAt IS NULL") fun contents(): Flow<List<ContentEntity>>
    @Query("SELECT * FROM version WHERE deletedAt IS NULL") fun versions(): Flow<List<VersionEntity>>
    @Query("SELECT * FROM asset WHERE deletedAt IS NULL") fun assets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM link WHERE deletedAt IS NULL") fun links(): Flow<List<LinkEntity>>
    @Query("SELECT * FROM task WHERE deletedAt IS NULL") fun tasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM event WHERE deletedAt IS NULL") fun events(): Flow<List<EventEntity>>
    @Query("SELECT * FROM snapshot WHERE deletedAt IS NULL") fun snapshots(): Flow<List<SnapshotEntity>>
    @Query("SELECT * FROM follower_log WHERE deletedAt IS NULL") fun followerLogs(): Flow<List<FollowerLogEntity>>
    @Query("SELECT * FROM feed_post WHERE deletedAt IS NULL") fun feedPosts(): Flow<List<FeedPostEntity>>
    @Query("SELECT * FROM highlight WHERE deletedAt IS NULL") fun highlights(): Flow<List<HighlightEntity>>
    @Query("SELECT * FROM status_def") fun statusDefs(): Flow<List<StatusDefEntity>>
    @Query("SELECT * FROM pref") fun prefs(): Flow<List<PrefEntity>>

    @Query("SELECT * FROM content WHERE id = :id") suspend fun contentById(id: String): ContentEntity?
    @Query("SELECT * FROM version WHERE contentId = :contentId") suspend fun versionsOf(contentId: String): List<VersionEntity>
    @Query("SELECT COUNT(*) FROM status_def") suspend fun statusCount(): Int
    @Query("SELECT * FROM asset") suspend fun allAssetsIncludingDeleted(): List<AssetEntity>

    @Upsert suspend fun upsert(e: ContentEntity)
    @Upsert suspend fun upsertAll(e: List<ContentEntity>)
    @Upsert suspend fun upsertVersion(e: VersionEntity)
    @Upsert suspend fun upsertVersions(e: List<VersionEntity>)
    @Upsert suspend fun upsert(e: AssetEntity)
    @Upsert suspend fun upsertAssets(e: List<AssetEntity>)
    @Upsert suspend fun upsert(e: LinkEntity)
    @Upsert suspend fun upsertLinks(e: List<LinkEntity>)
    @Upsert suspend fun upsert(e: TaskEntity)
    @Upsert suspend fun upsertTasks(e: List<TaskEntity>)
    @Upsert suspend fun upsert(e: EventEntity)
    @Upsert suspend fun upsert(e: SnapshotEntity)
    @Upsert suspend fun upsertSnapshots(e: List<SnapshotEntity>)
    @Upsert suspend fun upsert(e: FollowerLogEntity)
    @Upsert suspend fun upsertFollowerLogs(e: List<FollowerLogEntity>)
    @Upsert suspend fun upsert(e: FeedPostEntity)
    @Upsert suspend fun upsert(e: HighlightEntity)
    @Upsert suspend fun upsert(e: StatusDefEntity)
    @Upsert suspend fun upsertStatuses(e: List<StatusDefEntity>)
    @Upsert suspend fun upsert(e: PrefEntity)
}

@Database(
    entities = [
        ContentEntity::class, VersionEntity::class, AssetEntity::class, LinkEntity::class, TaskEntity::class,
        EventEntity::class, SnapshotEntity::class, FollowerLogEntity::class, FeedPostEntity::class,
        HighlightEntity::class, StatusDefEntity::class, PrefEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        const val FILE_NAME = "socmed.db"

        /**
         * MIGRATION POLICY — read before changing any entity.
         * 1. Bump `version` above and add a `Migration(from, to)` here that alters tables in place.
         * 2. Never use fallbackToDestructiveMigration(): it would erase the user's data on update.
         * 3. Commit the exported schema JSON in app/schemas/ for every version.
         * Example for a future v2 that adds a column:
         *   val MIGRATION_1_2 = object : Migration(1, 2) {
         *       override fun migrate(db: SupportSQLiteDatabase) {
         *           db.execSQL("ALTER TABLE content ADD COLUMN example TEXT NOT NULL DEFAULT ''")
         *       }
         *   }
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
