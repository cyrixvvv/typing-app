# Add project specific ProGuard rules here.
# Keep Room entities
-keep class com.honglu.typing.data.* { *; }

# Keep SoundPool usage
-keepclassmembers class com.honglu.typing.engine.SoundManager {
    public *;
}
