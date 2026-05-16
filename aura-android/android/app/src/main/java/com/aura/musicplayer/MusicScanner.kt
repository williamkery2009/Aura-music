package com.aura.musicplayer

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

object MusicScanner {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Supported audio MIME types
    private val AUDIO_MIME = setOf(
        "audio/mpeg", "audio/mp4", "audio/flac", "audio/ogg",
        "audio/wav", "audio/x-wav", "audio/aac", "audio/opus",
        "audio/x-ms-wma", "audio/vnd.wave"
    )

    /**
     * Scan device music library via MediaStore.
     * @param path  Optional folder path filter (null = all music)
     * @param taskId Unique scan task identifier
     * @param onDone Callback with JSON string of tracks array
     */
    fun scan(context: Context, path: String?, taskId: String, onDone: (String) -> Unit) {
        scope.launch {
            try {
                val tracks = queryMediaStore(context, path)
                onDone(tracks.toString())
            } catch (e: Exception) {
                onDone("""{"error":"${e.message}"}""")
            }
        }
    }

    private suspend fun queryMediaStore(context: Context, pathFilter: String?): JSONArray =
        withContext(Dispatchers.IO) {

        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,          // absolute path
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )

        // Selection: only music (IS_MUSIC=1) and not ringtones
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} = 1")
            append(" AND ${MediaStore.Audio.Media.DURATION} > 10000")  // >10s
            if (!pathFilter.isNullOrBlank()) {
                append(" AND ${MediaStore.Audio.Media.DATA} LIKE ?")
            }
        }
        val selectionArgs = if (!pathFilter.isNullOrBlank()) {
            arrayOf("$pathFilter%")
        } else null

        val sortOrder = "${MediaStore.Audio.Media.ARTIST} ASC, " +
            "${MediaStore.Audio.Media.ALBUM} ASC, " +
            "${MediaStore.Audio.Media.TRACK} ASC"

        val results = JSONArray()

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val trackCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val modCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id      = cursor.getLong(idCol)
                val mime    = cursor.getString(mimeCol) ?: continue
                if (mime !in AUDIO_MIME) continue

                val albumId = cursor.getLong(albumIdCol)
                // Content URI for playback
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                // Artwork URI (album art via MediaStore)
                val artUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                val obj = JSONObject().apply {
                    put("id",         id)
                    put("uri",        contentUri.toString())
                    put("path",       cursor.getString(dataCol) ?: "")
                    put("title",      cursor.getString(titleCol) ?: "Unknown")
                    put("artist",     cursor.getString(artistCol) ?: "Unknown Artist")
                    put("album",      cursor.getString(albumCol) ?: "Unknown Album")
                    put("albumId",    albumId)
                    put("artUri",     artUri.toString())
                    put("duration",   cursor.getLong(durationCol))
                    put("size",       cursor.getLong(sizeCol))
                    put("mime",       mime)
                    put("track",      cursor.getInt(trackCol))
                    put("year",       cursor.getInt(yearCol))
                    put("modified",   cursor.getLong(modCol))
                    // Hash for lyrics cache key matching
                    put("hash",       "${cursor.getString(titleCol)}_${cursor.getString(artistCol)}_${cursor.getLong(durationCol)}".hashCode().toString())
                }
                results.put(obj)
            }
        }

        results
    }

    fun cancel() { scope.cancel() }
}
