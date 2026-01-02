
# if face "ServiceLoader" crashes:
# -keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
# -keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

#kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

# PDFBox
#If the app crashes when opening a PDF:
#-keep class com.tom_roush.pdfbox.pdmodel.** { *; }
-keep class com.tom_roush.pdfbox.util.** { *; }
-dontwarn com.tom_roush.pdfbox.**

#Room
-dontwarn androidx.room.paging.**

# Jetpack Compose
-keepattributes SourceFile,LineNumberTable