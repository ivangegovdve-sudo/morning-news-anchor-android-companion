# kotlinx.serialization ------------------------------------------------------------------
# R8 strips the synthetic $$serializer members that @Serializable classes rely on, which
# fails at runtime rather than at build time. Keep them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.blumenkraft.morningnewsanchor.companion.data.models.**$$serializer { *; }
-keepclassmembers class com.blumenkraft.morningnewsanchor.companion.data.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.blumenkraft.morningnewsanchor.companion.data.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.blumenkraft.morningnewsanchor.companion.data.remote.**{ *; }

# Ktor / supabase-kt ---------------------------------------------------------------------
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-keep class io.ktor.client.engine.okhttp.** { *; }

# Retrofit -------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp ---------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
