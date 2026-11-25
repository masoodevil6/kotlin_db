# خواندن وضعیت Migration

سه API خواندنی، نمای متفاوتی از وضعیت Migration می‌دهند.

## current()

جدیدترین Version اعمال‌شده را از History معتبر برمی‌گرداند؛ اگر Versionای اعمال نشده باشد، Success با مقدار null است:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult

val currentResult = db.migration { current() }.toValue()
when (currentResult) {
    is ExecuteResult.Failure -> println(currentResult.exception)
    is ExecuteResult.Success -> println(currentResult.result?.name ?: "No applied Version")
}
```

## status()

یک `MigrationStatusReport` برای مقایسهٔ Configuration با Versionها، Migrationها، Tagها و History می‌سازد:

```kotlin
val report = db.migration { status() }.toValue().toValue()

report.versions.forEach { version ->
    println("${version.name}: ${version.status}")
    version.migrations.forEach { migration ->
        println("  ${migration.id}: ${migration.status}")
    }
}
report.issues.forEach { issue -> println("${issue.code}: ${issue.message}") }
```

Report برای بررسی است؛ دریافت موفق آن به‌تنهایی مجوز اجرای Migration نیست.

## state(...)

برای خواندن Version ثبت‌شده و ساخت Target بازیابی از Selection صریح استفاده کنید:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection

val stateResult = db.migration {
    state(MigrationVersionSelection.LATEST_INCOMPLETE)
}.toValue()

when (stateResult) {
    is ExecuteResult.Failure -> println(stateResult.exception)
    is ExecuteResult.Success -> {
        val target = stateResult.result
        if (target == null) {
            println("No incomplete Version")
        } else {
            println("versionId=${target.version.versionId}")
            println("tagId=${target.version.tagId}")
            println("status=${target.version.status}")
            println("migrationId=${target.migration?.id?.value}")
            println("operation=${target.operation}")
        }
    }
}
```

`LATEST_INCOMPLETE` یعنی بیشترین Version ID که Status آن `103` نیست. State مقدار خام Status و Message ذخیره‌شده را حفظ می‌کند؛ unknown status را به مقدار شناخته‌شده تبدیل نمی‌کند. `state(versionId)` برای خواندن یک Version مشخص است. Boolean overload قدیمی Deprecated شده است.

## قفل خواندن

`current()`، `status()` و overloadهای `state(...)` هرکدام برای خواندن خود از `SystemLock.MIGRATION` استفاده می‌کنند. این APIها به Scope مدیریت‌شدهٔ root، Database Identity کانونی، dialect پشتیبانی‌شدهٔ MySQL/MariaDB و Scope فاقد advisory lock فعال نیاز دارند. از فراخوانی آن‌ها درحالی‌که همان Scope قفل Migration را گرفته است خودداری کنید؛ Lock قابل re-enter نیست.

برای تفسیر عدد Status و Checkpoint به [راهنمای Stateها](../states/README.md) مراجعه کنید.
