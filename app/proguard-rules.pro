# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Maintain debugging/stacktrace attributes
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Deprecated,*Annotation*
-renamesourcefileattribute SourceFile

# --- Application Models and Serialization Rules ---
# Prevent obfuscating or stripping model and database entity classes,
# which are used for JSON serialization/deserialization and database persistence.
-keep class com.devson.vedinsta.model.** { *; }
-keep class com.devson.vedinsta.database.** { *; }
-keep class com.devson.vedinsta.viewmodel.** { *; }

# --- Gson Rules (R8 Full Mode compatible) ---
# Keep Gson annotations and reflection support
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# --- Room Database Rules ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# --- Chaquopy (Python integration) Rules ---
# Keep all Chaquopy internal reflection bindings
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# --- Coil (Image Loader) Rules ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- OkHttp & Okio Rules ---
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# --- Coroutines Rules ---
-dontwarn kotlinx.coroutines.**

# --- AndroidX Security/Crypto Rules ---
-dontwarn androidx.security.crypto.**