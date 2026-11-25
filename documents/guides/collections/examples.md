# مثال‌های ماژول

نمونه‌های اجرایی ماژول در `collection/examples/src/main/kotlin` قرار دارند. هر فایل، یک رفتار اصلی را جداگانه نشان می‌دهد:

| مثال Kotlin | موضوع مستند |
|---|---|
| `A1ExampleCollectionAdd.kt` | [افزودن عنصر](add.md) |
| `A2ExampleCollectionForEach.kt` | [پیمایش](for-each.md) |
| `A3ExampleCollectionFilter.kt` | [فیلتر](filter.md) |
| `A4ExampleCollectionSize.kt` | [اندازه](size.md) |
| `A5ExampleCollectionMap.kt` | [تبدیل عناصر](map.md) |
| `A6ExampleCollectionFirstOrNull.kt` | [یافتن اولین عنصر](first-or-null.md) |
| `A7ExampleCollectionToList.kt` | [تبدیل به List](to-list.md) |
| `A8ExampleCollectionGet.kt` | [دسترسی اندیسی](indexed-access.md) |
| `UserModel.kt` | مدل دادهٔ استفاده‌شده در مثال‌ها |
| `main.kt` | اجرای انتخابی مثال‌ها |

`main.kt` همهٔ مثال‌ها را به‌طور پیش‌فرض اجرا می‌کند. هرکدام را می‌توان با Gradle project property متناظر خاموش کرد؛ مقدار property باید دقیقاً `true` یا `false` باشد:

| مثال | Property |
|---|---|
| add | `collection.example.run.add` |
| forEach | `collection.example.run.foreach` |
| filter | `collection.example.run.filter` |
| size | `collection.example.run.size` |
| map | `collection.example.run.map` |
| firstOrNull | `collection.example.run.first-or-null` |
| toList | `collection.example.run.to-list` |
| get | `collection.example.run.get` |

برای نمونه، تنظیم `collection.example.run.filter=false` اجرای مثال filter را غیرفعال می‌کند؛ نام Gradle task برای اجرای examples را از ساختار build همین پروژه بگیرید.

ماژول examples به `collection:core` وابسته است. مثال‌های بالا API ماژول را نشان می‌دهند و پیاده‌سازی نهایی را باید در صفحات این پوشه و Source مربوطه بررسی کرد.
