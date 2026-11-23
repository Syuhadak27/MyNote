# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.catatan.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.catatan.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.catatan.app.data.**$$serializer { *; }
-keepclassmembers class com.catatan.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.catatan.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlinx.serialization umum
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# jsoup
-dontwarn org.jsoup.**

# Compose
-dontwarn androidx.compose.**

# Native (MediaRecorder/MediaPlayer) tidak perlu aturan tambahan
