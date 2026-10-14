# Add project specific ProGuard rules here.
# https://developer.android.com/studio/build/shrink-code

-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.mlkit.**

# Isolated DeepAR curtains test
-keep class ai.deepar.** { *; }
-dontwarn ai.deepar.**

