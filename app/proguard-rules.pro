# Keep gomobile / libv2ray JNI bindings intact
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# Keep MMKV JNI bindings intact
-keep class com.tencent.mmkv.** { *; }

# Keep Gson models & reflection metadata
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
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
