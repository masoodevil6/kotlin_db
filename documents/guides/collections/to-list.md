# تبدیل به `List`

`toList()` عناصر را به یک `List<T>` استاندارد Kotlin تبدیل می‌کند.

```kotlin
val userList: List<User> = users.toList()
```

پیاده‌سازی فعلی `elements.toList()` را برمی‌گرداند؛ بنابراین ساختار لیست جدید است و تغییر ساختاری بعدی در Collection، اندازه یا ترتیب همین snapshot را عوض نمی‌کند. این کپی سطحی است: reference عناصر حفظ می‌شود و objectهای mutable clone نمی‌شوند.

از این متد برای تحویل داده به APIای استفاده کنید که `List<T>` می‌خواهد. برای تبدیل مقدارهای typed Query هم extensionهای مرتبط را در [نتیجه‌های Query](query-results.md) ببینید.

**پیاده‌سازی:** `ICollection.toList` و `Collection.toList` در ماژول `collection/core`.
