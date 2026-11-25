# دسترسی با اندیس

`get(index)` و عملگر `[]` عنصر جایگاه خواسته‌شده را می‌خوانند. اندیس‌ها از صفر شروع می‌شوند.

```kotlin
val firstUser = users[0]
val secondUser = users.get(1)
```

اندیس معتبر باید در بازهٔ `0 until size` باشد. پیاده‌سازی فعلی دسترسی را به `MutableList` داخلی می‌سپارد؛ اندیس منفی یا بزرگ‌تر/مساوی `size` باعث `IndexOutOfBoundsException` می‌شود. پیش از دسترسی، خالی‌نبودن و محدودهٔ اندیس را بررسی کنید.

```kotlin
if (users.size > 0) {
    println(users[0])
}
```

**پیاده‌سازی:** `ICollection.get` و `Collection.get` در ماژول `collection/core`.
