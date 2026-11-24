# Keep annotations, line numbers for crash stacktraces & inner classes
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable

# kotlinx.serialization
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.loongair.tripplanner.**$$serializer { *; }
-keepclassmembers class com.loongair.tripplanner.** {
    *** Companion;
}
-keepclasseswithmembers class com.loongair.tripplanner.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database, Entities & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class com.loongair.tripplanner.data.local.** { *; }
-keep @androidx.room.Dao interface com.loongair.tripplanner.data.local.** { *; }

# WebView JavascriptInterface (NetEase Yidun CAPTCHA bridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep @androidx.annotation.Keep class * { *; }

# OkHttp / Okio / Conscrypt / BouncyCastle optional platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**
