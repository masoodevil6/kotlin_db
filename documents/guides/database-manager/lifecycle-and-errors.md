# Lifecycle، نتیجه و خطاها

Manager مالک registrationهای typed و Contextهایی است که برای آن‌ها ساخته است؛ مالک دیتابیس یا Connection خارجی نیست. `get` و `registeredKeys` فقط catalog را می‌خوانند. نخستین Query، Transaction، Schema یا Migration لازم ممکن است Context را lazy بسازد.

`shutdown()` دیگر عملیات جدیدی نمی‌پذیرد، منتظر عملیات پذیرفته‌شده می‌ماند و Contextهای ساخته‌شده را می‌بندد. فراخوانی آن از داخل عملیات فعال همان Manager رد می‌شود. `close()` همان `shutdown()` را انجام می‌دهد، بنابراین `use { ... }` نیز قابل استفاده است. خطای بستن Contextها به فراخوانی‌کنندهٔ shutdown منتقل می‌شود؛ cleanup سایر Contextها هم ادامه پیدا می‌کند. فراخوانی هم‌زمانی که منتظر shutdown فعال مانده است همان خطا را می‌گیرد؛ فراخوانی بعد از تکمیل shutdown بی‌اثر است.

```kotlin
DatabaseManager {
    database(MainDatabase) { config = mainConfig }
}.use { manager ->
    val database = manager[MainDatabase]
    // Query/Transaction/Schema calls
}
```

در عملیات handle، شکست به‌شکل `ExecuteResult.Failure` گزارش می‌شود؛ `Error` به‌عنوان Failure پنهان نمی‌شود. `ExecuteResult.Success.result` می‌تواند nullable باشد، پس نتیجه را با قرارداد خود عملیات بررسی کنید. تبدیل Failure به لیست خالی، null یا موفقیت، رفتار امنی نیست.

پیکربندی شامل اطلاعات اتصال حساس است؛ گذرواژه را در source، فایل‌های Gradle، log یا خروجی diagnostic ثبت نکنید. `DatabaseConfig` و `DatabaseConnectionConfig` نمایش متنی خود را با مقدار گذرواژهٔ redacted ارائه می‌کنند، اما همچنان باید از چاپ تنظیمات در محیط‌های حساس پرهیز کرد.

## API قدیمی مبتنی بر String

constructor بدون آرگومان، `register(String, DatabaseConfig)`، `select(String)` و `registeredNames()` برای سازگاری نگه داشته شده‌اند و deprecated هستند. API جدید typed از `DatabaseKey` و `DatabaseHandleApi` استفاده می‌کند. مسیر String-based همچنان ممکن است `DatabaseContext` را افشا کند؛ آن را API جدید برای کد تازه در نظر نگیرید.
