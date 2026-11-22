# ============================================================================
# Arena (com.arena.aid) R8 / ProGuard Rules
# ============================================================================

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-optimizationpasses 5
-overloadaggressively
-allowaccessmodification

# Strip debug logs in release APK for maximum speed on 4GB RAM devices
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep Application & Activity classes with default constructors (AGP 9 R8 full mode)
-keep public class * extends android.app.Application {
    public <init>();
}
-keep public class * extends androidx.activity.ComponentActivity {
    public <init>();
}

# Keep Serializable models & Retrofit interfaces
-keep,allowshrinking,allowobfuscation class com.arena.aid.data.model.** { *; }
-keep interface com.arena.aid.data.remote.** { *; }
-keep,allowshrinking,allowobfuscation @kotlinx.serialization.Serializable class com.arena.aid.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Kotlinx Serialization generated serializers
-keepclassmembers class com.arena.aid.** {
    *** Companion;
}
-keepclasseswithmembers class com.arena.aid.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep @androidx.room.Entity class * {
    public <init>();
    *;
}
-keep interface * extends androidx.room.Dao {
    <methods>;
}
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper {
    public <init>(...);
}

# Credential Manager / Google Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.gms.**

# Keep WebView JavascriptInterface methods for ArenaSessionBridge in release APK
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.arena.aid.data.remote.ArenaSessionBridge$* { *; }
