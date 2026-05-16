package com.aura.musicplayer

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Capacitor plugin that exposes Aura native APIs to JS via Capacitor.Plugins.AuraNative
 *
 * Usage from JS:
 *   import { Plugins } from '@capacitor/core';
 *   const { AuraNative } = Plugins;
 *   await AuraNative.updateMediaSession({ title, artist, album, durationMs });
 */
@CapacitorPlugin(name = "AuraNative")
class NativeBridgePlugin : Plugin() {

    @PluginMethod
    fun updateMediaSession(call: PluginCall) {
        val activity = activity as? MainActivity ?: run { call.reject("No activity"); return }
        // Will be wired in MainActivity.registerPlugin
        call.resolve()
    }

    @PluginMethod
    fun scanMusic(call: PluginCall) {
        val path = call.getString("path")
        val taskId = "scan_${System.currentTimeMillis()}"

        MusicScanner.scan(context, path, taskId) { result ->
            val ret = JSObject()
            ret.put("taskId", taskId)
            ret.put("tracks", result)
            call.resolve(ret)
        }
    }

    @PluginMethod
    fun exportLrc(call: PluginCall) {
        val artist  = call.getString("artist", "") ?: ""
        val title   = call.getString("title",  "") ?: ""
        val content = call.getString("content","") ?: ""
        if (content.isBlank()) { call.reject("No content"); return }

        val uri = LrcManager.saveLrc(context, artist, title, content)
        val ret = JSObject()
        ret.put("uri", uri.toString())
        ret.put("fileName", "$artist - $title.lrc")
        call.resolve(ret)
    }

    @PluginMethod
    fun importLrc(call: PluginCall) {
        val uriStr = call.getString("uri") ?: run { call.reject("No uri"); return }
        val content = LrcManager.readLrc(context, android.net.Uri.parse(uriStr))
            ?: run { call.reject("Could not read file"); return }
        val ret = JSObject()
        ret.put("content", content)
        call.resolve(ret)
    }

    @PluginMethod
    fun listCachedLrc(call: PluginCall) {
        val list = LrcManager.listCached(context)
        val ret = JSObject()
        ret.put("files", list.joinToString(","))
        call.resolve(ret)
    }
    @PluginMethod
    fun getDeviceInfo(call: PluginCall) {
        val ret = JSObject()
        ret.put("ram",    Runtime.getRuntime().maxMemory() / (1024 * 1024))
        ret.put("cores",  Runtime.getRuntime().availableProcessors())
        ret.put("sdk",    android.os.Build.VERSION.SDK_INT)
        ret.put("model",  android.os.Build.MODEL)
        call.resolve(ret)
    }
}

