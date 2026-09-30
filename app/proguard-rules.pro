# Retrofit, OkHttp, Room, Hilt and Coil all ship consumer ProGuard rules, so they need no
# configuration here. Only kotlinx.serialization requires explicit keeps: R8 cannot see
# that generated serializers are reachable, because they are looked up reflectively.

# Keep the generated serializer for every @Serializable type.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The DTOs are only ever instantiated by the serialization runtime, so keep their fields.
-keepclassmembers class com.application.alfaflix.data.remote.dto.** { *; }

# Navigation Compose type-safe routes are serialized by the same mechanism.
-keepclassmembers class com.application.alfaflix.presentation.navigation.** { *; }

# Retrofit interfaces are implemented reflectively at runtime.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Generic signatures are erased by default, which breaks Retrofit's return-type parsing.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
