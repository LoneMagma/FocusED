# Keep Room entities and DAOs
-keep class com.focused.app.data.** { *; }

# Keep accessibility and foreground services
-keep class com.focused.app.service.** { *; }
-keep class com.focused.app.receiver.** { *; }

# Keep WorkManager workers
-keep class com.focused.app.work.** { *; }
