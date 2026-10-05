# FleetTrackPro - ProGuard / R8 rules

# --- Retrofit ---
# Jangan obfuscate interface API dan method-nya agar reflection Retrofit tetap jalan.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep,allowobfuscation,allowshrinking interface com.industri.fleettrack.data.remote.api.CourierApiService
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- Gson / DTO ---
# Field DTO hanya diisi lewat reflection, nama field harus dipertahankan.
-keepclassmembers,allowobfuscation class com.industri.fleettrack.data.remote.api.** {
    <fields>;
    <init>();
}
-keep class com.industri.fleettrack.data.remote.api.ManifestOrderDto { *; }
-keep class com.industri.fleettrack.data.remote.api.StatusUpdateResponseDto { *; }
-keepclassmembers enum * { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Room ---
# Room membangkitkan Implementasi (AppDatabase_Impl, DeliveryDao_Impl) saat compile.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Entity class * { <init>(); }
-keep class com.industri.fleettrack.data.local.AppDatabase_Impl { <init>(); }
-keep class com.industri.fleettrack.data.local.dao.** { *; }
-dontwarn androidx.room.paging.**

# --- Kotlin metadata (diperlukan oleh kotlinx-coroutines & reflection) ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontnote kotlin.**
-dontwarn org.jetbrains.annotations.**

# --- Timber ---
-dontwarn timber.log.**