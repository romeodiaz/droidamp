# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.droidamp.**$$serializer { *; }
-keepclassmembers class com.droidamp.** {
    *** Companion;
}
-keepclasseswithmembers class com.droidamp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

