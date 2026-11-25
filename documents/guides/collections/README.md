# Collection

ماژول Collection یک نگه‌دارندهٔ ساده و جنریک برای عناصر هم‌نوع است. API آن از دو نوع `ICollection<T>` و `Collection<T>` تشکیل شده و برای افزودن، پیمایش، فیلتر، تبدیل، جست‌وجوی اولین مقدار، دسترسی با اندیس و تبدیل به `List` استفاده می‌شود.

این Collection، `kotlin.collections.Collection` یا `List` نیست و فقط عملیات مستندشده در این راهنما را ارائه می‌کند. پیاده‌سازی فعلی ترتیب ورود را حفظ می‌کند و تکراری‌ها را حذف نمی‌کند.

## صفحات این راهنما

| موضوع | توضیح |
|---|---|
| [قرارداد و ساختار](collection-contract.md) | تفاوت `ICollection<T>` و `Collection<T>`، ترتیب، نگه‌داری referenceها و محدودیت‌های پیاده‌سازی |
| [افزودن عنصر](add.md) | ساخت Collection و افزودن مقدار با `add()` |
| [پیمایش](for-each.md) | اجرای یک action برای هر عنصر با `forEach()` |
| [اندازه](size.md) | خواندن تعداد فعلی عناصر با `size` |
| [دسترسی اندیسی](indexed-access.md) | خواندن عنصر با `get()` یا `[]` و رفتار اندیس نامعتبر |
| [فیلتر](filter.md) | ساخت Collection تازه از عناصر منطبق با predicate |
| [تبدیل عناصر](map.md) | تبدیل نوع یا مقدار عناصر با `map()` |
| [یافتن اولین مقدار](first-or-null.md) | جست‌وجو با `firstOrNull()` و نتیجهٔ nullable |
| [تبدیل به List](to-list.md) | گرفتن یک کپی سطحی از فهرست عناصر |
| [نتیجه‌های Query](query-results.md) | استفاده از `ICollection` در `executeAs` و استخراج از `ExecuteResult` |
| [مثال‌های ماژول](examples.md) | نگاشت راهنماها به فایل‌های مثال Kotlin |

## شروع سریع

برای استفاده از پیاده‌سازی ماژول، dependency مربوط به `collection:core` را اضافه کنید:

```kotlin
dependencies {
    implementation(project(":collection:core"))
}
```

```kotlin
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

data class User(val id: Int, val name: String)

val users: ICollection<User> = Collection()
users.add(User(1, "Ada"))
users.add(User(2, "Grace"))

val names: ICollection<String> = users.map(User::name)
names.forEach(::println)
```

برای جزئیات، از صفحهٔ [قرارداد و ساختار](collection-contract.md) شروع کنید. اتصال این نوع به خروجی typed Query در [نتیجه‌های Query](query-results.md) شرح داده شده است.
