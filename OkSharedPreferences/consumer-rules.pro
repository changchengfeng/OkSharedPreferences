# JNI FileObserver
-keep class online.greatfeng.oksharedpreferences.fileobserver.OkFileObserverThread {
    native <methods>;
    void onEvent(int, int, java.lang.String);
}

# Public API
-keep interface online.greatfeng.oksharedpreferences.OkSharedPreferences { *; }
-keep class online.greatfeng.oksharedpreferences.OkSharedPreferences$Companion { *; }
-keepclassmembers class online.greatfeng.oksharedpreferences.OkSharedPreferences$Companion {
    public static <methods>;
}
