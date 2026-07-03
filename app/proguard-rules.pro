# RawSight ProGuard Rules
# Camera2 and Compose are mostly reflection-safe.

-keep class com.rawsight.** { *; }
-keepattributes *Annotation*

# Camera2 native
-keep class android.hardware.camera2.** { *; }
