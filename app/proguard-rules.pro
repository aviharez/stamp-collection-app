# R8 keep rules for the release build. Keep this list short: each rule says why it exists.

# play-services-ads pulls in WorkManager 2.7 with Room 2.2, which creates WorkDatabase_Impl by
# reflection. Its own rule (-keep class * extends RoomDatabase) no longer keeps the constructor
# in R8 full mode (AGP 9 default), so release builds crashed on launch in InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
