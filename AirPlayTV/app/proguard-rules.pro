# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep JNI native methods (will be used in Phase 4)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep protocol handler classes
-keep class com.airplay.tv.protocol.** { *; }

# Keep media decoder classes
-keep class com.airplay.tv.media.** { *; }

# Keep network classes
-keep class com.airplay.tv.network.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
