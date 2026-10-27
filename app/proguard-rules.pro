# Keep kotlinx.serialization generated serializers used by the export format.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.equisteg.mydaycompanion.** {
    *** Companion;
}
-keepclasseswithmembers class com.equisteg.mydaycompanion.** {
    kotlinx.serialization.KSerializer serializer(...);
}
