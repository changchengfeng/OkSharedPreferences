# OkSharedPreferences
a better SharedPreferences ,Can be used across processes

### Use

kotlin 
```kotlin
fun Context.getOkSharedPreferences(name: String): OkSharedPreferences;
```

java 
```java
OkSharedPreferencesKt.deleteSharedPreferences(context, "test");
final OkSharedPreferences preferences = OkSharedPreferencesKt.getOkSharedPreferences(context,"test");
```

### Releases

The release is available on Maven Central.
```
implementation("online.greatfeng:oksharedpreferences:1.0.1")
```
