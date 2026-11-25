# Tətbiqin öz kodu azdır — adlarını/strukturunu olduğu kimi saxlayırıq.
-keep class com.wpradar.** { *; }

# DataStore Preferences daxilindəki protobuf-lite (R8 full mode üçün ehtiyat).
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
