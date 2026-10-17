package app.stickynotes.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Attached images live as private files in filesDir/note_images (not in the gallery). */
class ImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "note_images")

    fun file(fileName: String): File = File(dir, fileName)

    /** Copies a picked image into app storage and returns the new file name. */
    @Throws(IOException::class)
    suspend fun copyIn(uri: Uri): String = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val extension = when (appContext.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(dir, fileName)
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Can't open $uri")
        try {
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        fileName
    }

    /** Duplicates an attached image (for "Make a copy"). */
    suspend fun duplicate(fileName: String): String? = withContext(Dispatchers.IO) {
        val source = file(fileName)
        if (!source.exists()) return@withContext null
        val copyName = "${UUID.randomUUID()}.${source.extension.ifEmpty { "jpg" }}"
        try {
            source.copyTo(File(dir, copyName))
            copyName
        } catch (e: IOException) {
            null
        }
    }

    suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { file(fileName).delete() }
    }

    /** Removes files no image row points to (left over after a crash). Skips anything from the last hour. */
    suspend fun deleteOrphans(referenced: Set<String>) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            dir.listFiles()?.forEach { f ->
                if (f.name !in referenced && f.lastModified() < cutoff) f.delete()
            }
        }
    }
}
