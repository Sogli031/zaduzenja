# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.http.** { *; }

-dontwarn com.google.api.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontwarn com.google.android.gms.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep model classes used for JSON serialization
-keep class * extends com.google.api.client.json.GenericJson { *; }
-keep class * extends com.google.api.client.util.GenericData { *; }

# Guava
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.lang.model.element.Modifier
-dontwarn org.checkerframework.**
-dontwarn sun.misc.Unsafe

# Keep application classes
-keep class com.zaduzenja.app.** { *; }
