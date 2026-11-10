package dev.bastian.bitacora.data.system

import android.content.Context
import android.net.Uri
import dev.bastian.bitacora.domain.PhotoStorage
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class FilePhotoStorage(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : PhotoStorage {

    private val photosDir: File get() = File(context.filesDir, PHOTOS_DIR)

    override suspend fun import(uri: String): Result<String> = withContext(dispatcher) {
        try {
            val directory = photosDir
            if (!directory.exists() && !directory.mkdirs()) {
                return@withContext Result.failure(IOException("Cannot create $directory"))
            }
            val target = File(directory, "${UUID.randomUUID()}.jpg")
            val input = context.contentResolver.openInputStream(Uri.parse(uri))
                ?: return@withContext Result.failure(IOException("Cannot open the selected image"))
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            Result.success(target.absolutePath)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    override fun delete(path: String) {
        val file = File(path)
        // Only files this class created are ever removed.
        if (file.parentFile == photosDir) file.delete()
    }

    private companion object {
        const val PHOTOS_DIR = "photos"
    }
}
