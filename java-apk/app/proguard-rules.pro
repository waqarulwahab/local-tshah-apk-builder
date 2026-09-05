# App classes
-keep class come.tshah.app.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# gRPC + Protobuf (Firestore)
-keep class io.grpc.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn io.grpc.**
-dontwarn com.google.protobuf.**

# AndroidX lifecycle
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }

# Suppress misc warnings
-dontwarn com.google.android.play.core.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
