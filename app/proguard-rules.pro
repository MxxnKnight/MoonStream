# MoonStream ProGuard / R8 rules (minify on since 0.15.139).
#
# The dangerous ones are the JNI boundaries: native code calls into Java
# by exact class/method name, so if R8 renames or strips those, the app
# crashes on launch / login / playback. Everything else is reachable
# from MainActivity and the manifest components, which R8 keeps.

# --- TDLib (CRITICAL) ---
# The Telegram engine. Its Java API (TdApi, Client) is source inside this
# module (app/src/main/java/org/drinkless/tdlib) and libtdjni.so calls it
# by name from native code. Never rename or strip any of it.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }

# Our TDLib wrapper and the backend boundary around it.
-keep class com.moon.moonstream.tdlib.** { *; }
-keep class com.moon.moonstream.backend.** { *; }

# --- Room database ---
# Entities, DAOs and the database class. Room ships its own consumer
# rules, but keep the app's DB package explicitly — a stripped entity
# field silently corrupts the library.
-keep class com.moon.moonstream.index.db.** { *; }

# --- Media3 FFmpeg audio decoders ---
# FfmpegLibrary / FfmpegAudioRenderer talk to libffmpegJNI.so via JNI.
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# --- Any native method, anywhere ---
# The standard guard: never rename a method a .so calls.
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Entry points (also kept via the manifest, listed for safety) ---
-keep class com.moon.moonstream.MainActivity { *; }
-keep class com.moon.moonstream.index.scan.ScanService { *; }
-keep class com.moon.moonstream.server.MoonStreamServerService { *; }
-keep class com.moon.moonstream.update.UpdateDownloadService { *; }
-keep class com.moon.moonstream.update.InstallReceiver { *; }
