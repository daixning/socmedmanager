package app.socmed.manager.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class RestoreMode { MERGE, REPLACE }

data class BackupManifest(val appVersion: String, val dbVersion: Int, val createdAt: Long, val counts: Map<String, Int>, val mediaFiles: Int)

data class RestorePreview(
    val manifest: BackupManifest,
    val workDir: File,
    val missingMedia: List<String>,
    val newRecords: Int,
    val updatedRecords: Int,
    val unchangedRecords: Int,
)

data class RestoreResult(val added: Int, val updated: Int, val skipped: Int, val mediaCopied: Int)

/**
 * Full backup = one .zip containing manifest.json, socmed.db (every record, including tombstones, prefs and status
 * labels) and every media file. CSV export is NOT a backup: it has no media and loses relationships.
 */
class Backup(private val context: Context, private val repo: Repository) {
    private val dbFile get() = repo.dbFile
    private val media get() = repo.media

    suspend fun export(target: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val w = repo.db.openHelper.writableDatabase
        w.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val assets = repo.db.dao().allAssetsIncludingDeleted()
        val counts = linkedMapOf<String, Int>()
        for (t in Repository.TABLES) w.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); counts[t] = c.getInt(0) }
        val files = assets.map { it.path }.distinct().filter { media.file(it).isFile }
        val manifest = BackupManifest(APP_VERSION, DB_VERSION, System.currentTimeMillis(), counts, files.size)
        context.contentResolver.openOutputStream(target, "wt")!!.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifestJson(manifest).toString(2).toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("socmed.db"))
                dbFile.inputStream().use { it.copyTo(zip, 64 * 1024) }; zip.closeEntry()
                for (rel in files) {
                    zip.putNextEntry(ZipEntry(rel))
                    media.file(rel).inputStream().use { it.copyTo(zip, 64 * 1024) }; zip.closeEntry()
                }
            }
        }
        repo.setPref(Prefs.BACKUP_LAST, manifest.createdAt.toString())
        manifest
    }

    private fun manifestJson(m: BackupManifest) = JSONObject().apply {
        put("format", FORMAT); put("formatVersion", 1); put("appVersion", m.appVersion); put("dbVersion", m.dbVersion)
        put("createdAt", m.createdAt); put("mediaFiles", m.mediaFiles); put("counts", JSONObject(m.counts as Map<*, *>))
    }

    /** Unpacks and validates a backup without touching current data. Throws IllegalArgumentException with a readable reason. */
    suspend fun inspect(source: Uri): RestorePreview = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var manifestText: String? = null
        var haveDb = false
        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (e.isDirectory) continue
                    val safe = name == "manifest.json" || name == "socmed.db" || (name.startsWith("media/") && !name.contains("..") && name.count { it == '/' } == 1)
                    if (!safe) continue // ignore anything unexpected (also blocks zip-slip)
                    val out = File(work, name).also { it.parentFile?.mkdirs() }
                    out.outputStream().use { zip.copyTo(it, 64 * 1024) }
                    if (name == "manifest.json") manifestText = out.readText()
                    if (name == "socmed.db") haveDb = true
                }
            }
        } ?: throw IllegalArgumentException("Could not open that file.")
        val mj = try { JSONObject(manifestText ?: throw IllegalArgumentException("manifest.json is missing — this is not a SocMed Manager backup.")) } catch (e: org.json.JSONException) { throw IllegalArgumentException("manifest.json is unreadable.") }
        require(mj.optString("format") == FORMAT) { "This file is not a SocMed Manager backup." }
        require(haveDb) { "The backup has no database." }
        val dbV = mj.optInt("dbVersion", -1)
        require(dbV in 1..DB_VERSION) { "This backup was made by a newer app version (database v$dbV). Update the app first." }
        val counts = mj.optJSONObject("counts")?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }.orEmpty()
        val manifest = BackupManifest(mj.optString("appVersion"), dbV, mj.optLong("createdAt"), counts, mj.optInt("mediaFiles"))

        val dbPath = File(work, "socmed.db")
        upgradeIfOlder(dbPath, dbV)
        val bak = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            bak.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst(); require(c.getString(0) == "ok") { "The backup database is damaged." } }
            val missing = ArrayList<String>()
            bak.rawQuery("SELECT path FROM asset", null).use { c -> while (c.moveToNext()) { val p = c.getString(0); if (!File(work, p).isFile) missing += p } }
            val (n, u, s) = diff(bak)
            RestorePreview(manifest, work, missing, n, u, s)
        } finally { bak.close() }
    }

    private fun upgradeIfOlder(file: File, version: Int) {
        if (version == DB_VERSION) return
        // Let Room run the normal migrations on the extracted copy so old backups restore on newer app versions.
        val tmp = Room.databaseBuilder(context, AppDatabase::class.java, file.path).addMigrations(*AppDatabase.MIGRATIONS).build()
        try { tmp.openHelper.writableDatabase } finally { tmp.close() }
    }

    private fun idColumn(table: String) = if (table == "status_def" || table == "pref") "key" else "id"

    /** Counts how many backup rows are new, newer than local, or unchanged (older/equal). Used for the preview. */
    private fun diff(bak: SQLiteDatabase): Triple<Int, Int, Int> {
        val w = repo.db.openHelper.readableDatabase
        var n = 0; var u = 0; var s = 0
        for (t in Repository.TABLES) {
            if (t == "pref") continue
            val local = HashMap<String, Long>()
            w.query("SELECT ${idColumn(t)}, updatedAt FROM $t").use { c -> while (c.moveToNext()) local[c.getString(0)] = c.getLong(1) }
            bak.rawQuery("SELECT ${idColumn(t)}, updatedAt FROM $t", null).use { c ->
                while (c.moveToNext()) {
                    val lu = local[c.getString(0)]
                    when { lu == null -> n++; c.getLong(1) > lu -> u++; else -> s++ }
                }
            }
        }
        return Triple(n, u, s)
    }

    /**
     * MERGE keeps everything already in the app: records that exist only in the backup are added, records in both are
     * replaced only when the backup copy was modified more recently (same id = same record, so nothing is duplicated).
     * REPLACE makes the app match the backup exactly.
     */
    suspend fun apply(preview: RestorePreview, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        val bak = SQLiteDatabase.openDatabase(File(preview.workDir, "socmed.db").path, null, SQLiteDatabase.OPEN_READONLY)
        var added = 0; var updated = 0; var skipped = 0; var mediaCopied = 0
        try {
            // Media first: a record must never point at a file that has not been copied yet.
            File(preview.workDir, "media").listFiles()?.forEach { f ->
                val dest = media.file("media/${f.name}")
                if (!dest.exists() || mode == RestoreMode.REPLACE) { f.copyTo(dest, overwrite = true); mediaCopied++ }
            }
            repo.db.withTransaction {
                val w = repo.db.openHelper.writableDatabase
                if (mode == RestoreMode.REPLACE) repo.clearAllDataForRestore()
                for (t in Repository.TABLES) {
                    if (t == "pref" && mode == RestoreMode.MERGE) continue
                    val key = idColumn(t)
                    val local = HashMap<String, Long>()
                    w.query("SELECT $key, updatedAt FROM $t").use { c -> while (c.moveToNext()) local[c.getString(0)] = c.getLong(1) }
                    val localCols = HashSet<String>()
                    w.query("PRAGMA table_info($t)").use { c -> while (c.moveToNext()) localCols += c.getString(c.getColumnIndexOrThrow("name")) }
                    bak.rawQuery("SELECT * FROM $t", null).use { c ->
                        while (c.moveToNext()) {
                            val id = c.getString(c.getColumnIndexOrThrow(key))
                            val lu = local[id]
                            val bu = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
                            if (lu != null && bu <= lu) { skipped++; continue }
                            w.insert(t, SQLiteDatabase.CONFLICT_REPLACE, toValues(c, localCols))
                            if (lu == null) added++ else updated++
                        }
                    }
                }
            }
        } finally { bak.close() }
        // Thumbnails are not part of the backup; rebuild any that are missing.
        repo.db.dao().allAssetsIncludingDeleted().forEach { a ->
            if (a.thumbPath.isEmpty() || !media.file(a.thumbPath).isFile) {
                if (media.file(a.path).isFile) {
                    val (w, h, th) = media.makeThumb(a.path, a.id, a.isVideo)
                    repo.saveAsset(a.copy(thumbPath = th, width = if (a.width == 0) w else a.width, height = if (a.height == 0) h else a.height))
                }
            }
        }
        repo.ensureDefaults()
        File(context.cacheDir, "restore").deleteRecursively()
        RestoreResult(added, updated, skipped, mediaCopied)
    }

    private fun toValues(c: Cursor, allowed: Set<String>): ContentValues {
        val cv = ContentValues()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            if (name !in allowed) continue
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> cv.putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> cv.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> cv.put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> cv.put(name, c.getBlob(i))
                else -> cv.put(name, c.getString(i))
            }
        }
        return cv
    }

    companion object {
        const val FORMAT = "socmed-manager-backup"
        const val DB_VERSION = 1
        const val APP_VERSION = "1.0.0"
    }
}
