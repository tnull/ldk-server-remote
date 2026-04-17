# JNA is reflective; keep its classes intact so UniFFI can find them at runtime.
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }

# Keep UniFFI's generated bindings (names matter for the native lookups).
-keep class uniffi.** { *; }
-keep class org.lightningdevkit.ldkserver.client.** { *; }
