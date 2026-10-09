# kotlinx.serialization: keep generated serializers for our models.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ir.nama.**$$serializer { *; }
-keepclassmembers class ir.nama.** { *** Companion; }
-keepclasseswithmembers class ir.nama.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class ir.nama.** { *; }

# Reflection used to expand the notification shade.
-keep class android.app.StatusBarManager { *; }

# Services and receivers referenced from the manifest are kept by AAPT rules.
