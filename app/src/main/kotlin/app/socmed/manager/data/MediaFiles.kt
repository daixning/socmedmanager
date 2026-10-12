package app.socmed.manager.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedMedia(
    val path: String, val thumbPath: String, val mime: String, val isVideo: Boolean,
    val sizeBytes: Long, val width: Int, val height: Int, val name: String,
)

data class StorageUsage(val mediaBytes: Long, val thumbBytes: Long, val dbBytes: Long, val mediaFiles: Int)

/**
 * Copies picked/captured media into app-managed storage (so deleting the phone original never breaks a record),
 * keeps the original untouched, and writes a small JPEG thumbnail for smooth scrolling. Files are streamed in
 * 64 KB chunks and images are decoded with sub-sampling, so large files are never fully loaded into memory.
 * Paths stored in the database are relative to filesDir ("media/<uuid>.<ext>") so backups are portable.
 */
class MediaFiles(private val context: Context) {
    private val root: File get() = context.filesDir
    private val mediaDir = File(root, "media").apply { mkdirs() }
    private val thumbDir = File(root, "thumbs").apply { mkdirs() }

    fun file(relPath: String) = File(root, relPath)
    fun uriFor(relPath: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(relPath))

    suspend fun copyIn(uri: Uri, suggestedName: String? = null): ImportedMedia? = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            val id = UUID.randomUUID().toString()
            val rel = "media/$id.$ext"
            val dest = file(rel)
            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                }
            } ?: return@withContext null
            val isVideo = mime.startsWith("video/")
            val (w, h, thumbRel) = makeThumb(rel, id, isVideo)
            ImportedMedia(rel, thumbRel, mime, isVideo, dest.length(), w, h, suggestedName ?: uri.lastPathSegment.orEmpty())
        } catch (e: Exception) {
            null
        }
    }

    /** Creates (or re-creates) the thumbnail for a stored media file. Returns (width, height, thumbRelPath or ""). */
    fun makeThumb(rel: String, id: String = File(rel).nameWithoutExtension, isVideo: Boolean): Triple<Int, Int, String> {
        val src = file(rel)
        val thumbRel = "thumbs/$id.jpg"
        return try {
            if (isVideo) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(src.absolutePath)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val frame = r.getFrameAtTime(0) ?: return Triple(w, h, "")
                    saveThumb(scaleDown(frame, THUMB_PX), thumbRel)
                    Triple(w, h, thumbRel)
                } finally { r.release() }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(src.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB_PX) }
                var bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return Triple(bounds.outWidth, bounds.outHeight, "")
                bmp = rotateIfNeeded(src, bmp)
                saveThumb(scaleDown(bmp, THUMB_PX), thumbRel)
                Triple(bounds.outWidth, bounds.outHeight, thumbRel)
            }
        } catch (e: Exception) {
            Triple(0, 0, "")
        }
    }

    /** Decodes an image for full-screen viewing at roughly [maxPx] on the long side. */
    fun decodeForDisplay(rel: String, maxPx: Int): Bitmap? = try {
        val f = file(rel)
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, b)
        val o = BitmapFactory.Options().apply { inSampleSize = sampleSize(b.outWidth, b.outHeight, maxPx) }
        BitmapFactory.decodeFile(f.absolutePath, o)?.let { rotateIfNeeded(f, it) }
    } catch (e: Exception) { null }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        val longSide = maxOf(w, h)
        while (longSide / (s * 2) >= target) s *= 2
        return s
    }

    private fun scaleDown(b: Bitmap, target: Int): Bitmap {
        val longSide = maxOf(b.width, b.height)
        if (longSide <= target) return b
        val f = target.toFloat() / longSide
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun rotateIfNeeded(f: File, b: Bitmap): Bitmap {
        val orientation = try { ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val degrees = when (orientation) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        if (degrees == 0f) return b
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun saveThumb(b: Bitmap, rel: String) {
        FileOutputStream(file(rel)).use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** A file + content Uri for the camera app to write into. */
    fun newCameraTarget(): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "cam-${UUID.randomUUID()}.jpg")
        return f to FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    fun delete(rel: String) { if (rel.isNotEmpty()) file(rel).delete() }

    fun usage(dbFile: File): StorageUsage {
        fun size(d: File) = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val wal = File(dbFile.path + "-wal"); val shm = File(dbFile.path + "-shm")
        return StorageUsage(size(mediaDir), size(thumbDir), dbFile.length() + wal.length() + shm.length(), mediaDir.listFiles()?.size ?: 0)
    }

    /** Removes media and thumbnail files that no asset row references any more. Returns bytes freed. */
    fun deleteOrphans(referenced: Set<String>): Long {
        var freed = 0L
        for (dir in listOf(mediaDir, thumbDir)) dir.listFiles()?.forEach { f ->
            val rel = "${dir.name}/${f.name}"
            if (rel !in referenced) { freed += f.length(); f.delete() }
        }
        return freed
    }

    /** Colourful generated picture used by the optional sample data (no real photos are needed). */
    fun createPlaceholder(label: String, hue: Float, w: Int = 800, h: Int = 1000): ImportedMedia {
        val id = UUID.randomUUID().toString()
        val rel = "media/$id.jpg"
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.HSVToColor(floatArrayOf(hue, 0.35f, 1f)), Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.45f, 0.95f)), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null; p.color = Color.argb(70, 255, 255, 255)
        c.drawCircle(w * 0.8f, h * 0.2f, w * 0.3f, p); c.drawCircle(w * 0.15f, h * 0.85f, w * 0.22f, p)
        p.color = Color.argb(230, 60, 40, 90); p.textSize = w / 11f; p.isFakeBoldText = true; p.textAlign = Paint.Align.CENTER
        val words = label.split(' '); var line = ""; var y = h * 0.45f
        for (wd in words) { if ((line + " " + wd).length > 14) { c.drawText(line.trim(), w / 2f, y, p); y += p.textSize * 1.3f; line = wd } else line += " $wd" }
        c.drawText(line.trim(), w / 2f, y, p)
        FileOutputStream(file(rel)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val (_, _, thumb) = makeThumb(rel, id, false)
        return ImportedMedia(rel, thumb, "image/jpeg", false, file(rel).length(), w, h, "sample")
    }

    companion object { const val THUMB_PX = 480 }
}
