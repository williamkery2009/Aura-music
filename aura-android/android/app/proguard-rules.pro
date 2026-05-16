# Aura Music Player — ProGuard rules

# Keep Capacitor plugin classes
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * {
    @com.getcapacitor.PluginMethod *;
    @android.webkit.JavascriptInterface *;
}

# Keep our app classes
-keep class com.aura.musicplayer.** { *; }

# Keep MediaSession / AndroidX Media
-keep class androidx.media.** { *; }
-keep class androidx.media3.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES; public *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General Android
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
