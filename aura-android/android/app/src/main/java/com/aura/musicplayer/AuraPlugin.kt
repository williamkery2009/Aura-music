package com.aura.musicplayer

/**
 * Plugin registration is done in MainActivity via registerPlugin(NativeBridgePlugin::class.java)
 * This file documents the Capacitor JS-side usage:
 *
 * import { registerPlugin } from '@capacitor/core';
 * const AuraNative = registerPlugin('AuraNative');
 *
 * // Scan device music
 * const { tracks } = await AuraNative.scanMusic({ path: null });
 *
 * // Get device info
 * const info = await AuraNative.getDeviceInfo();
 *
 * All other calls go through window.NativeAura (JavascriptInterface) directly
 * since they need synchronous or very low-latency access.
 */
