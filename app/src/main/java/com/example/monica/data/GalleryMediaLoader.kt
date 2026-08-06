package com.example.monica.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class GalleryMediaItem(
    val id: Long,
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
)

object GalleryMediaLoader {
    fun loadRecent(context: Context, limit: Int = 120): List<GalleryMediaItem> {
        val items = LinkedHashMap<Long, GalleryMediaItem>()
        loadImages(context, limit).forEach { items[it.id] = it }
        loadVideos(context, limit).forEach { items[it.id] = it }
        return items.values
            .sortedByDescending { it.id }
            .take(limit)
    }

    private fun loadImages(context: Context, limit: Int): List<GalleryMediaItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        return query(context, collection, projection, sort, limit) { id, name, mime, _ ->
            GalleryMediaItem(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                mimeType = mime.ifBlank { "image/jpeg" },
                displayName = name.ifBlank { "photo-$id.jpg" },
                isVideo = false,
            )
        }
    }

    private fun loadVideos(context: Context, limit: Int): List<GalleryMediaItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        return query(context, collection, projection, sort, limit) { id, name, mime, duration ->
            GalleryMediaItem(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                mimeType = mime.ifBlank { "video/mp4" },
                displayName = name.ifBlank { "video-$id.mp4" },
                isVideo = true,
                durationMs = duration,
            )
        }
    }

    private fun query(
        context: Context,
        collection: Uri,
        projection: Array<String>,
        sort: String,
        limit: Int,
        map: (id: Long, name: String, mime: String, duration: Long) -> GalleryMediaItem,
    ): List<GalleryMediaItem> {
        val out = mutableListOf<GalleryMediaItem>()
        val resolver = context.contentResolver
        runCatching {
            resolver.query(collection, projection, null, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(projection[0])
                val nameCol = cursor.getColumnIndexOrThrow(projection[1])
                val mimeCol = cursor.getColumnIndexOrThrow(projection[2])
                val durationCol = if (projection.size > 3 && projection[3] == MediaStore.Video.Media.DURATION) {
                    cursor.getColumnIndex(projection[3])
                } else {
                    -1
                }
                while (cursor.moveToNext() && out.size < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    out += map(id, name, mime, duration)
                }
            }
        }
        return out
    }
}

fun isVideoMime(mime: String?, fileName: String?): Boolean {
    if (!mime.isNullOrBlank() && mime.startsWith("video/", ignoreCase = true)) return true
    val name = fileName.orEmpty().lowercase()
    return name.endsWith(".mp4") ||
        name.endsWith(".mov") ||
        name.endsWith(".webm") ||
        name.endsWith(".mkv") ||
        name.endsWith(".3gp") ||
        name.endsWith(".m4v")
}
