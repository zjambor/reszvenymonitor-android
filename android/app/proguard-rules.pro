# Release-szabályok — érdemben a 10. fázisban (aláírt kiadás) kerülnek véglegesítésre.

# kotlinx.serialization: a generált szerializátorok reflexió nélkül működnek,
# de a @Serializable osztályok mezőneveit a DTO-knál meg kell őrizni.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
