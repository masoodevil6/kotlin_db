# وضعیت‌های پایدار Migration

این راهنما مقادیر عددی ذخیره‌شده در `system_migration_version.status` و معنای آن‌ها در Source فعلی را توضیح می‌دهد. این عدد به‌تنهایی شرح کامل وضعیت اجرا نیست: برای Versionهای ناتمام، `retry_checkpoint` نیز جهت، Revision، Migration جاری و مرحلهٔ پیشرفت را نگه می‌دارد.

## جدول Statusها

| مقدار | ثابت | مفهوم و زمان ثبت | رفتار فعلی |
|---:|---|---|---|
| `100` | `STATUS_MIGRATE_STARTED` | یک Version برای مسیر UP درج شده یا اجرای UP آن هنوز نهایی نشده است. | Status شروع است؛ Checkpoint تعیین می‌کند اجرای بعدی باید از کدام Migration و کدام مرحله ادامه یابد. `migrate()` و `rollback()` خودکار از Version ناتمام عبور نمی‌کنند. |
| `101` | `STATUS_MIGRATE_FAILED_UP` | اجرای Schema یک Migration در مسیر UP با Failure عادی شکست خورده است و ثبت شرطی Failure موفق شده است. نتیجهٔ واقعی DDL ممکن است نیازمند بررسی باشد. | `retryUp()` این Status را فقط پس از اعتبارسنجی Target، Checkpoint و Diagnostic می‌پذیرد؛ اجرای Migration جاری ممکن است دوباره انجام شود. `recovery()` نیز در Source فعلی این Target را به همان مسیر `retryUp()` می‌فرستد. پیش از فراخوانی، اثر احتمالی اجرای قبلی Schema را در نظر بگیرید. |
| `102` | `STATUS_MIGRATE_FAILED_RECORD` | اجرای UP موفق شده، اما ثبت Metadata، پیشروی Checkpoint یا نهایی‌سازی Version شکست خورده است. | Retry باید با Checkpoint معتبر در `RECORD_PENDING` یا `FINALIZATION_PENDING` ادامه یابد. در این مسیر Schema همان Migration دوباره اجرا نمی‌شود؛ فقط Recording یا نهایی‌سازی انجام می‌شود. |
| `103` | `STATUS_MIGRATE_COMPLETED` | مسیر UP و نهایی‌سازی Version کامل شده‌اند. | تنها Status تکمیل‌شدهٔ Version است. Checkpoint باید `NULL` باشد. شروع DOWN از همین Version انجام می‌شود و Status را به `200` می‌برد. |
| `110` | `STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED` | مسیر UP به تناقض Reconciliation یا نتیجه‌ای رسیده که به‌طور امن در دستهٔ اجرای عادی یا Recording قابل ادامه نیست. | Retry خودکار مجاز نیست. `recovery()` و مسیرهای عادی اجرای Migration آن را اجرا نمی‌کنند؛ وضعیت را بررسی کنید و از ادامهٔ خودکار بپرهیزید. |
| `200` | `STATUS_ROLLBACK_STARTED` | یک Version تکمیل‌شده وارد مسیر DOWN شده و تغییر شرطی شروع DOWN موفق بوده است. | مانند `100`، Checkpoint مرحلهٔ دقیق را مشخص می‌کند. مقدار `200` به‌تنهایی به معنی شروع مجدد `down()` نیست؛ Phase باید بررسی شود. |
| `201` | `STATUS_ROLLBACK_FAILED_DOWN` | اجرای Schema یک Migration در مسیر DOWN با Failure عادی شکست خورده و ثبت شرطی Failure انجام شده است. | `retryDown()` پس از اعتبارسنجی Target، Checkpoint و Diagnostic می‌تواند اجرای Migration جاری را دوباره انجام دهد. `recovery()` فعلی نیز آن را به همین مسیر می‌فرستد. اثر احتمالی اجرای قبلی `down()` را پیش از Retry بررسی کنید. |
| `202` | `STATUS_ROLLBACK_FAILED_RECORD` | اجرای DOWN موفق شده، اما ثبت یا حذف Metadata، پیشروی Checkpoint یا حذف نهایی Version شکست خورده است. | Retry با Checkpoint معتبر در `RECORD_PENDING` یا `FINALIZATION_PENDING` فقط Recording یا نهایی‌سازی را ادامه می‌دهد؛ Schema همان Migration دوباره اجرا نمی‌شود. |
| `210` | `STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED` | مسیر DOWN به تناقض Reconciliation یا نتیجه‌ای رسیده که ادامهٔ خودکار آن امن نیست. | Retry خودکار مجاز نیست. برای تعیین تکلیف به بررسی دستی نیاز است. |

مقادیر `120`، `203` یا هر عدد دیگری که در جدول بالا نیست، در قرارداد فعلی ثابت تعریف‌شده‌ای ندارند. State API مقدار خام دیتابیس را حفظ می‌کند؛ چنین مقداری به‌عنوان Status شناخته‌شده تفسیر نمی‌شود و اعتبارسنجی/اجرای خودکار باید متوقف شود. مقدار `120`، برخلاف `210`، Status دستی DOWN در Source فعلی نیست.

## Status به‌تنهایی کافی نیست

برای Statusهای شروع‌شده و خطای Recording، Checkpoint بخشی از قرارداد پایدار است. Checkpoint شامل `formatVersion`، `revision`، جهت اولیه (`originalStatus`)، Diagnostic اولیه، `cursorMigrationId` و Phase است. Phaseهای فعلی عبارت‌اند از:

| Phase | معنی عملی |
|---|---|
| `EXECUTION_PENDING` | Migration مشخص‌شده در Cursor هنوز برای اجرای Schema در صف است. |
| `EXECUTION_IN_FLIGHT` | اجرای Schema آغاز شده یا نتیجه‌اش ممکن است نامشخص باشد. ترکیب این Phase با Status شروع `100`/`200` به‌طور خودکار قابل ادامه نیست؛ اما شکست ثبت‌شده در `101`/`201` از مسیر Retry صریح و با اعتبارسنجی Diagnostic بررسی می‌شود. |
| `RECORD_PENDING` | اجرای Schema برای Cursor با موفقیت برگشته است؛ ادامه باید در Recording بماند و Schema تکرار نشود. |
| `PAUSED_NEXT` | اجرای قبلی عمداً در مرز بین Migrationها متوقف شده است و Cursor به Migration بعدی اشاره دارد. |
| `FINALIZATION_PENDING` | اجرای Migrationهای Version تمام شده و فقط نهایی‌سازی Version باقی است؛ Cursor برابر `null` است. |

اعتبارسنجی مشترک Status/Checkpoint در `MigrationCheckpointStatusValidator` انجام می‌شود. Checkpoint خراب، ناسازگار با جهت Status، یا ناسازگار با Phase باعث ادامهٔ خودکار نمی‌شود. برای Statusهای `102` و `202`، وجود Diagnostic اولیه و Phase مجاز الزامی است.

## انتقال‌های اصلی

- **UP:** درج Version با `100` و Checkpoint آغاز می‌شود. پیشرفت معمولاً با CAS روی Checkpoint انجام می‌شود. Failure اجرای Schema می‌تواند به `101`، Failure مربوط به Recording به `102` و تناقض یا وضعیت نامطمئن به `110` منتهی شود. پس از تکمیل، Status به `103` تغییر می‌کند و Checkpoint پاک می‌شود.
- **DOWN:** شروع DOWN فقط از Version تکمیل‌شده با انتقال شرطی `103 → 200` انجام می‌شود. Failure اجرای Schema می‌تواند به `201`، Failure مربوط به Recording یا نهایی‌سازی به `202` و تناقض یا وضعیت نامطمئن به `210` منتهی شود. پس از موفقیت DOWN، ردیف Version حذف می‌شود؛ Status موفق DOWN جداگانه‌ای وجود ندارد.
- Failure Statusها با Capability محدود و Update شرطی ثبت می‌شوند. مقدار جدول، نتیجهٔ تضمین‌شدهٔ اجرای SQL نیست: در صورت CAS ناموفق یا نتیجهٔ مبهم، ممکن است Status قبلی باقی بماند یا نتیجهٔ پایدار قابل تعیین نباشد. سیستم برای این حالت Retry یا Fallback نوشتن Status انجام نمی‌دهد.

## مرز اعتماد Capability ثبت Failure

Operation یک Capability محدود و callback-scoped در اختیار مسیر مورداعتماد Migration می‌گذارد. پیش از استفاده، Failure عادی باید در Operation ثبت شده باشد؛ Capability به Scope فعال، Thread مالک، Lease فعال `SystemLock.MIGRATION` و نبود Child Scope فعال محدود است. APIهای وضعیت‌محور و Checkpoint-aware از یک حالت مصرف یک‌بارمصرف مشترک استفاده می‌کنند.

SPI فقط Commandهای بسته و مشخص را می‌پذیرد؛ SQL، DML/DDL دلخواه یا callback عمومی پس از Failure نمی‌گیرد. مسیر Checkpoint یک UPDATE شرطی ثابت برای Status، Diagnostic و Checkpoint اجرا می‌کند و Operation محتوای JSON مربوط به Checkpoint را تفسیر نمی‌کند. Failure عادی نوشتن Status به Failure اصلی ضمیمه می‌شود؛ Fatal `Error` با همان اولویت Fatal منتشر می‌شود.

این Capability یک API عمومی محدود برای مصرف‌کنندگان داخلی مورداعتماد است، نه مرز امنیتی در برابر کد برنامه یا افزونهٔ غیرقابل‌اعتماد. Operation هویت فراخواننده یا منشأ `versionId` را احراز نمی‌کند؛ Migration مسئول اعتبارسنجی هویت Version، تشخیص مرحلهٔ Failure و انتخاب Command مجاز است. در Kotlin، `internal` به محدودهٔ Module اعمال می‌شود، نه Package؛ جدایی Moduleهای SPI و Executor به‌تنهایی دسترسی Migration-only ایجاد نمی‌کند.

تصمیم فعلی حفظ همین قرارداد محدود است: Execution Token، چارچوب Authorization عمومی یا قابلیت دلخواه اجرای SQL پس از Failure اضافه نشده است. اگر مدل تهدید به کد مصرف‌کننده یا افزونهٔ غیرقابل‌اعتماد دارای دسترسی به Scope تغییر کند، این تصمیم باید دوباره بررسی شود.

تفاوت مهم این است که `101`/`201` مربوط به Failure در اجرای Schema است، اما `102`/`202` مربوط به Failure پس از موفقیت اجرای Schema و در مسیر Metadata یا نهایی‌سازی است. به همین دلیل Phase Checkpoint تعیین می‌کند آیا Retry باید Schema را اجرا کند یا فقط Recording را ادامه دهد.

## خواندن وضعیت از API

برای دریافت Target آخرین Version ناقص، از Selection صریح استفاده کنید:

```kotlin
val result = db.migration {
    state(MigrationVersionSelection.LATEST_INCOMPLETE)
}

when (result) {
    is ExecuteResult.Success -> {
        val target = result.result
        if (target == null) {
            println("Version ناقصی وجود ندارد")
        } else {
            println("versionId=${target.version.versionId}")
            println("status=${target.version.status}")
            println("operation=${target.operation}")
            println("migrationId=${target.migration?.id?.value}")
            println("message=${target.version.message}")
        }
    }
    is ExecuteResult.Failure -> println(result.exception)
}
```

`state(MigrationVersionSelection.LATEST_INCOMPLETE)` بزرگ‌ترین Version ID را که Status آن `103` نیست انتخاب می‌کند؛ بنابراین Status ناشناخته و Statusهای دستی نیز می‌توانند Target ناقص باشند. `MigrationRecoveryOperation` یک طبقه‌بندی کمکی است و به‌تنهایی مجوز Retry نیست. Retry دوباره زیر `SystemLock.MIGRATION` وضعیت واقعی دیتابیس، Version ID، Tag، Revision، Checkpoint و Migration ثبت‌شده را اعتبارسنجی می‌کند.

همچنین overload سازگاری `state(lastIncompleteVersion: Boolean)` وجود دارد: مقدار پیش‌فرض `false` آخرین Version را می‌خواند و `true` آخرین Version با Status غیر از `103` را. این overload Deprecated است؛ برای Target دارای Operation از Selection بالا استفاده کنید. هر دو State خواندنی هستند و Migration یا Metadata را تغییر نمی‌دهند.

برای اجرای مسیر Recovery از API موجود استفاده می‌شود:

```kotlin
val result = db.migration {
    recovery(toCompleteVersion = true)
}
```

`recovery()` آخرین Version ناقص را می‌خواند و بر اساس Operation به `retryUp()` یا `retryDown()` موجود واگذار می‌کند. در Source فعلی Statusهای `101` و `201` نیز به Retry متناظر هدایت می‌شوند؛ این کار می‌تواند اجرای Schema همان Migration را تکرار کند. Statusهای `110` و `210`، Operation ناشناخته، Checkpoint نامعتبر و Target ناسازگار با Failure متوقف می‌شوند. اگر Target ناقصی نباشد، Recovery نتیجهٔ موفق بدون Target برمی‌گرداند.

`MigrationVersionState.message` مقدار ذخیره‌شده را بدون تبدیل برمی‌گرداند (ممکن است `null` باشد). پیام تشخیصی جدید معمولاً JSON نسخه‌دار است؛ آن را متن تشخیصی بدانید، نه مرجع مستقل برای مجاز کردن اجرا. هویت و Phase از Checkpoint معتبر و تطبیق آن با State تازه تأیید می‌شوند.

## تفاوت State و Status Report

- `state(...)` یک Version ذخیره‌شده را به‌همراه Status خام عددی، `versionId`، `tagId` و `message` می‌دهد.
- `status()` یک Report تحلیلی از تطبیق Configuration با Versionها، Migrationها، Tagها و History تولید می‌کند؛ مقدارهای آن `MigrationVersionStatus` مانند `APPLIED`، `PENDING`، `INVALID`، `UP_MANUAL_RECOVERY_REQUIRED` و `DOWN_MANUAL_RECOVERY_REQUIRED` هستند، نه اعداد ستون `status`.
- برای `110` و `210`، Analyzer Issueهای جداگانهٔ `MANUAL_RECOVERY_REQUIRED_UP` و `MANUAL_RECOVERY_REQUIRED_DOWN` گزارش می‌کند. Statusهای ناقص یا ناسازگار دیگر نیز در Report به‌عنوان History ناقص/نامعتبر گزارش می‌شوند.
- دریافت موفق Report به معنی امن یا مجاز بودن اجرای Migration نیست.

## رفتار مسیرهای عادی

`migrate()` و `rollback()` History را پیش از اجرای Migration بررسی می‌کنند. Versionهای تکمیل‌نشده، Statusهای ناشناخته، Checkpointهای خراب و وضعیت‌های دستی از اجرای عادی عبور نمی‌کنند. برای Version ناتمام، State را بررسی کنید و فقط از مسیر Recovery/Retry پشتیبانی‌شده استفاده کنید؛ Status را برای عبور از این Gate دستی تغییر ندهید.

## محل‌های مرتبط در Source

- ثابت‌ها و مدل State: `data_base/migration/api/src/main/kotlin/model/migration_state/MigrationVersionState.kt`
- انتخاب Version و Operation بازیابی: `data_base/migration/api/src/main/kotlin/model/migration_state/MigrationVersionSelection.kt` و `MigrationRecoveryOperation.kt`
- تفسیر مشترک Status/Checkpoint: `data_base/migration/executor/src/main/kotlin/service/version/MigrationCheckpointStatusValidator.kt`
- خواندن و ساخت Target: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionStateService.kt`
- اعتبارسنجی Retry و Target تازه: `data_base/migration/executor/src/main/kotlin/service/version/MigrationRetryTargetValidator.kt` و `MigrationRetryDiagnosticValidator.kt`
- شروع، CAS و نهایی‌سازی Version: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionCheckpointPersistence.kt`
- ثبت Failureهای Execution، Recording و Reconciliation: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionTransactionService.kt`
- Gate اجرای عادی و Recovery dispatch: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionHistoryValidator.kt` و `data_base/migration/executor/src/main/kotlin/service/run/MigrationRunOrchestrationService.kt`
- طبقه‌بندی Report: `data_base/migration/executor/src/main/kotlin/service/status/MigrationStatusAnalyzer.kt` و `data_base/migration/api/src/main/kotlin/model/migration_status/MigrationStatusReport.kt`

