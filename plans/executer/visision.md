عملیات	مسئولیت
Command	قرارداد
migrate()	اجرای تمام Versionهای اجرا‌نشده، به ترتیب تعریف‌شده
migrate(version)	اجرای تمام Versionهای اجرا‌نشده تا نسخهٔ هدف، شامل خود هدف
rollback()	Rollback آخرین Version اجرا‌شده، در صورت پشتیبانی از Rollback
rollback(version)	Rollback تمام Versionهای بعد از نسخهٔ هدف؛ خود هدف باقی می‌ماند
status()	نمایش وضعیت هر Version و Migration نسبت به Catalog و History
check(version)	بررسی اینکه نسخه اجرا شده و وضعیت ثبت‌شدهٔ آن معتبر است
current()	بازگرداندن آخرین Version اجرا‌شده در زنجیرهٔ معتبر
history()	نمایش سوابق اجرای ثبت‌شده، به ترتیب زمانی
pending()	نمایش Migrationهای تعریف‌شده‌ای که هنوز اجرا نشده‌اند
refresh()	بازسازی دیتابیس از طریق Rollback کامل و سپس Migrate مجدد، فقط در صورت پشتیبانی و تأیید صریح

نقشه کامل طراحی سیستم Migration
مرحله ۱: زیرساخت اجرای Migration با Operation
تکمیل شده
هدف این مرحله این بود که اجرای Migration کاملاً تحت مدیریت Operation باشد.

موارد اصلی:
1. Migration از طریق OperationExecutionScope به دیتابیس دسترسی داشته باشد.
2. اجرای SQL از همان Scope و Connection متعلق به Operation انجام شود.
3. Migration مستقیماً Connection نسازد و Lifecycle آن را مدیریت نکند.
4. مدیریت Connection و Scope بر عهده Operation باشد.
5. امکان اجرای SQL و مدیریت Failure بدون وابستگی به APIهای قدیمی حفظ شود.
6. APIهای سازگاری قبلی بدون تغییر ناخواسته باقی بمانند.
   در این مرحله زیرساخت withOperationScope و اتصال مسیر Migration به Operation ایجاد شد.

اصل معماری: Migration مصرف‌کننده Operation است، نه مالک Connection.

مرحله ۲: مدل نتیجه و مدیریت Failure
تکمیل شده
هدف این بود که شکست در لایه‌های مختلف گم نشود.

موارد اصلی:
- تفکیک Success و Failure.
- مشخص بودن مرحله‌ای که Failure در آن رخ داده است.
- حفظ Reference خطای اصلی.
- ثبت خطاهای بعدی به‌صورت Suppressed در صورت لزوم.
- رفتار مشخص برای Error و پرتاب مجدد همان Reference.
- جلوگیری از تبدیل اشتباه Fatal Error به Failure عادی.
  در این مرحله مدل نتیجه و مدیریت Fatal Error توسعه پیدا کرد.

مرحله ۳: تعریف Status برای Version
زیرساخت پیاده‌سازی شده
برای هر Version یک Status داریم تا وضعیت اجرای آن در دیتابیس قابل تشخیص باشد.

یکی از Statusهای اصلی:
const val STATUS_MIGRATE_COMPLETED = 3


قاعده مهمی که اکنون مشخص کردی این است:

هر Status دیگری به‌جز ۳ به معنی وجود وضعیت غیرعادی یا اجرای ناقص در سیستم است.

پس سیستم نباید فقط Version مربوط به درخواست فعلی را بررسی کند.

باید تمام Versionهای ثبت‌شده در History را بررسی کند.

قاعده:
\[
Healthy = \forall v \in Versions,\; v.status = 3
\]

و در صورت وجود حتی یک Version با Status دیگر:
\[
\exists v \in Versions:\; v.status \neq 3
\]

سیستم باید عملیات Migration را متوقف کند.

این موضوع یک قانون سراسری برای اجرای Migration است، نه صرفاً یک کنترل برای Version انتخاب‌شده.

مرحله ۴: خواندن و اعتبارسنجی History
پیاده‌سازی شده
سیستم باید بتواند وضعیت واقعی دیتابیس را از جدول‌های Migration بخواند.

اطلاعات موردنیاز:
- system_migration_tag
- system_migration_version
- system_migration
  جریان فعلی:
1. خواندن Tagها.
2. خواندن Versionها و Status آن‌ها.
3. خواندن Migration History.
4. ساخت Snapshot.
5. اعتبارسنجی روابط بین اطلاعات.
6. استخراج وضعیت Version فعلی.
   این Snapshot باید در ادامه همان اجرای Orchestration استفاده شود تا خواندن غیرضروری History تکرار نشود.

نکته: خواندن سه جدول در سه SELECT ترتیبی انجام می‌شود؛ بنابراین Snapshot فعلی تضمین اتمیک بودن در سطح دیتابیس ندارد.

مرحله ۵: کنترل سلامت سیستم در ابتدای اجرا
قاعده مشخص است؛ انطباق کامل مسیر نیازمند بررسی است
این مرحله با توضیح اخیر تو اهمیت ویژه‌ای پیدا کرده است.

ترتیب مورد انتظار:
1. ورود به مسیر رسمی Migration.
2. گرفتن SystemLock.MIGRATION.
3. خواندن History.
4. بررسی Status تمام Versionهای ثبت‌شده.
5. اگر حتی یک Status غیر از 3 وجود داشت، توقف عملیات.
6. فقط در صورت سالم بودن تمام Versionها، ادامه مراحل.
   در این مرحله:
- هیچ Migration اجرا نمی‌شود.
- هیچ Version جدیدی انتخاب و اجرا نمی‌شود.
- هیچ Marker جدیدی ثبت نمی‌شود.
- هیچ Tag یا History جدیدی نوشته نمی‌شود.
  این بررسی باید پیش از Selection و Preflight اجرایی انجام شود.

البته خواندن History و اعتبارسنجی ساختاری آن طبیعتاً قبل از این تصمیم لازم است.

مرحله ۶: جلوگیری از اجرای هم‌زمان Migrationها
زیرساخت پیاده‌سازی شده؛ تست واقعی باقی است
این بخش دو نوع ترتیب را پوشش می‌دهد.
الف) ترتیب داخل یک اجرای Migration
Migrationها باید دقیقاً طبق ترتیب Registration اجرا شوند.

برای UP:
Version 1
Migration A
Migration B
Migration C

Version 2
Migration D
Migration E


برای DOWN ترتیب Migrationهای هر Version معکوس می‌شود.

Versionها نیز طبق جهت Traversal حرکت می‌کنند.
ب) جلوگیری از اجرای هم‌زمان دو جریان
برای این کار از:
SystemLock.MIGRATION


استفاده می‌کنیم.

قفل باید کل عملیات Orchestration را پوشش دهد:
Acquire Migration Lock
|
v
Read History
|
v
Validate
|
v
Select Versions
|
v
Preflight
|
v
Prepare Migrations
|
v
Execute Versions
|
v
Release Migration Lock


در نتیجه، دو اجرای رسمی Migration روی یک Database Identity نباید هم‌زمان وارد بخش اجرایی شوند.

زیرساخت Lock قبلاً پیاده‌سازی شده است، اما تست هم‌زمانی واقعی MariaDB هنوز لازم است.

مرحله ۷: انتخاب Versionها و ساخت Traversal Plan
تکمیل شده
پس از بررسی سلامت سیستم، باید مشخص شود چه Versionهایی باید اجرا شوند.

فرمان‌های فعلی:
init
current
status
migrate
migrate(version)
rollback
rollback(version)


قواعد:
- migrate() اجرای Versionهای لازم تا آخرین Version ثبت‌شده در Configuration را انجام می‌دهد.
- migrate(version) تا خود Version هدف، به‌صورت inclusive، پیش می‌رود.
- rollback() مسیر برگشت را طی می‌کند.
- rollback(version) تا Version هدف برمی‌گردد و خود Version هدف را نگه می‌دارد.
  Traversal Plan ترتیب دقیق Versionها و Migrationهای هرکدام را مشخص می‌کند.

مرحله ۸: Preflight کامل تمام Versionهای انتخاب‌شده
تکمیل شده
این مرحله برای جلوگیری از اجرای نیمه‌کاره Plan به دلیل خطای قابل تشخیص پیش از اجرا طراحی شد.

هدف این است که قبل از اولین Marker یا اجرای Schema، تمام Versionهای انتخاب‌شده بررسی شوند.

Preflight شامل:
- اعتبارسنجی Versionها.
- بررسی Version Tagها.
- بررسی Migration Tagها.
- بررسی History قبلی.
- تشخیص Migration تکراری.
- بررسی سازگاری Registrationها با History.
- بررسی شرایط UP و DOWN.
  برای UP و DOWN قواعد متفاوتی داریم.

در UP نبودن ITEM Tag مجاز است، اما History قبلی یا تعارض‌های تعریف‌شده باید رد شوند.

در DOWN باید Version و History ثبت‌شده با Migrationهای مورد انتظار تطابق داشته باشند.

محدودیت مهم: Preflight هیچ Write، Marker یا اجرای Migration انجام نمی‌دهد.

مرحله ۹: آماده‌سازی تمام Migrationها پیش از اجرا
تکمیل شده
در این مرحله قبل از ورود به حلقه اجرای Versionها، تمام Instanceهای Migration ساخته و اعتبارسنجی می‌شوند.

چرا؟

فرض کن سه Version داریم و Constructor مربوط به Migration آخر شکست بخورد.

نباید دو Version اول قبلاً اجرا شده باشند و تازه در Version سوم متوجه مشکل شویم.

پس ترتیب:
Preflight تمام Versionها
|
v
Prepare تمام Migrationها
|
v
اگر همه آماده بودند:
|
v
شروع Execution


همچنین همان Instance آماده‌شده در اجرای UP یا DOWN استفاده می‌شود و دوباره ساخته نمی‌شود.

ترتیب Declaration برای UP و ترتیب معکوس برای DOWN حفظ شده است.

مرحله ۱۰: اجرای Version و ثبت وضعیت آن
مسیر اصلی پیاده‌سازی شده
حالا وارد اجرای واقعی می‌شویم.
مسیر UP
Create/Resolve Version Tag
|
v
Insert Version Row
status = 0
|
v
Execute Migration 1
|
v
Register Migration History
|
v
Execute Migration 2
|
v
Register Migration History
|
v
All migrations completed
|
v
Update Version status = 3


Status صفر نقش Marker شروع اجرای UP را دارد.
مسیر DOWN
Version exists
|
v
Update Version status = 4
|
v
Execute migrations in reverse order
|
v
Remove Migration History
|
v
Remove unused Item Tags
|
v
Delete Version Row


در مسیر DOWN، Version بعد از تکمیل Rollback حذف می‌شود.

نکته مهم: در معماری فعلی Migration از Transaction استفاده نمی‌کند. این تصمیم قبلی است و قرار نیست صرفاً برای حل Failure آن را برگردانیم.

مرحله ۱۱: ثبت Failure با Connection جدید
باقی‌مانده برای پیاده‌سازی و ممیزی
این همان سناریویی است که قبلاً درباره‌اش صحبت کرده بودیم.

اگر در هر مرحله از اجرای Version شکست رخ دهد، باید بتوانیم نوع شکست را در Status همان Version ثبت کنیم.

اما Connection اصلی ممکن است به دلیل Failure دیگر قابل استفاده نباشد.

بنابراین مسیر موردنظر:
Migration Failure
|
v
Stop normal execution
|
v
Create independent Operation
|
v
Open new Connection
|
v
Update Version Failure Status
|
v
Close new Connection


این Connection جدید نیز باید تحت مدیریت یک Operation مستقل باشد.

فعلاً درباره نحوه Recovery، Retry یا اصلاح دیتابیس تصمیم نمی‌گیریم.

مرحله ۱۲: سیاست برخورد با سیستم شکست‌خورده
قاعده اولیه مشخص شده؛ ادامه آن عمداً باز است
تنها تصمیم قطعی فعلی:

اگر سیستم در ابتدای اجرای Migration، Versionای با Status غیر از 3 پیدا کند، عملیات را متوقف می‌کند.

هنوز تصمیمی درباره موارد زیر نداریم:
- Retry کردن Migration شکست‌خورده.
- ادامه اجرای Migration از نقطه شکست.
- اصلاح دستی دیتابیس.
- بازگرداندن تغییرات ناقص.
- نحوه پاک‌سازی وضعیت شکست.
- نحوه تأیید سلامت مجدد سیستم.
  این‌ها تصمیم‌های Recovery هستند و فعلاً خارج از محدوده تصمیم‌گیری ما هستند.

مرحله ۱۳: تست‌های Integration واقعی
باقی‌مانده
تست Unit برای اثبات کامل رفتار سیستم کافی نیست.

سناریوهای مهم MariaDB:
سناریو	انتظار
دو اجرای Migration هم‌زمان	فقط یک اجرا وارد بخش محافظت‌شده شود
Lock Timeout	اجرای دوم متوقف شود
Failure هنگام UP	اجرای عادی متوقف شود
ثبت Failure با Connection جدید	Status در صورت موفقیت ثبت شود
شکست Connection اصلی	Connection مستقل بتواند Failure را ثبت کند
وجود Version با Status غیر از ۳	اجرای Migration جدید متوقف شود
شکست ثبت Failure	خطای اصلی و خطای ثبت Failure قابل تشخیص باشند
قطع ناگهانی Connection	وضعیت باقی‌مانده در اجرای بعدی تشخیص داده شود

این تست‌ها باید روی MariaDB واقعی اجرا شوند، نه صرفاً Mock.

مرحله ۱۴: Schema Upgrade و سازگاری دیتابیس‌های قدیمی
باقی‌مانده
یک مسئله جداگانه هم داریم:

CREATE TABLE IF NOT EXISTS ساختار جدول موجود را به‌روزرسانی نمی‌کند.

اگر در آینده ستون جدیدی به جدول Migration اضافه کنیم، دیتابیس قدیمی خودبه‌خود آن را دریافت نمی‌کند.

پس باید مسیر Schema Upgrade مشخص شود و با:
SystemLock.SCHEMA_UPDATE


هماهنگ باشد.

مرحله ۱۵: مستندات و تثبیت قراردادها
باقی‌مانده
در انتها باید قراردادهای سیستم روشن و مستند شوند:
- Statusهای Version و معنی آن‌ها.
- ترتیب اجرای Migrationها.
- قانون سلامت سراسری سیستم.
- نحوه استفاده از SystemLock.MIGRATION.
- رفتار Failure Recording.
- محدودیت‌های Recovery.
- قرارداد Constructorهای Migration.
- تفاوت مسیر رسمی Orchestration با APIهای Low-Level.
- تضمین‌ها و محدودیت‌های Database Identity و Lock.

جمع‌بندی وضعیت کل پروژه
نقشه پیشرفت Migration
زیرساخت Operation و مدل Failure
تکمیل

Status و History
پیاده‌سازی شده

Preflight و Preparation
تکمیل و تست شده

ترتیب اجرای Migration و Lock
پیاده‌سازی شده

بررسی سراسری Status در ابتدای اجرا
نیازمند تطبیق با قرارداد نهایی

Failure Recording با Connection جدید
باقی‌مانده

Recovery Policy
فعلاً بدون تصمیم

MariaDB Integration و Concurrency Tests
باقی‌مانده

Schema Upgrade
باقی‌مانده

مستندات نهایی
باقی‌مانده

---
SELECT GET_LOCK(?, ?)
و:
SELECT RELEASE_LOCK(?) AS released
Operation
├── Query
├── Schema
└── System
├── AcquireLock
└── ReleaseLock
----
``` kotlin
enum class ConnectionDecision {
    REUSE,
    NEW
}

data class StageResult<T>(
    val value: T,
    val nextConnection: ConnectionDecision
)

db.connection {
    // خروجی Stage اول
}
.then<T1> { result, ctx ->
    // ورودی: نوع خروجی Stage اول
    // خروجی: T1
}
.then<T2> { result, ctx ->
    // ورودی: T1
    // خروجی: T2
}
.then<T3> { result, ctx ->
    // ورودی: T2
    // خروجی: T3
}
```

```kotlin

db.connection {
    start { ctx ->
        ctx.next(loadMigration())
            .connection(ConnectionDecision.REUSE)
    }
        .then<ValidationResult> { result, ctx ->
            ctx.next(validate(result))
                .connection(ConnectionDecision.REUSE)
        }
}

db.transaction {
    start { ctx ->
        ctx.next(loadData())
    }
        .then<ValidationResult> { result, ctx ->
            ctx.next(validate(result))
        }
}
```




---

```kotlin
data class MigrationState(
    var migration: Migration? = null,
    var validation: ValidationResult? = null,
    var execution: ExecutionResult? = null,
    var error: Throwable? = null,
    var executedSteps: MutableList<String> = mutableListOf()
)
```
```kotlin
val result: ExecuteResult<MigrationState> =
    db.connection<MigrationState> {

        start<Migration> { ctx ->

            ctx.state.executedSteps.add("LOAD_MIGRATION")

            val migration = loadMigration()

            ctx.state.migration = migration

            ctx.next(migration)
                .connection(ConnectionDecision.REUSE)
        }

        .then<Migration, ValidationResult> { migration, ctx ->

            ctx.state.executedSteps.add("VALIDATE")

            val validation = validate(migration)

            ctx.state.validation = validation

            ctx.next(validation)
                .connection(ConnectionDecision.REUSE)
        }

        .then<ValidationResult, ExecutionResult> { validation, ctx ->

            ctx.state.executedSteps.add("EXECUTE")

            if (!validation.isValid) {
                // بدون next، Pipeline متوقف می‌شود.
                // State فعلی نتیجه Pipeline خواهد بود.
                Unit
            } else {

                val execution = executeMigration(
                    ctx.state.migration!!,
                    validation
                )

                ctx.state.execution = execution

                ctx.next(execution)
                    .connection(ConnectionDecision.REUSE)
            }
        }

        .then<ExecutionResult, Unit> { execution, ctx ->

            ctx.state.executedSteps.add("FINALIZE")

            finalizeMigration(execution)

            // توقف Pipeline بدون next
            Unit
        }
    }
```
```kotlin
db.connection<MigrationState> {

    start<Migration> { ctx ->

        val migration = loadMigration()
        ctx.state.migration = migration

        ctx.next(migration)
            .connection(ConnectionDecision.REUSE)
    }

    .then<Migration, ValidationResult> { migration, ctx ->

        val validation = validate(migration)
        ctx.state.validation = validation

        ctx.next(validation)
            .connection(ConnectionDecision.NEW)
    }

    .then<ValidationResult, ExecutionResult> { validation, ctx ->

        val execution = executeMigration(
            ctx.state.migration!!,
            validation
        )

        ctx.state.execution = execution

        ctx.next(execution)
            .connection(ConnectionDecision.REUSE)
    }
}
```
```kotlin
val result: ExecuteResult<MigrationState> =
    db.executeTransactionDefinition(
        definition = start<MigrationState, Migration> { ctx ->

            val migration = loadMigration()

            ctx.state.migration = migration

            migration
        }
            .then<Migration, ValidationResult> { migration, ctx ->

                val validation = validate(migration)

                ctx.state.validation = validation

                validation
            }
            .then<ValidationResult, ExecutionResult> { validation, ctx ->

                if (!validation.isValid) {
                    // باید از مکانیزم توقف Stage در API واقعی استفاده شود.
                    return@then ctx.stop()
                }

                val execution = executeMigration(
                    ctx.state.migration!!,
                    validation
                )

                ctx.state.execution = execution

                execution
            }
            .then<ExecutionResult, MigrationState> { execution, ctx ->

                finalizeMigration(execution)

                ctx.state
            }
    )
```
```kotlin
when (result) {
    is ExecuteResult.Success -> {
        val state = result.value

        println(state.migration)
        println(state.validation)
        println(state.execution)
        println(state.executedSteps)
    }

    is ExecuteResult.Failure -> {
        println(result.error)
    }
}
```