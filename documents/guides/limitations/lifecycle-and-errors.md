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

## هم‌زمانی عملیات و مرز قفل‌ها

هر عملیات پذیرفته‌شدهٔ Handle یک Manager lease می‌گیرد، اما SQL و callback زیر یک قفل سراسری Manager اجرا نمی‌شوند. بنابراین چند Connection یا Transaction، حتی از یک Registration، می‌توانند هم‌زمان پیش بروند. Manager lease فقط شروع کار جدید پس از آغاز shutdown را می‌بندد و باعث می‌شود shutdown تا پایان کارهای پذیرفته‌شده و بستن Contextها منتظر بماند؛ این lease ترتیب Queryها یا سازگاری منطقی آن‌ها را تضمین نمی‌کند.

یک استثنای محدود، اجرای `OperationPipelineDefinition` است: Coordinator متعلق به همان نمونهٔ `DatabaseHandle` اجرای هم‌زمان Definitionها روی همان Handle را هماهنگ می‌کند. این Coordinator قفل عمومی Queryها، Pipelineهای دیگر یا Handleهای جداگانه نیست و جای قفل Migration را نمی‌گیرد.

Migration و بعضی خواندن‌های چندمرحله‌ای وضعیت، `SystemLock.MIGRATION` را به‌صورت advisory lock روی Connection همان Operation می‌گیرند. نام قفل از شناسهٔ منطقی و `DatabaseIdentity` پیکربندی‌شده ساخته می‌شود؛ در نتیجه مسیرهایی که همان قفل و هویت canonical را دارند با یکدیگر هماهنگ می‌شوند. عملیات عادی Connection و Transaction این قفل را خودکار نمی‌گیرند. این قفل نیز میان همهٔ Registrationها سراسری نیست: مقصدهایی با هویت‌های canonical متفاوت مستقل‌اند، حتی اگر یک provider سفارشی در عمل آن‌ها را به یک دیتابیس فیزیکی هدایت کند.

`DatabaseIdentity` مقصد قابل‌استخراج از تنظیمات اتصال را بازنمایی می‌کند، نه لزوماً مقصد نهایی یک provider، proxy یا router سفارشی را. اگر هویت ساخته نشود یا dialect پشتیبانی نشود، گرفتن SystemLock طبق قرارداد Operation رد می‌شود و مسیر بدون قفل جایگزین نمی‌شود.

| سازوکار | هماهنگ می‌کند | تضمین نمی‌کند |
|---|---|---|
| Manager operation lease | جلوگیری از شروع عملیات تازه پس از آغاز shutdown و انتظار برای عملیات پذیرفته‌شده | سریالی‌شدن Queryها یا Migrationهای همهٔ Registrationها |
| Coordinator مربوط به Definition | اجرای Definitionها روی همان نمونهٔ Handle | هماهنگی Queryهای عادی، Pipelineهای دیگر یا Handleهای جداگانه |
| `SystemLock.MIGRATION` | مسیرهایی که همان قفل و `DatabaseIdentity` را می‌گیرند | هماهنگی با Query عادی یا مقصدی با هویت متفاوت |
| Transaction و قفل دیتابیس | عملیاتی که واقعاً در همان Transaction یا قفل شرکت می‌کنند | هماهنگی خودکار همهٔ Registrationها یا منطق برنامهٔ بیرونی |

برای اثر جدا بودن مقصدها و Registrationها، [راهنمای ثبت چند پایگاه داده](../database-manager/database_define/multiple-databases.md) را ببینید.

## API قدیمی مبتنی بر String

constructor بدون آرگومان، `register(String, DatabaseConfig)`، `select(String)` و `registeredNames()` برای سازگاری نگه داشته شده‌اند و deprecated هستند. API جدید typed از `DatabaseKey` و `DatabaseHandleApi` استفاده می‌کند. مسیر String-based همچنان ممکن است `DatabaseContext` را افشا کند؛ آن را API جدید برای کد تازه در نظر نگیرید.
