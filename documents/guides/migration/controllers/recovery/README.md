# `recovery()`

`recovery()` آخرین Version ناقص را پیدا می‌کند و تلاش می‌کند ادامهٔ مجاز آن را از مسیرهای Retry موجود انجام دهد. این متد اجرای مستقلی برای Migration نمی‌سازد؛ ابتدا State را می‌خواند و سپس بر اساس Operation به `retryUp()` یا `retryDown()` واگذار می‌کند.

راهنمای مقادیر Status و Phaseها: [وضعیت‌های Migration](../../states/README.md).

## امضا و محل استفاده

درون receiver مربوط به `db.migration { ... }`:

```kotlin
recovery(toCompleteVersion = true)
```

پارامتر `toCompleteVersion` پیش‌فرض `true` است. برای اجرای فقط یک فعالیت Migration و توقف در مرز بعدی، مقدار `false` را بدهید.

## جریان اجرا

1. Orchestration از `MigrationVersionStateService` آخرین Version با Status غیر از `103` را با `LATEST_INCOMPLETE` می‌خواند. خواندن State زیر `SystemLock.MIGRATION` انجام می‌شود.
2. اگر خواندن State شکست بخورد، همان `ExecuteResult.Failure` برمی‌گردد. اگر Version ناقصی وجود نداشته باشد، نتیجه `ExecuteResult.Success(null)` است.
3. `MigrationRecoveryOperation` و Status همراه Checkpoint بررسی می‌شوند. خود Operation به‌تنهایی مجوز اجرا نیست.
4. برای UP، مسیر `retryUp()` و برای DOWN، مسیر `retryDown()` فراخوانی می‌شود. Retry قفل خودش را می‌گیرد، Snapshot را زیر قفل دوباره می‌خواند و Target را اعتبارسنجی می‌کند.
5. پس از تأیید Target تازه، همان Version execution pipeline موجود ادامه می‌یابد؛ مسیر Recovery از Version دیگری عبور نمی‌کند.

میان خواندن اولیه و گرفتن قفل Retry ممکن است دیتابیس تغییر کند. این حالت با Revision، Version/Tag ID، Status، Operation، Checkpoint و Migration ثبت‌شده دوباره بررسی می‌شود؛ Target کهنه یا ناسازگار پیش از اجرای فعالیت رد می‌شود.

## Dispatch فعلی

| Status و Operation | رفتار `recovery()` |
|---|---|
| `100` + `START_EXECUTION` | `retryUp()`؛ Phase معتبر Checkpoint مشخص می‌کند Schema، Recording یا Finalization انجام شود. |
| `101` + `UP_EXECUTION` | `retryUp()`؛ اجرای Migration جاری ممکن است تکرار شود. Diagnostic و Checkpoint باید معتبر و منطبق باشند. |
| `100` یا `102` + `UP_RECORDING_EXECUTION` | `retryUp()`؛ در `RECORD_PENDING` فقط Recording و در `FINALIZATION_PENDING` فقط Finalization اجرا می‌شود. |
| `200` + `START_EXECUTION` | `retryDown()` با همان قواعد Phase. |
| `201` + `DOWN_EXECUTION` | `retryDown()`؛ اجرای Migration جاری ممکن است تکرار شود و Diagnostic/Checkpoint اعتبارسنجی می‌شوند. |
| `200` یا `202` + `DOWN_RECORDING_EXECUTION` | `retryDown()`؛ Recording یا Finalization ادامه می‌یابد، بدون اجرای دوبارهٔ Schema همان Migration. |
| `103` | در انتخاب `LATEST_INCOMPLETE` وارد Recovery نمی‌شود؛ Version تکمیل‌شده نیاز به Recovery ندارد. |
| `110` یا `210`، `UNKNOWN`، یا Operation/Status ناسازگار | `Failure`؛ اجرای خودکار متوقف می‌شود. |

`recovery()` در Source فعلی Statusهای `101` و `201` را هم به Retry متناظر می‌فرستد. این یک انتخاب پرریسک‌تر از ادامهٔ Recording است: Failure اجرای Schema تضمین نمی‌کند که DDL هیچ اثری نداشته است. با اجرای `recovery()` یا `retryUp()`/`retryDown()` برای این Statusها، احتمال اجرای مجدد Schema را بپذیرید و پیش از آن اثر اجرای قبلی را بررسی کنید. این مسیر اعتبارسنجی ساختار Schema انجام نمی‌دهد.

### معنی Phaseها هنگام Recovery

- `EXECUTION_PENDING` و `PAUSED_NEXT`: Cursor به Migrationای اشاره می‌کند که اجرای Schema آن هنوز آغاز نشده است.
- `EXECUTION_IN_FLIGHT`: با Status شروع `100`/`200` قابل ادامهٔ خودکار نیست؛ پایان یا اثر اجرای قبلی نامعلوم است. اگر Failure عادی در Status `101`/`201` ثبت شده باشد، API Retry بعد از اعتبارسنجی Diagnostic اجازهٔ تلاش مجدد می‌دهد.
- `RECORD_PENDING`: اجرای Schema همان Migration موفق بوده؛ فقط Metadata recording/reconciliation مجاز است.
- `FINALIZATION_PENDING`: Cursor ندارد و فقط Finalization Version انجام می‌شود.

Checkpoint خراب یا ناسازگار، Diagnostic نامعتبر، Cursor ثبت‌نشده، Version/Tag اشتباه یا Target قدیمی باعث Failure می‌شود. `110` و `210` همیشه نیازمند تعیین تکلیف دستی هستند و این API آن‌ها را Reset نمی‌کند.

## `toCompleteVersion`

- `true`: از فعالیت فعلی ادامه می‌دهد و تا انتهای همان Version پیش می‌رود.
- `false`: فعالیت فعلی را بازیابی می‌کند و اگر Migration دیگری باقی مانده باشد، در مرز بعدی با Cursor بعدی و Phase `PAUSED_NEXT` متوقف می‌شود. Version همچنان Status شروع `100` یا `200` دارد.
- اگر Target از ابتدا `FINALIZATION_PENDING` باشد، حتی با `false` هم Finalization انجام می‌شود؛ نقطهٔ Migration دیگری برای Pause وجود ندارد.

برای نتیجهٔ خود Retry، `Success(true)` یعنی Version تمام شده و `Success(false)` یعنی Retry با موفقیت در مرز بعدی Pause کرده است. نبود Version ناقص `Success(null)` است؛ این با Pause یکی نیست. Failure عادی به شکل `ExecuteResult.Failure` برمی‌گردد و Fatal `Error` طبق قرارداد Operation منتشر می‌شود.

## نمونه و نتیجهٔ تو در تو

```kotlin
val call: ExecuteResult<ExecuteResult<Boolean>> = db.migration {
    recovery(toCompleteVersion = false)
}

when (call) {
    is ExecuteResult.Failure -> println("Migration block could not run: ${call.exception}")
    is ExecuteResult.Success -> when (val result = call.result) {
        null -> println("Migration block returned no command result")
        is ExecuteResult.Failure -> println("Recovery failed: ${result.exception}")
        is ExecuteResult.Success -> when (result.result) {
            null -> println("No incomplete Version")
            true -> println("Version completed")
            false -> println("Recovery paused at the next Migration boundary")
        }
    }
}
```

`DatabaseHandleApi.migration { ... }` نتیجهٔ Block را در Result بیرونی قرار می‌دهد؛ چون خود `recovery()` نیز `ExecuteResult` برمی‌گرداند، نمونه دو لایهٔ Result دارد. Failure بیرونی مربوط به بازکردن/اجرای Scope یا فراخوانی Block است؛ Failure داخلی نتیجهٔ دستور Migration است.

## حدود مسئولیت

`recovery()` وضعیت را انتخاب و Dispatch می‌کند؛ مالک Lock، Scope، Connection، Failure handling و Cleanup همان Operation/Migration pipeline موجود است. Recovery تضمین نمی‌کند Schema یا Metadata قبلی در دیتابیس واقعی چه اثری گذاشته‌اند. Status را دستی عوض نکنید تا Gate عادی دور زده شود.

**Source مرتبط:** `MigrationFacade.recovery()`، `MigrationController.recovery()`، `MigrationRunOrchestrationService.recovery()` و `dispatchRecovery()`، `MigrationVersionStateService.state()` و `MigrationVersionTransactionService.retryUp()`/`retryDown()`.

