# بازگرداندن Migration با `rollback()`

`rollback()` مسیر DOWN را برای Versionهای اعمال‌شده اجرا می‌کند. انتخاب Versionها از Configuration و History معتبر انجام می‌شود و اجرای Schema، Recording و حذف Version از pipeline موجود می‌گذرد.

## API و انتخاب Version

دو overload عمومی در `MigrationFacade` وجود دارد:

```kotlin
rollback()
rollback(versionTag)
```

- `rollback()` فقط آخرین Version اعمال‌شده را Rollback می‌کند.
- `rollback(versionTag)` Versionهای اعمال‌شده‌ای را که از Version هدف جدیدترند، به‌ترتیب معکوس Configuration Rollback می‌کند؛ خود Version هدف باقی می‌ماند.
- اگر Version اعمال‌شده‌ای وجود نداشته باشد و هدفی تعیین نشده باشد، عملیات کاری برای انجام ندارد و فرمان عادی با `Success(true)` تمام می‌شود.
- هدف باید در Configuration موجود باشد و با محدودهٔ Versionهای واقعاً اعمال‌شده سازگار باشد؛ هدف نامعتبر، جلوتر از Version فعلی، یا درخواست Rollback وقتی چیزی اعمال نشده است، پیش از اجرای Schema رد می‌شود.

مثلاً اگر ترتیب Configuration برابر `v1`, `v2`, `v3`, `v4` باشد:

| فراخوانی | Versionهای انتخاب‌شده |
|---|---|
| `rollback()` در حالی که `v4` آخرین Version اعمال‌شده است | فقط `v4` |
| `rollback(v2)` در حالی که تا `v4` اعمال شده است | `v4` سپس `v3`; خود `v2` حفظ می‌شود |

نمونه:

```kotlin
val execution: ExecuteResult<ExecuteResult<Boolean>> = database.migration {
    rollback()
}
```

برای بازگرداندن Versionهای جدیدتر از یک مرز مشخص:

```kotlin
val execution: ExecuteResult<ExecuteResult<Boolean>> = database.migration {
    rollback(targetVersionTag)
}
```

همان `MigrationVersionTag` ثبت‌شده برای Version را به overload بدهید. نتیجهٔ `database.migration { ... }` دو لایه است: Result بیرونی مربوط به Operation/اجرای Block و Result داخلی نتیجهٔ `rollback()` است. در Rollback عادی، `Success(true)` یعنی همهٔ Versionهای انتخاب‌شده حذف و کار تکمیل شده‌اند؛ `Success(false)` نتیجهٔ Pause مربوط به Retry است و انتظار معمول این API نیست.

## ترتیب DOWN

جریان اجرای Rollback به این صورت است:

1. Orchestration زیر `SystemLock.MIGRATION` snapshot تاریخچه را می‌خواند و Configuration، ترتیب Versionها، Tagها، Version rowها و History مربوط به Migrationها را اعتبارسنجی می‌کند.
2. Versionهای اعمال‌شدهٔ انتخابی در ترتیب معکوس Configuration پردازش می‌شوند.
3. داخل هر Version، Migrationها نیز به ترتیب معکوس Declaration اجرا می‌شوند.
4. پس از موفقیت Schema هر Migration، Recording مسیر DOWN History آن را حذف می‌کند و Tag مربوط به Migration را فقط در صورت بی‌استفاده بودن حذف می‌کند.
5. پس از تکمیل Migrationهای Version، Finalization ردیف Version را به‌صورت شرطی حذف می‌کند.

موفقیت DOWN با Status تکمیل جدید ثبت نمی‌شود؛ ردیف Version حذف می‌شود. بنابراین پس از Rollback موفق، State آن Version دیگر قابل خواندن نیست. Tagهای VERSION ممکن است برای استفادهٔ دوباره باقی بمانند؛ حذف Version به معنی حذف کل Tagهای مرتبط نیست.

## Statusهای DOWN و Checkpoint

| Status | معنی |
|---:|---|
| `200` | DOWN شروع شده است؛ Phase و Cursor در Checkpoint نگهداری می‌شوند. |
| `201` | اجرای Schema یک Migration در DOWN شکست خورده است. |
| `202` | اجرای Schema موفق بوده، اما Recording یا Finalization شکست خورده است. |
| `210` | تناقض یا وضعیت نیازمند بررسی دستی در مسیر DOWN؛ اجرای عادی مسدود است. |

Status شروع `200` به‌تنهایی مشخص نمی‌کند که باید `down()` اجرا شود: Phase Checkpoint تعیین می‌کند اجرای Schema، Recording یا Finalization مجاز است. Failureهای `201` و `202` نیز با مسیر عادی `rollback()` ادامه داده نمی‌شوند. Checkpoint ناسازگار، Status ناشناخته، Version ناتمام یا Metadata ناسازگار باعث توقف پیش از اجرای عادی می‌شود.

برای خواندن وضعیت و عملیات تشخیصی، از [راهنمای State](../state/README.md)، [راهنمای Recovery](../recovery/README.md) و [راهنمای وضعیت‌های Migration](../../states/README.md) استفاده کنید. `retryDown()`/`recovery()` با `rollback()` یکی نیستند: آن‌ها Target و Checkpoint را جداگانه اعتبارسنجی می‌کنند و برای ادامهٔ Version ناتمام طراحی شده‌اند.

## Lock، Scope و خطاها

Rollback از Operation Scope و Connection مدیریت‌شدهٔ فراخواننده استفاده می‌کند و در محدودهٔ `SystemLock.MIGRATION` انجام می‌شود. برای اجرای عمومی، Scope باید Managed root Connection Scope فعال با `DatabaseIdentity` canonical و dialect پشتیبانی‌شدهٔ MySQL یا MariaDB باشد؛ روی همان Scope نباید قفل session advisory دیگری فعال باشد. قفل و lifecycle جدیدی در Migration ساخته نمی‌شود.

Failure عادی در نتیجهٔ داخلی به‌صورت `ExecuteResult.Failure` برمی‌گردد و جلوی اجرای Versionهای بعدی را می‌گیرد. Fatal `Error` به‌صورت Fatal منتقل می‌شود. اگر failure در میانهٔ یک Version رخ دهد، Version ممکن است با Status و Checkpoint ناتمام باقی بماند؛ از `state(LATEST_INCOMPLETE)` و ابزارهای Recovery برای بررسی همان وضعیت استفاده کنید.

همانند UP، ترتیب Schema و Recording به‌تنهایی تضمین اتمیک بودن یا Rollback شدن تمام DDLها را نمی‌دهد. رفتار DDL تابع دیتابیس و فرمان مشخص است؛ موفقیت هر بخش را از روی Result/Metadata و مستندات وضعیت بررسی کنید.

## Source مرتبط

- امضاهای Facade و Controller: `data_base/migration/src/main/kotlin/facade/MigrationFacade.kt` و `data_base/migration/executor/src/main/kotlin/controller/MigrationController.kt`
- Lock، انتخاب، اعتبارسنجی و traversal DOWN: `data_base/migration/executor/src/main/kotlin/service/run/MigrationRunOrchestrationService.kt`
- انتخاب Rollback از آخرین Version یا تا پیش از Target: `data_base/migration/executor/src/main/kotlin/service/version_selection/MigrationVersionSelectionService.kt`
- اعتبارسنجی History و Status/Checkpoint: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionHistoryValidator.kt` و `MigrationCheckpointStatusValidator.kt`
- اجرای DOWN، Recording و حذف نهایی Version: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionTransactionService.kt`
- نتیجهٔ بیرونی DSL: `data_base/migration/src/main/kotlin/facade/DatabaseHandleMigration.kt`
