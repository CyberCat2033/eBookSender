# Add project specific Proguard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and share your rules in the project.

# Keep Room compiler and entities
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.Location

# Keep Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }

# Keep kotlinx serialization properties/classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep JSoup
-keep class org.jsoup.** { *; }
-dontwarn org.jspecify.**

# Keep Apache Commons Net
-keep class org.apache.commons.net.** { *; }

# Keep Junrar
-keep class com.github.junrar.** { *; }
