# Keep kotlinx.serialization & Room models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.loongair.tripplanner.data.remote.**$$serializer { *; }
-keepclassmembers class com.loongair.tripplanner.data.remote.** { *** Companion; }
-keepclasseswithmembers class com.loongair.tripplanner.data.remote.** { kotlinx.serialization.KSerializer serializer(...); }
