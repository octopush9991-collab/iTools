# Keep kotlinx.serialization generated serializers for project documents.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.itools.app.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
