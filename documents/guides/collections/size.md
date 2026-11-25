# خواندن اندازه

`size` خاصیت فقط‌خواندنی تعداد عناصر فعلی Collection را برمی‌گرداند.

```kotlin
val values = Collection<String>()
println(values.size) // 0

values.add("one")
println(values.size) // 1
```

چون `add()` ساختار Collection را تغییر می‌دهد، مقدار `size` پس از افزودن به‌روز است. اندازهٔ Collection خروجی `filter()` یا `map()` مربوط به همان خروجی تازه است و اندازهٔ منبع را تغییر نمی‌دهد.

**پیاده‌سازی:** `ICollection.size` و getter مربوط به `Collection.size` در ماژول `collection/core`.
