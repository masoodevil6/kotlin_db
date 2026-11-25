# پیمایش با `forEach`

`forEach(action)` action را برای هر عنصر اجرا می‌کند. در پیاده‌سازی فعلی پیمایش با ترتیب ورود عناصر انجام می‌شود.

```kotlin
users.forEach { user ->
    println("${user.id}: ${user.name}")
}
```

مقدار action از نوع `(T) -> Unit` است؛ این متد Collection جدیدی برنمی‌گرداند. اگر لازم است نتیجهٔ تبدیل‌شده بسازید، از [map](map.md) استفاده کنید؛ اگر لازم است فقط بخشی از عناصر را نگه دارید، [filter](filter.md) را ببینید.

**پیاده‌سازی:** `ICollection.forEach` و `Collection.forEach` در ماژول `collection/core`.
