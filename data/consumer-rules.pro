# SQLCipher uses JNI; keep its classes intact when the app is minified.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
