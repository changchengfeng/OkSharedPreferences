# OkSharedPreferences
a better SharedPreferences ,Can be used across processes

### Use

kotlin 
```kotlin
//  default ,it don't migrate old SharedPreferences
fun Context.getOkSharedPreferences(name: String): OkSharedPreferences;
// if you want to migrate old SharedPreferences ,migration need be true 
fun Context.getOkSharedPreferences(name: String,migration: Boolean): OkSharedPreferences
// delete OkSharedPreferences
fun Context.deleteSharedPreferences(name: String): Boolean {
    return OkSharedPreferencesManager.getInstance(this).deleteSharedPreferences(name)
}
```

java 

```java
//  default ,it don't migrate old SharedPreferences
final OkSharedPreferences preferences=OkSharedPreferencesKt.getOkSharedPreferences(context,"test");
// if you want to migrate old SharedPreferences ,migration must be true 
final OkSharedPreferences preferences=OkSharedPreferencesKt.getOkSharedPreferences(context,"test",true);
// delete OkSharedPreferences
        OkSharedPreferencesKt.deleteSharedPreferences(context,"test");
```

### Releases

The release is available on Maven Central.
```
implementation("online.greatfeng:oksharedpreferences:1.0.6")
```
