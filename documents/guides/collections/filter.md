# فیلتر با `filter`

`filter(predicate)` شرط را روی عناصر پیمایش می‌کند و یک `ICollection<T>` تازه شامل عناصر منطبق می‌سازد.

```kotlin
val usersWithAName = users.filter { user ->
    user.name.startsWith("A")
}

usersWithAName.forEach { println(it.name) }
```

ترتیب نسبی عناصر حفظ می‌شود. عناصر تکراری که شرط را می‌گذرانند نیز باقی می‌مانند. منبع تغییر ساختاری نمی‌کند و خروجی مستقل از نظر ساختار است؛ خود objectهای انتخاب‌شده کپی نمی‌شوند.

اگر هیچ عنصری منطبق نباشد، خروجی Collection خالی است، نه `null`.

**پیاده‌سازی:** `ICollection.filter` و `Collection.filter` در ماژول `collection/core`.
