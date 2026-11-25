# بررسی وضعیت Migration با `status()`

`status()` یک گزارش تحلیلی از وضعیت ثبت‌شدهٔ Migrationها در برابر Configuration فعلی می‌سازد. این API برای عیب‌یابی و دیدن ناسازگاری‌هاست؛ Migration اجرا نمی‌کند و داده‌های جداول سیستمی را تغییر نمی‌دهد.

## فراخوانی

از داخل DSL مربوط به Handle استفاده کنید:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import model.migration_status.MigrationStatusReport

val execution: ExecuteResult<ExecuteResult<MigrationStatusReport>> =
    database.migration {
        status()
    }

when (execution) {
    is ExecuteResult.Failure -> {
        // خطای اجرای Operation بیرونی؛ برای نمونه خطای Scope یا پایان Operation
        println("Operation failed: ${execution.exception}")
    }

    is ExecuteResult.Success -> when (val command = execution.result) {
        null -> println("Migration status command returned no result")
        is ExecuteResult.Failure -> println("Status read failed: ${command.exception}")
        is ExecuteResult.Success -> {
            val report = command.result
            if (report == null) {
                println("Status report is empty")
            } else {
                report.versions.forEach { version ->
                    println("${version.name}: ${version.status}")
                    version.issues.forEach { issue ->
                        println("  ${issue.code}: ${issue.message}")
                    }
                }
                report.issues.forEach { issue ->
                    println("${issue.code}: ${issue.message}")
                }
            }
        }
    }
}
```

`DatabaseHandleApi.migration { ... }` نتیجهٔ Block را در نتیجهٔ بیرونی Operation قرار می‌دهد و `ExecuteResult` داخلی را flatten نمی‌کند. به همین دلیل نوع نتیجه در نمونه دو لایه است. اگر در برنامه‌تان یک wrapper برای اجرای Migration دارید، هر دو لایه را جداگانه بررسی کنید تا Failure اجرای Operation با Failure خود فرمان اشتباه نشود.

## گزارش چه چیزی دارد؟

`MigrationStatusReport` یک snapshot تحلیلی از Configuration و داده‌های خوانده‌شده ارائه می‌دهد:

- `versions`: Versionهای تعریف‌شده در Configuration، به ترتیب تعریف. هر مورد نام Version، وضعیت تحلیلی، شناسه‌های Tag و Version ذخیره‌شده، Migrationهای آن و Issueهای مربوط را دارد.
- `unknownVersions`: Versionهای ذخیره‌شده‌ای که در Configuration فعلی شناخته نمی‌شوند.
- `unknownMigrations`: Migrationهای ذخیره‌شده‌ای که در Configuration فعلی شناخته نمی‌شوند.
- `unknownTags`: Tagهایی با نوع ناشناخته برای Migration.
- `issues`: ناسازگاری‌ها و هشدارهای مستقل، همراه کد، پیام و شناسهٔ رکوردهای مرتبط.

وضعیت تحلیلی Version از نوع `MigrationVersionStatus` است:

| مقدار | کاربرد |
|---|---|
| `APPLIED` | Migrationهای مورد انتظار برای Version در Metadata ثبت‌شده‌اند. |
| `PENDING` | Version در Metadata هنوز ثبت نشده است. |
| `UNKNOWN` | رکوردی هست، اما هویت آن با Configuration فعلی تطبیق ندارد یا قابل شناسایی نیست. |
| `INVALID` | رابطه‌ها یا History با ساختار مورد انتظار سازگار نیستند. |
| `UP_MANUAL_RECOVERY_REQUIRED` | Validator وضعیت ذخیره‌شده را نیازمند مداخلهٔ دستی در جهت UP تشخیص داده است. |
| `DOWN_MANUAL_RECOVERY_REQUIRED` | Validator وضعیت ذخیره‌شده را نیازمند مداخلهٔ دستی در جهت DOWN تشخیص داده است. |

وضعیت هر Migration از نوع `MigrationItemStatus` است: `APPLIED`، `PENDING`، `UNKNOWN` یا `INVALID`. وضعیت‌های Version/Item در این Report، عدد ستون `system_migration_version.status` نیستند؛ آن‌ها نتیجهٔ مقایسه و تحلیل‌اند.

Issueهای مهم شامل رکوردهای تکراری یا ناسازگار، ارجاع Version خراب، History ناقص، Migration/Tag ناشناخته، ترتیب Version نادرست، History مورد انتظارِ گم‌شده و `INCOMPLETE_VERSION_HISTORY` هستند. برای وضعیت‌های دستی، Issueهای `MANUAL_RECOVERY_REQUIRED_UP` و `MANUAL_RECOVERY_REQUIRED_DOWN` جهت را جداگانه مشخص می‌کنند.

## تفاوت `status()` با `state()` و `current()`

- `status()` چند بخش از Metadata را با Configuration فعلی تطبیق می‌دهد و مجموعه‌ای از Versionها و Issueها تحویل می‌دهد.
- `state(...)` یک Version مشخص را می‌خواند و Status خام عددی، `versionId`، `tagId`، `message` و در overloadهای typed، طبقه‌بندی Operation و Migration قابل Resolve را ارائه می‌کند.
- `current()` فقط Version اعمال‌شدهٔ فعلی را برمی‌گرداند.

Report جایگزین State نیست: `MigrationStatusReport` مقدار خام Status، Checkpoint کامل یا Diagnostic اصلی Version را برای مصرف‌کننده ارائه نمی‌کند. برای دیدن یک رکورد مشخص و پیام ذخیره‌شده از `state(...)` استفاده کنید. دریافت `APPLIED` نیز به‌تنهایی مجوز اجرای Migration یا تضمین صحت Schema واقعی نیست؛ این Report Metadata را با Configuration مقایسه می‌کند.

## Snapshot، Lock و خطاها

خواندن کامل و تحلیل Report زیر یک `SystemLock.MIGRATION` انجام می‌شود. خواننده ابتدا ردیف‌های Status را می‌خواند، سپس Versionها را می‌خواند و Analyzer را پیش از خروج از همان callback اجرا می‌کند. بنابراین Migrationهای همین مسیر که همان Lock را می‌گیرند، بین این خواندن‌ها تغییر هم‌زمان ایجاد نمی‌کنند.

Scope باید یک Managed root Connection Scope فعال با `DatabaseIdentity` canonical و dialect پشتیبانی‌شدهٔ MySQL یا MariaDB باشد و روی همان Scope قفل session advisory دیگری فعال نباشد. Scope نامعتبر یا dialect پشتیبانی‌نشده از قرارداد موجود Operation lock شکست می‌خورد؛ fallback بدون Lock وجود ندارد. اجرای مستقل و هم‌زمان روی دیتابیسی که این Lock را رعایت نمی‌کند، در این قرارداد serialize نمی‌شود.

خطای عادی خواندن یا تحلیل به‌صورت `ExecuteResult.Failure` در نتیجهٔ داخلی فرمان منتقل می‌شود. Fatal `Error`ها از مسیر معمول Failure عبور داده نمی‌شوند و همان Error منتشر می‌شود. هر دو حالت را از Failure احتمالی لایهٔ بیرونی نیز جدا بررسی کنید.

## چه زمانی از `status()` استفاده کنیم؟

از آن برای پاسخ به پرسش‌هایی مانند این‌ها استفاده کنید:

- کدام Migrationهای Configuration فعلی در Metadata اعمال‌شده دیده می‌شوند؟
- کدام Version، Migration یا Tag ذخیره‌شده دیگر در Configuration فعلی وجود ندارد؟
- آیا رابطهٔ Tag، Version و History ناقص یا تکراری است؟
- آیا Version به وضعیت دستی UP یا DOWN رسیده است؟

برای انتخاب عملیات بعدی، Status خام، Checkpoint و Target تازه را از APIهای State/Retry بخوانید؛ از روی Issue به‌تنهایی Retry یا اجرای Migration را تصمیم‌گیری نکنید.

## Source مرتبط

- APIها و قفل گزارش: `data_base/migration/executor/src/main/kotlin/controller/MigrationController.kt` و `data_base/migration/executor/src/main/kotlin/service/status/MigrationStatusService.kt`
- خواندن snapshot: `data_base/migration/executor/src/main/kotlin/service/status/MigrationStatusReader.kt`
- تحلیل Configuration و Metadata: `data_base/migration/executor/src/main/kotlin/service/status/MigrationStatusAnalyzer.kt`
- مدل Report: `data_base/migration/api/src/main/kotlin/model/migration_status/MigrationStatusReport.kt`
- مدل وضعیت‌های تحلیلی: `data_base/migration/api/src/main/kotlin/model/migration_status/MigrationVersionStatus.kt` و `MigrationItemStatus.kt`
- نحوهٔ اجرای DSL و nesting نتیجه: `data_base/migration/src/main/kotlin/facade/DatabaseHandleMigration.kt`
- راهنمای State: [خواندن State](../state/README.md)
