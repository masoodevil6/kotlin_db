# Migration

این راهنما مسیر عمومی مصرف Migration را پوشش می‌دهد. مصرف‌کننده به Aggregate ماژول `:data_base:migration` وابسته می‌شود؛ این ماژول `migration:api` و Database Manager را برای API مصرف‌کننده عرضه می‌کند و Executor را به‌عنوان جزئیات پیاده‌سازی نگه می‌دارد.

در یک build چندماژوله می‌توان وابستگی را این‌طور نوشت:

```kotlin
dependencies {
    implementation(project(":data_base:migration"))
}
```

مختصات Artifact منتشرشده در این Repository تعریف نشده است؛ بنابراین مختصات Maven/Plugin Portal را نمی‌توان از Source تأیید کرد. Consumer مستقل مرحلهٔ قبل با وابستگی مستقیم فقط به Aggregate کامپایل شده است. Binary compatibility برای Artifact منتشرشده بررسی نشده است.

مسیر اجرای مصرف‌کننده این است: Migrationها در `MigrationConfiguration` ثبت می‌شوند، Configuration به یک Database Registration متصل می‌شود و دستورات از طریق `DatabaseHandleApi.migration { ... }` اجرا می‌شوند. برای استفادهٔ روزمره لازم نیست `MigrationController` یا Executor را مستقیماً بسازید. کلاس `MigrationFacade` در Source عمومی باقی مانده، اما سازنده‌اش `internal` است؛ پس ادعای مخفی‌بودن کامل FQN آن درست نیست.

## راهنماها

- [تعریف Migration و ثبت Configuration](registration.md)
- [فرمان‌ها، Status، Failure و Lock](execution.md)
- [نمونه‌های AST و Schema](ast-examples.md)
- [نمونهٔ دو Database Registration](../../../example/README.md)
- [نمونه‌های رندر Schema در ماژول `data_base:migration:example`](../../../data_base/migration/example/README.md)

ماژول `data_base:migration:example` نمونه‌های Schema را می‌سازد و SQL را برای Dialect انتخاب‌شده رندر می‌کند؛ به دیتابیس متصل نمی‌شود. نمونهٔ اجرای API عمومی Manager/Migration در پوشهٔ ریشهٔ `example` قرار دارد و اجرای دیتابیس در آن opt-in است.
