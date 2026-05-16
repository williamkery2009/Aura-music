package com.aura.musicplayer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Manages .lrc lyric file import and export on the device filesystem.
 * Exposed to JS via NativeBridgePlugin.
 */
object LrcManager {

    private fun lrcDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "lrc").also { it.mkdirs() }

    /**
     * Save LRC content to a file named {artist} - {title}.lrc
     * Returns the content:// URI for sharing.
     */
    fun saveLrc(ctx: Context, artist: String, title: String, content: String): Uri {
        val fileName = sanitize("$artist - $title.lrc")
        val file = File(lrcDir(ctx), fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }

    /**
     * Read LRC content from a content:// or file:// URI.
     */
    fun readLrc(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
    } catch (e: Exception) { null }

    /**
     * List all cached .lrc files.
     */
    fun listCached(ctx: Context): List<String> =
        lrcDir(ctx).listFiles()
            ?.filter { it.extension == "lrc" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    /**
     * Delete cached .lrc for a track.
     */
    fun deleteLrc(ctx: Context, artist: String, title: String): Boolean {
        val fileName = sanitize("$artist - $title.lrc")
        return File(lrcDir(ctx), fileName).delete()
    }

    private fun sanitize(name: String) =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(200)
}
