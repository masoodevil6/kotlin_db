# تبدیل عناصر با `map`

`map(transform)` برای هر عنصر یک مقدار خروجی می‌سازد و آن‌ها را به ترتیب در یک Collection تازه قرار می‌دهد. نوع خروجی می‌تواند با نوع ورودی متفاوت باشد: `ICollection<T>` به `ICollection<R>` تبدیل می‌شود.

```kotlin
val names: ICollection<String> = users.map { user -> user.name }
val ids: ICollection<Int> = users.map(User::id)
```

تعداد خروجی‌ها با تعداد عناصر ورودی برابر است؛ برای حذف عناصر از `filter()` استفاده کنید. منبع از نظر ساختاری تغییر نمی‌کند. objectهایی که transform برمی‌گرداند clone نمی‌شوند.

**پیاده‌سازی:** `ICollection.map` و `Collection.map` در ماژول `collection/core`.
