# OkHttp / Okio: no reflection used by us; keep the default consumer rules shipped in the AARs.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Google Identity / Credential Manager classes are kept by their own consumer rules.

# Keep line numbers for readable crash reports in Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
