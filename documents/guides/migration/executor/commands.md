# init، migrate و rollback

## راه‌اندازی جداول سیستم

`init()` جداول داخلی Migration را آماده می‌کند و Migrationهای کاربر را اجرا نمی‌کند. آن را پیش از خواندن History یا اجرای Migrationها فراخوانی کنید:

```kotlin
val initialized: Boolean = db.migration { init() }.toValue().toValue()
check(initialized)
```

## اجرای UP

`migrate()` تمام Versionهای pending را به ترتیب Configuration اجرا می‌کند:

```kotlin
val completed: Boolean = db.migration { migrate() }.toValue().toValue()
```

برای اجرا تا یک Version مشخص، Tag آن را بدهید. خود Version مقصد نیز اجرا می‌شود:

```kotlin
val completedThroughTarget = db.migration {
    migrate(AppMigrationTags.Versions.V2)
}.toValue().toValue()
```

## اجرای DOWN

`rollback()` جدیدترین Version اعمال‌شده را برمی‌گرداند. Migrationهای همان Version به ترتیب معکوس ثبت اجرا می‌شوند:

```kotlin
val rolledBack = db.migration { rollback() }.toValue().toValue()
```

`rollback(target)` فقط Versionهای جدیدتر از Target را Rollback می‌کند؛ Target باقی می‌ماند:

```kotlin
val rolledBackToTarget = db.migration {
    rollback(AppMigrationTags.Versions.V1)
}.toValue().toValue()
```

برای بازگرداندن خود آخرین Version، `rollback()` را فراخوانی کنید.

## ترتیب و Failure

ترتیب Versionها از ترتیب `migrationVersion(...)` در Configuration می‌آید، نه از مرتب‌سازی رشته یا شمارهٔ داخل نام. UP به جلو و DOWN به عقب حرکت می‌کند. هر Version و Migration در مسیر عادی با اعتبارسنجی History اجرا می‌شود؛ Version ناقص یا ناسازگار باعث Failure می‌شود و مسیر عادی آن را نادیده نمی‌گیرد.

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult

when (val result = db.migration { migrate() }.toValue()) {
    is ExecuteResult.Success -> println("Completed: ${result.result}")
    is ExecuteResult.Failure -> println("Migration failed: ${result.exception}")
}
```

`ExecuteResult.Failure.exception` همان خطای فرمان است. Fatal `Error` در مسیر Operation به‌عنوان Fatal منتشر می‌شود و نباید به Failure عادی تبدیل شود.
