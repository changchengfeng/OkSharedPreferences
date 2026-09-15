# OkSharedPreferences
a better SharedPreferences ,Can be used across processes

### Use

kotlin 
```kotlin
//  default ,it don't migrate old SharedPreferences
fun Context.getOkSharedPreferences(name: String): OkSharedPreferences
// if you want to migrate old SharedPreferences ,migration need be true 
fun Context.getOkSharedPreferences(name: String, migration: Boolean): OkSharedPreferences
// delete OkSharedPreferences (do not use Context.deleteSharedPreferences — that is the platform API)
fun Context.deleteOkSharedPreferences(name: String): Boolean
```

java 

```java
//  default ,it don't migrate old SharedPreferences
final OkSharedPreferences preferences = OkSharedPreferencesKt.getOkSharedPreferences(context, "test");
// if you want to migrate old SharedPreferences ,migration must be true 
final OkSharedPreferences preferences = OkSharedPreferencesKt.getOkSharedPreferences(context, "test", true);
// delete OkSharedPreferences
OkSharedPreferencesKt.deleteSharedPreferences(context, "test");
```

### Multi-process

- Writes are merged with on-disk data before save (dirty-key tracking).
- Other processes are notified via inotify (`FileObserver`); call `reload()` if you need to read immediately after a remote write.
- On-disk files are bound to the APK signing certificate (`{signingId}_{name}.oksp`); only same-signature processes share storage.

### Limits

Configure before opening any instance (e.g. in `Application.onCreate`):

```kotlin
OkSharedPreferences.configureLimits(
    maxDecodeBytesPerField = 16 * 1024 * 1024,
    maxFileBytes = 64 * 1024 * 1024
)
```

### Releases

The release is available on Maven Central.
```
implementation("online.greatfeng:oksharedpreferences:1.1.3")
```
