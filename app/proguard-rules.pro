# Add project specific ProGuard rules here.
# Keep Room entities
-keep class com.honglu.typing.data.* { *; }

# Keep SoundPool usage
-keepclassmembers class com.honglu.typing.engine.SoundManager {
    public *;
}

# Keep custom Views used in XML layouts
-keep class com.honglu.typing.ui.KeyboardView { *; }
