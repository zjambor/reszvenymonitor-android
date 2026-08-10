# Release-szabályok (10. fázis). A kiadás minify + resource-shrink mellett készül,
# ezért itt kell megőrizni mindent, amit a kód NEM közvetlen hivatkozással ér el.
#
# A szabályok helyességét a release APK VALÓS munkamenete igazolja (belépés →
# adatletöltés → portfólió → részletek → frissítés), nem a fordítás sikere: a
# szerializációs hibák futásidőben jelentkeznek.

# --- kotlinx.serialization -----------------------------------------------------
# A generált $$serializer osztályokra és a Companion-okra reflexióval hivatkozik
# a runtime; ezek nélkül a DTO-k dekódolása futásidőben omlik el.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class hu.jamborz.reszvenymonitor.**$$serializer { *; }
-keepclassmembers class hu.jamborz.reszvenymonitor.** {
    *** Companion;
}
-keepclasseswithmembers class hu.jamborz.reszvenymonitor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- supabase-kt ---------------------------------------------------------------
# A könyvtár saját @Serializable modelljei (session, felhasználó, PostgREST-hiba)
# ugyanígy a generált szerializátorokon keresztül élnek.
-keep,includedescriptorclasses class io.github.jan.supabase.**$$serializer { *; }
-keepclassmembers class io.github.jan.supabase.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.jan.supabase.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor / OkHttp -------------------------------------------------------------
# Az OkHttp és a Ktor hoz saját consumer-szabályokat; ami marad, az az opcionális
# (nem használt) függőségek hiánya miatti figyelmeztetés.
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn java.lang.management.**

# --- MPAndroidChart ------------------------------------------------------------
# Nem reflektál, de a JitPack-AAR nem hoz consumer-szabályt: a nézetosztályokat
# és a formázó-interfészeket megtartjuk (XML-ből is példányosíthatók).
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**
