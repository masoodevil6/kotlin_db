# اجرای Migration، Status و محدودیت‌های فعلی

دستورات Migration از API رسمی `DatabaseHandleApi.migration { ... }` اجرا می‌شوند. Configuration از Registration همان Handle خوانده می‌شود و callback در Operation Scope مدیریت‌شدهٔ همان Handle اجرا می‌شود. هر فرمان بلافاصله اجرا می‌شود و `ExecuteResult` خودش را برمی‌گرداند.

```kotlin
val initialized: Boolean = database.migration { init() }.toValue().toValue()
check(initialized)

val applied: Boolean = database.migration { migrate() }.toValue().toValue()
check(applied)

val report: MigrationStatusReport = database.migration { status() }.toValue().toValue()
val current: MigrationVersion? = database.migration { current() }.toValue().toValue()
```

`ExecuteResult.toValue()` خطای اصلی را دوباره پرتاب می‌کند؛ موفقیت بدون مقدار نیز خطای قرارداد است. اگر چند فرمان را در یک block بنویسید، Handle همان Operation Scope و Connection را برای کل block نگه می‌دارد، اما هر فرمان Migration قفل سیستم را جداگانه می‌گیرد و آزاد می‌کند.

## فرمان‌ها

| فرمان | رفتار فعلی | پیش‌شرط و نتیجه |
|---|---|---|
| `init()` | جدول‌های داخلی `system_migration_tag`، `system_migration_version` و `system_migration` را با تعریف‌های `ifNotExists()` ایجاد می‌کند. Migrationهای کاربر را اجرا نمی‌کند. | باید پیش از فرمان‌هایی که History می‌خوانند با موفقیت اجرا شود. برای این فرمان Global Failure Gate و `SystemLock.MIGRATION` اجرا نمی‌شوند. `ifNotExists()` ساختار جدول موجود را ارتقا نمی‌دهد؛ مثلاً ستون تازه را به جدول قدیمی اضافه نمی‌کند. |
| `current()` | جدیدترین Version اعمال‌شده را بر اساس ترتیب Configuration برمی‌گرداند؛ اگر هیچ‌کدام اعمال نشده باشد، مقدار `null` موفق است. | History خوانده و اعتبارسنجی می‌شود؛ History ناقص یا نامعتبر می‌تواند به `ExecuteResult.Failure` منجر شود. این فرمان قفل Migration نمی‌گیرد. |
| `status()` | History را می‌خواند و `MigrationStatusReport` شامل Versionها، Migrationها، موارد ناشناخته و `issues` می‌سازد. | گزارش تشخیصی است و وضعیت‌های `APPLIED`، `PENDING`، `UNKNOWN` یا `INVALID` را نشان می‌دهد؛ به‌تنهایی Gate اجرایی نیست. قفل Migration نمی‌گیرد و در رقابت با اجرای هم‌زمان ممکن است چند خواندن آن وضعیت‌های زمانی متفاوت را ببیند. |
| `migrate()` | تمام Versionهای Pending را به ترتیب Configuration تا آخرین Version ثبت‌شده اجرا می‌کند. | Versionهای قبلاً اعمال‌شده در مسیر عادی دوباره انتخاب نمی‌شوند. |
| `migrate(version)` | Versionهای Pending را به ترتیب Configuration تا **و شامل** Version مقصد اجرا می‌کند. | مقصد باید در Configuration باشد و قبلاً اعمال نشده باشد. |
| `rollback()` | فقط جدیدترین Version اعمال‌شده را Rollback می‌کند. | Migrationهای همان Version به ترتیب معکوس Declaration اجرا می‌شوند. |
| `rollback(version)` | Versionهای اعمال‌شدهٔ جدیدتر از مقصد را از جدید به قدیم Rollback می‌کند و خود مقصد را نگه می‌دارد. | مقصد باید در Configuration و در موقعیت معتبر نسبت به Version فعلی باشد. |

UP، Versionها و Migrationهای هر Version را به ترتیب ثبت اجرا می‌کند. DOWN، Versionها و Migrationهای انتخاب‌شده را به ترتیب معکوس اجرا می‌کند. Migrationهای یک Version ترتیبی‌اند؛ اجرای موازی در این مسیر انجام نمی‌شود.

تفاوت مقصد را با Versionهای پیکربندی‌شده در نظر بگیرید:

```kotlin
val target = AppMigrationTags.Versions.V1

// UP: مقصد هم اعمال می‌شود.
database.migration { migrate(target) }.toValue().toValue()

// DOWN: Versionهای بعد از مقصد حذف می‌شوند؛ خود target باقی می‌ماند.
database.migration { rollback(target) }.toValue().toValue()
```

اگر مقصد آخرین Version باشد، `rollback(target)` Version جدیدتری برای حذف ندارد. برای بازگرداندن آخرین Version کافی است `rollback()` را فراخوانی کنید.

## گزارش Status

نوع‌های عمومی گزارش در Package `model.migration_status` قرار دارند: `MigrationStatusReport`، `MigrationVersionStatus` و `MigrationItemStatus`. هر دو enum وضعیت Version و Migration این مقادیر را دارند: `APPLIED`، `PENDING`، `UNKNOWN` و `INVALID`.

```kotlin
val report: MigrationStatusReport = database.migration { status() }.toValue().toValue()

report.versions.forEach { version ->
    println("${version.name}: ${version.status}")
    version.migrations.forEach { migration ->
        println("  ${migration.id}: ${migration.status}")
    }
}
report.issues.forEach { issue -> println("${issue.code}: ${issue.message}") }
```

گزارش می‌تواند `unknownVersions`، `unknownMigrations` و `unknownTags` را نیز شامل شود. `status()` دادهٔ تشخیصی می‌دهد؛ موفق‌بودن دریافت Report به معنی قابل‌اجرا بودن Migration نیست.

## Global Failure Gate

چهار فرمان `migrate()`، `migrate(version)`، `rollback()` و `rollback(version)` قبل از Selection، Preflight اجرایی، Marker و Writeهای Migration، تمام Version rowهای ذخیره‌شده را از History می‌خوانند. هر Version که Status آن `3` (`STATUS_MIGRATE_COMPLETED`) نباشد کل اجرای رسمی را متوقف می‌کند؛ حتی اگر آن Version خارج از محدودهٔ مقصد فعلی باشد. این Gate بعد از گرفتن `SystemLock.MIGRATION` اجرا می‌شود. گرفتن قفل یک Query کنترلی است، نه Write پایدار Migration.

این قرارداد را به `init()`، `current()` یا `status()` تعمیم ندهید:

- `init()` فقط DDL جدول‌های سیستمی را اجرا می‌کند؛ Gate و قفل این چهار فرمان را ندارد.
- `current()` History را می‌خواند و اعتبارسنجی می‌کند و در History ناقص/نامعتبر ممکن است Failure بدهد، اما قفل Migration را نمی‌گیرد.
- `status()` گزارش تشخیصی می‌سازد و وضعیت ناقص را در Report نشان می‌دهد؛ اجرای Migration را آغاز نمی‌کند و قفل Migration نمی‌گیرد.

## Statusهای پایدار Version

Source فعلی این معانی را تعریف می‌کند:

| مقدار | ثابت | استفادهٔ فعلی |
|---:|---|---|
| 0 | `STATUS_MIGRATE_STARTED` | مقدار پیش‌فرض ستون؛ با درج Version جدید ثبت می‌شود. |
| 1 | `STATUS_MIGRATE_FAILED_UP` | تعریف شده، اما در مسیرهای Production بررسی‌شده نوشته نمی‌شود. |
| 2 | `STATUS_MIGRATE_FAILED_RECORD` | تعریف شده، اما در مسیرهای Production بررسی‌شده نوشته نمی‌شود. |
| 3 | `STATUS_MIGRATE_COMPLETED` | پس از موفقیت کامل UP ثبت می‌شود و تنها Status عبوری از Global Failure Gate است. |
| 4 | `STATUS_ROLLBACK_STARTED` | پیش از اجرای DOWN ثبت می‌شود. |
| 5 | `STATUS_ROLLBACK_FAILED_DOWN` | تعریف شده، اما در مسیرهای Production بررسی‌شده نوشته نمی‌شود. |
| 6 | `STATUS_ROLLBACK_FAILED_RECORD` | تعریف شده، اما در مسیرهای Production بررسی‌شده نوشته نمی‌شود. |

وجود Constant به معنی فعال بودن ثبت Failure نیست. شکست UP می‌تواند Version row را با Status `0` باقی بگذارد؛ شکست DOWN پس از ثبت Marker می‌تواند Status `4` باقی بگذارد. هیچ Status شکست خودکاری در این مسیرها ثبت نمی‌شود.

## System Lock و Execution Scope

Orchestration چهار فرمان UP/DOWN از `SystemLock.MIGRATION` استفاده می‌کند. در مسیر فعلی، قفل Session Advisory برای Dialectهای MySQL و MariaDB روی Connection متعلق به Operation Scope گرفته می‌شود؛ خواندن History، اجرای callback و Release از همان Session استفاده می‌کنند. قفل پیش از خواندن History گرفته و بعد از پایان callback آزاد می‌شود. پایان Session نیز قفل session-level را آزاد می‌کند.

لایهٔ Operation برای timeout پارامتر می‌پذیرد، ولی Migration orchestration فعلی مقدار ثابت ۶۰ ثانیه می‌فرستد و Migration API مصرف‌کننده گزینه‌ای برای تنظیم آن ندارد. اگر callback موفق باشد و Release شکست بخورد، نتیجهٔ عملیات می‌تواند Failure شود. اگر callback شکست بخورد و Release نیز با Failure عادی روبه‌رو شود، Failure callback حفظ می‌شود و خطای Release به آن Suppressed می‌شود. اگر Release یک `Error` ایجاد کند، آن Error به‌عنوان Fatal منتشر می‌شود و خطای قبلی را به‌صورت Suppressed نگه می‌دارد.

قفل بر اساس `SystemLock.MIGRATION` و Database Identity پیکربندی‌شده نام‌گذاری می‌شود؛ تضمین آن در سطح کل سرور فیزیکی یا همهٔ Aliasهای مقصد نیست. در Integration واقعی MariaDB 10.4.28، دو Database Identity متفاوت روی همان سرور و Schema توانستند قفل‌های مستقل بگیرند. برای جلوگیری از اجرای هم‌زمان، Registrationهایی که باید همدیگر را serialize کنند باید Identity یکسان داشته باشند.

این مسیر Migration برای هر Version Transaction مستقلی ایجاد نمی‌کند. DDL روی Operation Scope و Connection همان Operation اجرا می‌شود. بنابراین Atomicity کامل Migration یا بازگردانی تضمین‌شدهٔ DDL را فرض نکنید؛ ماندگاری و rollbackپذیری DDL به دیتابیس و دستور وابسته است.

## Failure و Recovery

Global Failure Gate اجرای رسمی بعدی را متوقف می‌کند، اما خودش Recovery انجام نمی‌دهد و به‌تنهایی میزان تغییر واقعی Schema را مشخص نمی‌کند. شکست پس از DDL ممکن است Schema و History را از هم جدا کند؛ پاسخ مبهم SQL نیز می‌تواند وضعیت پایدار سرور را نامعلوم بگذارد. اجرای دوباره، اصلاح Status یا اجرای دستی DDL را بر اساس حدس انجام ندهید. مسیر Recovery خودکار در API فعلی وجود ندارد.

`init()` فقط جدول‌های سیستمی را در صورت نبودن ایجاد می‌کند و ارتقای Schema جدول‌های موجود را انجام نمی‌دهد. اگر ساختار قبلی جدول با تعریف فعلی سازگار نیست، مسیر ارتقای خودکار در این مرحله مستند یا پیاده‌سازی نشده است.

## محدودهٔ API مصرف‌کننده

مسیر توصیه‌شده، Aggregate dependency و extension عمومی `DatabaseHandleApi.migration` است. مصرف‌کننده برای اجرای Migration نیازی به ساخت `MigrationController` یا دسترسی به Serviceهای Executor ندارد. `MigrationFacade` در Source public باقی مانده ولی constructor داخلی دارد؛ API مرزی JVM و Binary Artifactهای منتشرشده جداگانه تأیید نشده‌اند.
