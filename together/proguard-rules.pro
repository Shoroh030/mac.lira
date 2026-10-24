# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# JSON
-keep class org.json.** { *; }

# Android Support / X
-keep class androidx.** { *; }

# Preserve R class
-keep class com.lira.vmest.R { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Supabase / HTTP
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
