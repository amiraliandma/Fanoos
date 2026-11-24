# Keep gomobile / libv2ray JNI bindings AND all classes implementing them
-keep class libv2ray.** { *; }
-keep class * implements libv2ray.** { *; }
-keep class go.** { *; }
-keep class * implements go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# Keep MMKV JNI bindings AND handlers intact
-keep class com.tencent.mmkv.** { *; }
-keep class * implements com.tencent.mmkv.** { *; }

# Keep Gson models & reflection metadata
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.v2ray.ang.dto.** { *; }
-keep class com.v2ray.ang.enums.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp / Kotlin coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
