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


# Migration System
## Version-Based Migration Architecture

**Document Type:** Architecture Vision & Development Specification  
**Project:** Kotlin Database Library  
**Status:** Proposed Architecture  
**Scope:** Migration Registration, Versioning, Execution, History Tracking, and Rollback

---

# 1. Overview

هدف این طراحی، ایجاد یک سیستم Migration مرحله‌ای است که در آن توسعه‌دهنده کنترل مشخصی روی نحوه انتشار تغییرات Schema داشته باشد.

در این معماری، به‌جای اینکه سیستم به‌صورت خودکار برای Migrationها Batch تولید کند، توسعه‌دهنده با استفاده از مفهوم `MigrationVersion` مشخص می‌کند کدام Migrationها باید به‌عنوان یک مرحله مستقل اجرا شوند.

هر Version یک واحد اجرایی و اتمیک محسوب می‌شود.

تمام Migrationهای متعلق به یک Version در یک Transaction مشترک اجرا خواهند شد.

اگر تمام Migrationهای یک Version با موفقیت اجرا شوند، Transaction آن Commit می‌شود و سیستم به Version بعدی می‌رود.

اگر هر Migration در یک Version شکست بخورد:

- Transaction همان Version Rollback می‌شود.
- تغییرات و سوابق ایجادشده در همان مرحله بازگردانده می‌شوند.
- Versionهای قبلی که با موفقیت Commit شده‌اند باقی می‌مانند.
- فرایند Migration متوقف می‌شود.

این ساختار به توسعه‌دهنده اجازه می‌دهد تغییرات Schema را به مراحل مشخص تقسیم کند، هر مرحله را مستقل بررسی کند و در صورت بروز مشکل، از ادامه اجرای مراحل جلوگیری کند.

---

# 2. Design Goals

اهداف اصلی معماری:

1. حذف تولید خودکار Batch توسط سیستم.
2. واگذاری مرحله‌بندی Migrationها به توسعه‌دهنده.
3. تعریف یک واحد اجرایی مستقل به نام `MigrationVersion`.
4. اجرای هر Version در یک Transaction مستقل.
5. حفظ اتمیک بودن تمام Migrationهای متعلق به یک Version.
6. توقف فرایند در صورت شکست هر Version.
7. امکان Rollback مرحله‌ای و قابل پیش‌بینی.
8. نگهداری تاریخچه Versionها و Migrationها در دیتابیس.
9. استفاده مجدد از QueryExecutor و SchemaExecutor موجود.
10. حفظ مرزبندی مسئولیت‌ها در معماری فعلی کتابخانه.

## Non-Goals

موارد زیر هدف این مرحله از طراحی نیستند:

- اجرای موازی Migrationها.
- اجرای مستقل هر Migration در Transaction جداگانه.
- ایجاد Transaction مستقل برای هر MigrationGroup.
- تولید خودکار Batch.
- اضافه‌کردن وضعیت‌های `PENDING` یا `RUNNING` به تاریخچه.
- ایجاد مکانیزم توزیع‌شده برای قفل‌گذاری Migration.
- تغییر معماری Operation.
- ساخت یک سیستم مستقل برای مدیریت نسخه نرم‌افزار.

---

# 3. Core Concepts

معماری بر اساس سه مفهوم اصلی ساخته می‌شود.

## 3.1 Migration

یک Migration نماینده یک تغییر مشخص در Schema دیتابیس است.

هر Migration دو عملیات تعریف می‌کند:

- `up()`: اعمال تغییر.
- `down()`: بازگرداندن تغییر.

نمونه:

```kotlin
@MigrationId("create_customers")
class CreateCustomersMigration : Migration {

    override fun up() = migration {
        operation(
            Schema.createTable {
                table {
                    tableName("customers")
                }

                addColumn {
                    name("id")
                    dataType(SchemaDataType.IntType)
                    primaryKey()
                    autoIncrement()
                }
            }
        )
    }

    override fun down() = migration {
        operation(
            Schema.dropTable {
                tableName("customers")
            }
        )
    }
}
```

هر Migration یک `MigrationDefinition` شامل دقیقاً یک `SchemaOperation` تولید می‌کند.

Migration خودش مسئول اجرای SchemaOperation نیست.

اجرای عملیات از طریق SchemaExecutor انجام می‌شود.

## 3.2 MigrationGroup

MigrationGroup برای سازمان‌دهی چند Migration تحت یک نام مشترک استفاده می‌شود.

نمونه:

```kotlin
migrationGroup("customers") {
    migration<CreateCustomersMigration>()
    migration<AddCustomerEmailMigration>()
}
```

مسئولیت Group صرفاً سازمان‌دهی Registrationها است.

MigrationGroup:

- واحد Transaction نیست.
- واحد Version نیست.
- Batch تولید نمی‌کند.
- ترتیب اجرای مستقل از Version ندارد.
- مرز Rollback ایجاد نمی‌کند.

تمام Migrationهای یک Group در Transaction مربوط به Version والد اجرا می‌شوند.

## 3.3 MigrationVersion

MigrationVersion مهم‌ترین مفهوم جدید این معماری است.

هر Version مجموعه‌ای از Migrationها و Groupهای ثبت‌شده است که باید به‌عنوان یک مرحله مستقل اجرا شود.

نمونه:

```kotlin
migrationVersion("V1") {
    migration<CreateCustomersMigration>()

    migrationGroup("customers") {
        migration<AddCustomerEmailMigration>()
    }
}
```

ویژگی‌های MigrationVersion:

- شناسه مشخص و یکتا دارد.
- شامل یک یا چند Registration است.
- ترتیب Migrationهای داخل آن حفظ می‌شود.
- یک Transaction مستقل برای اجرای آن ایجاد می‌شود.
- در صورت موفقیت، در تاریخچه دیتابیس ثبت می‌شود.
- در صورت شکست، تمام تغییرات همان Version Rollback می‌شوند.

**MigrationVersion واحد اجرای اتمیک Migration System است.**

---

# 4. Public API Design

API عمومی باید ساده، خوانا و قابل پیش‌بینی باشد.

ساختار پیشنهادی:

```kotlin
migrationConfig {

    migrationVersion("V1") {

        migration<CreateCustomersMigration>()

        migration<AddCustomerEmailMigration>()

        migrationGroup("orders") {
            migration<CreateOrdersMigration>()
            migration<AddOrderItemsMigration>()
        }
    }

    migrationVersion("V2") {

        migration<AddCustomerPhoneMigration>()

        migrationGroup("products") {
            migration<CreateProductsMigration>()
            migration<AddProductPriceMigration>()
        }
    }

    migrationVersion("V3") {
        migration<AddOrderStatusMigration>()
    }
}
```

## 4.1 API Structure

ساختار مفهومی Builderها:

```text
MigrationConfigurationBuilder
    |
    +-- migrationVersion(name)
            |
            +-- MigrationVersionBuilder
                    |
                    +-- migration<T>()
                    |
                    +-- migrationGroup(name)
                            |
                            +-- MigrationGroupBuilder
                                    |
                                    +-- migration<T>()
```

## 4.2 Registration Rules

قوانین Registration:

1. نام Version نباید خالی باشد.
2. نام Version باید در کل Configuration یکتا باشد.
3. شناسه Migration باید در کل Configuration یکتا باشد.
4. یک Migration نباید در چند Version ثبت شود.
5. ترتیب ثبت Versionها باید حفظ شود.
6. ترتیب ثبت Migrationها در هر Version باید حفظ شود.
7. ترتیب Registrationها، شامل Migration مستقل و Group، باید حفظ شود.
8. Configuration پس از Build باید Snapshot مستقل و غیرقابل تغییر از Registrationها داشته باشد.
9. Version خالی نباید بدون تصمیم صریح طراحی وارد چرخه اجرا شود؛ رفتار آن باید در Validation مشخص شود.

## 4.3 Version Identity

شناسه Version یک String است.

نمونه:

```text
V1
V2
V3
2026_01
initial_schema
```

سیستم نباید برای تعیین ترتیب Versionها از مقایسه متنی نام آن‌ها استفاده کند.

ترتیب اجرای Versionها بر اساس ترتیب ثبت آن‌ها در Configuration است.

بنابراین:

```kotlin
migrationConfig {
    migrationVersion("V1") { /* ... */ }
    migrationVersion("V2") { /* ... */ }
    migrationVersion("V10") { /* ... */ }
}
```

ترتیب اجرا دقیقاً V1، سپس V2 و سپس V10 خواهد بود.

---

# 5. Database History Model

در معماری فعلی، تاریخچه Migration در یک جدول ذخیره می‌شود:

```text
system_migration
```

این جدول دارای ستون `batch` است.

در معماری جدید، Batch حذف می‌شود و Version به‌صورت یک موجودیت مستقل ذخیره خواهد شد.

## 5.1 New Table: system_migration_version

جدول جدید:

```text
system_migration_version
```

ساختار پیشنهادی:

| Column | Description |
|---|---|
| id | شناسه داخلی و Auto Increment |
| version | شناسه Version |

قیدها:

- `id` کلید اصلی است.
- `version` باید Unique باشد.
- `version` نباید Null باشد.

## 5.2 Updated Table: system_migration

ساختار جدید:

| Column | Description |
|---|---|
| id | شناسه داخلی و Auto Increment |
| migration | شناسه Migration |
| version_id | ارجاع به Version |

قیدها:

- `id` کلید اصلی است.
- `migration` باید Unique باشد.
- `version_id` نباید Null باشد.
- `version_id` به `system_migration_version.id` ارجاع می‌دهد.

رابطه:

```text
system_migration_version
          |
          | 1
          |
          | N
          |
system_migration
```

هر Version می‌تواند چند Migration داشته باشد.

هر Migration دقیقاً به یک Version تعلق دارد.

## 5.3 History Example

فرض کنیم Configuration شامل سه Version باشد:

```text
V1:
    create_customers
    add_customer_email

V2:
    create_products
    add_product_price

V3:
    create_orders
```

پس از اجرای موفق V1 و V2، تاریخچه می‌تواند به شکل زیر باشد:

**system_migration_version**

| id | version |
|---:|---|
| 1 | V1 |
| 2 | V2 |

**system_migration**

| id | migration | version_id |
|---:|---|---:|
| 1 | create_customers | 1 |
| 2 | add_customer_email | 1 |
| 3 | create_products | 2 |
| 4 | add_product_price | 2 |

در این وضعیت V3 هنوز اجرا نشده است.

هیچ Batch خودکاری وجود ندارد.

`version_id` مشخص می‌کند هر Migration متعلق به کدام مرحله است.

---

# 6. Forward Migration Workflow

Forward Migration باید بر اساس Versionها اجرا شود.

هر Version یک Transaction مستقل دارد.

## 6.1 Execution Algorithm

فرض کنیم Configuration شامل V1، V2 و V3 باشد.

سیستم ابتدا تاریخچه دیتابیس را می‌خواند و وضعیت Versionهای اجراشده را با Configuration مقایسه می‌کند.

سپس Versionهای اجرا‌نشده را به ترتیب Configuration اجرا می‌کند.

برای هر Version:

1. شروع Transaction جدید.
2. ایجاد رکورد Version در `system_migration_version`.
3. اجرای Migrationهای متعلق به Version به ترتیب ثبت.
4. پس از موفقیت هر Migration، ثبت سابقه آن در `system_migration`.
5. پس از موفقیت تمام Migrationهای Version، Commit.
6. شروع Transaction مستقل برای Version بعدی.

ثبت رکورد Version در ابتدای Transaction ضروری است؛ چون Migrationها برای ثبت `version_id` به شناسه آن نیاز دارند.

با این حال، رکورد Version تا زمان Commit در همان Transaction باقی می‌ماند و در صورت شکست، همراه سایر تغییرات Rollback می‌شود.

## 6.2 Execution Example

```text
Forward Migration
       |
       v
    V1 Begin
       |
       +--> Migration A
       |
       +--> Migration B
       |
       +--> Commit V1
       |
       v
    V2 Begin
       |
       +--> Migration C
       |
       +--> Migration D
       |
       +--> Commit V2
       |
       v
    V3 Begin
       |
       +--> Migration E
       |
       +--> Migration F
       |
       +--> Failure
       |
       +--> Rollback V3
       |
       v
    STOP
```

نتیجه:

- V1 باقی می‌ماند.
- V2 باقی می‌ماند.
- V3 در تاریخچه ثبت‌شده باقی نمی‌ماند.
- هیچ Version بعدی اجرا نمی‌شود.

## 6.3 Failure Handling

اگر هر مرحله شکست بخورد:

- اجرای Migrationهای بعدی همان Version متوقف می‌شود.
- Transaction جاری Rollback می‌شود.
- ثبت Version و Migrationهای آن مرحله نیز Rollback می‌شوند.
- خطای اصلی به مصرف‌کننده گزارش می‌شود.
- اجرای Versionهای بعدی انجام نمی‌شود.

سیستم نباید پس از شکست یک Version به‌صورت خودکار از آن عبور کند.

---

# 7. Rollback Workflow

Rollback نیز باید بر اساس Version انجام شود.

واحد Rollback یک Version کامل است، نه یک Migration مستقل.

## 7.1 Rollback Order

اگر ترتیب Forward به این صورت باشد:

```text
V1 -> V2 -> V3
```

ترتیب Rollback باید معکوس باشد:

```text
V3 -> V2 -> V1
```

داخل هر Version نیز Migrationها باید به ترتیب معکوس اجرای Forward بازگردانده شوند.

## 7.2 Rollback Algorithm

برای Rollback هر Version:

1. انتخاب آخرین Version اعمال‌شده.
2. شروع Transaction مستقل.
3. خواندن Migrationهای متعلق به Version.
4. اجرای `down()` آن‌ها به ترتیب معکوس.
5. پس از موفقیت هر `down()`، حذف رکورد آن Migration از `system_migration`.
6. پس از موفقیت تمام Rollbackها، حذف رکورد Version از `system_migration_version`.
7. Commit Transaction.
8. در صورت درخواست Rollback مراحل بیشتر، رفتن به Version قبلی.

## 7.3 Rollback Example

```text
Rollback
    |
    v
 V3 Begin
    |
    +--> Migration E down()
    |
    +--> Migration D down()
    |
    +--> Delete migration records
    |
    +--> Delete V3 record
    |
    +--> Commit V3
    |
    v
 V2 Begin
    |
    +--> Migration C down()
    |
    +--> Migration B down()
    |
    +--> Failure
    |
    +--> Rollback V2
    |
    v
 STOP
```

در این مثال:

- V3 با موفقیت Rollback شده و از تاریخچه حذف می‌شود.
- V2 به دلیل شکست Rollback نمی‌شود و سابقه آن باقی می‌ماند.
- V1 بدون تغییر باقی می‌ماند.
- فرایند متوقف می‌شود.

## 7.4 Rollback Atomicity

تمام `down()`های یک Version و حذف سوابق آن باید در همان Transaction اجرا شوند.

اگر یکی از `down()`ها شکست بخورد:

- تمام تغییرات همان Version Rollback می‌شوند.
- رکوردهای تاریخچه آن Version باقی می‌مانند.
- Versionهای قبلی دست‌نخورده باقی می‌مانند.

---

# 8. Transaction Ownership

این بخش یکی از مهم‌ترین اصول معماری است.

Migration System نباید مالک Connection یا Transaction lifecycle باشد.

مرزبندی فعلی پروژه حفظ می‌شود:

| Component | Responsibility |
|---|---|
| Operation | Connection lifecycle, Scope, Transaction |
| QueryExecutor | Query execution |
| SchemaExecutor | Schema and DDL execution |
| Migration | Migration workflow and orchestration |
| MigrationVersion | Logical execution boundary |

## 8.1 Forward Transaction

برای هر Version، Migration از API فعلی Operation برای ایجاد Transaction مستقل استفاده می‌کند.

تمام عملیات همان Version باید از Context و Executorهای متعلق به همان Transaction استفاده کنند.

## 8.2 Query Execution

تمام Queryهای تاریخچه باید از:

```kotlin
tx.queryExecutor
```

استفاده کنند.

از ساخت QueryExecutor مستقل یا Connection جدید خودداری شود.

## 8.3 Schema Execution

تمام SchemaOperationها باید از مسیر SchemaExecutor موجود و Scope همان Transaction اجرا شوند.

## 8.4 No Independent Lifecycle

موارد زیر ممنوع هستند:

- ساخت Connection مستقل در Migration.
- ایجاد Transaction مستقل داخل Migration.
- اجرای Insert/Delete تاریخچه خارج از Transaction جاری.
- استفاده از Executor مستقل که Scope فعلی را نادیده بگیرد.

---

# 9. System Migration Components

ساختار فعلی System Migration باید با Versioning توسعه پیدا کند.

ساختار مفهومی:

```text
system_migration
│
├── schema
│   ├── SystemMigrationTable.kt
│   └── SystemMigrationVersionTable.kt
│
├── model
│   ├── SystemMigrationModel.kt
│   └── SystemMigrationVersionModel.kt
│
├── cte
│   ├── SystemMigrationQuery.kt
│   └── SystemMigrationVersionQuery.kt
│
└── query
    ├── SystemMigrationInsertQuery.kt
    ├── SystemMigrationDeleteQuery.kt
    ├── SystemMigrationVersionInsertQuery.kt
    └── SystemMigrationVersionDeleteQuery.kt
```

این ساختار صرفاً سازمان‌دهی مفهومی است و نام‌گذاری نهایی باید با قراردادهای موجود پروژه هماهنگ باشد.

## 9.1 SystemMigrationTable

مسئول تعریف جدول `system_migration` است.

ستون `batch` حذف و ستون `version_id` جایگزین می‌شود.

## 9.2 SystemMigrationVersionTable

مسئول تعریف جدول `system_migration_version` است.

این جدول شناسه Versionهای اعمال‌شده را نگهداری می‌کند.

## 9.3 SystemMigrationQuery

مسئول خواندن تاریخچه Migrationها است.

در صورت نیاز به اطلاعات Version، Query باید امکان دسترسی به رابطه Version و Migration را فراهم کند.

## 9.4 SystemMigrationVersionQuery

مسئول خواندن تاریخچه Versionها است.

این Query برای تشخیص Versionهای اجراشده و تعیین آخرین مرحله موفق استفاده می‌شود.

## 9.5 Insert/Delete Queries

برای هر دو جدول، Queryهای Insert و Delete مرکزی وجود خواهند داشت.

| Table | Insert | Delete |
|---|---|---|
| system_migration | ثبت Migration | حذف سابقه Migration |
| system_migration_version | ثبت Version | حذف سابقه Version |

این Queryها فقط Query می‌سازند و نباید مسئول اجرای آن‌ها باشند.

اجرای Query از طریق Executorهای موجود انجام می‌شود.

---

# 10. Version Consistency and Immutability

یکی از مهم‌ترین قواعد این معماری، ثابت ماندن Versionهای منتشرشده است.

فرض کنیم V1 قبلاً با موفقیت روی دیتابیس اجرا شده است.

بعداً توسعه‌دهنده یک Migration جدید به V1 اضافه کند.

این کار نباید به‌عنوان ادامه طبیعی V1 پذیرفته شود.

Version موفق باید از نظر مجموعه Migrationهای متعلق به خود Immutable باشد.

## 10.1 Required Rules

- Version اجراشده نباید با Migration جدید توسعه پیدا کند.
- Migrationهای آن نباید حذف شوند.
- شناسه Migrationهای آن نباید تغییر کنند.
- ترتیب Migrationهای آن نباید تغییر کند.
- Migration جدید باید در Version جدید قرار بگیرد.

## 10.2 Configuration Comparison

سیستم باید Configuration فعلی را با تاریخچه دیتابیس مقایسه کند.

حداقل این وضعیت‌ها باید قابل تشخیص باشند:

| Situation | Expected Behavior |
|---|---|
| Version جدید و اجرا‌نشده | قابل اجرا |
| Version اجراشده و بدون تغییر | معتبر |
| Version اجراشده با Migration جدید | خطای ناسازگاری |
| Migration اجراشده حذف‌شده از Configuration | خطای ناسازگاری |
| Migration اجراشده به Version دیگری منتقل‌شده | خطای ناسازگاری |
| ترتیب Versionهای قبلی تغییر کرده | خطای ناسازگاری |

سیستم نباید اختلاف Configuration و تاریخچه را با اجرای خودکار UPDATE یا بازنویسی تاریخچه اصلاح کند.

---

# 11. Ordering Rules

ترتیب اجرای Versionها از ترتیب ثبت در Configuration تعیین می‌شود.

ترتیب Migrationها نیز از ترتیب ثبت آن‌ها در Version مشخص می‌شود.

ترتیب اجرای Migrationها:

1. ترتیب Registrationهای مستقیم و Groupها در Version.
2. ترتیب Migrationها داخل هر Group.
3. حفظ ترتیب در تمام مراحل Build و Execution.

برای Rollback، همین ترتیب باید معکوس شود.

## 11.1 Persisted Order

شناسه‌های داخلی Version می‌توانند ترتیب اعمال‌شده را منعکس کنند، مشروط بر اینکه ایجاد Versionها در یک جریان ترتیبی و در Transactionهای موفق انجام شود.

برای تشخیص وضعیت Configuration، سیستم نباید صرفاً به مقایسه رشته Versionها تکیه کند.

در صورت نیاز به ترتیب مستقل و قابل‌تغییر، می‌توان در آینده فیلد ترتیبی صریح اضافه کرد؛ فعلاً نیازی به آن نیست.

---

# 12. Migration Configuration State Validation

پیش از شروع اجرای Migration، سیستم باید وضعیت کلی را بررسی کند.

موارد مهم:

- شناسه‌های Version تکراری نباشند.
- شناسه‌های Migration در کل Configuration یکتا باشند.
- Migration ثبت‌شده در چند Version وجود نداشته باشد.
- Versionهای اجراشده با Configuration فعلی سازگار باشند.
- ترتیب Versionهای قبلی تغییر نکرده باشد.
- تاریخچه ناقص یا ناسازگار شناسایی شود.

اگر تاریخچه دیتابیس نشان دهد Migrationهایی ثبت شده‌اند که Version معتبر ندارند، سیستم باید آن وضعیت را ناسازگار تشخیص دهد.

سیستم نباید برای چنین مواردی حدس بزند یا تاریخچه را خودکار اصلاح کند.

---

# 13. Failure Scenarios

## Scenario A: Failure During Forward Migration

```text
V1 -> Success
V2 -> Success
V3 -> Failure
```

Expected:

- V1 باقی می‌ماند.
- V2 باقی می‌ماند.
- V3 Rollback می‌شود.
- فرایند متوقف می‌شود.

## Scenario B: Failure During Rollback

```text
Rollback V3 -> Success
Rollback V2 -> Failure
```

Expected:

- V3 حذف شده است.
- V2 باقی می‌ماند.
- V1 باقی می‌ماند.
- فرایند متوقف می‌شود.

## Scenario C: Failure During History Insert

اگر اجرای Schema موفق باشد ولی Insert تاریخچه شکست بخورد:

- Transaction همان Version باید Rollback شود.
- تغییر Schema و سایر تغییرات همان Version باید به وضعیت قبل بازگردند.
- Version نباید به‌عنوان موفق ثبت شود.

## Scenario D: Failure During History Delete

اگر اجرای `down()` موفق باشد ولی حذف رکورد تاریخچه شکست بخورد:

- Transaction همان Version باید Rollback شود.
- تغییرات `down()` باید بازگردانده شوند.
- تاریخچه Version باید باقی بماند.

## Scenario E: Configuration Changed After Release

اگر Version اجراشده تغییر کرده باشد:

- سیستم باید ناسازگاری را تشخیص دهد.
- Migration جدید را به Version قبلی اضافه نکند.
- اجرای خودکار و اصلاح تاریخچه انجام ندهد.

---

# 14. Database Transactional DDL Compatibility

این معماری بر Transaction مستقل برای هر Version تکیه دارد.

اما باید یک محدودیت مهم در نظر گرفته شود:

تمام دیتابیس‌ها و Dialectها رفتار یکسانی در برابر DDL داخل Transaction ندارند.

برخی عملیات DDL ممکن است:

- به‌صورت کامل Transactional باشند.
- باعث Implicit Commit شوند.
- در صورت شکست قابل Rollback نباشند.
- محدودیت‌های خاصی در اجرای DDL داخل Transaction داشته باشند.

بنابراین قبل از ادعای Atomicity کامل، باید قابلیت Transactional DDL برای دیتابیس‌های پشتیبانی‌شده بررسی شود.

### Required Behavior

- Migration System باید از رفتار واقعی Dialectها آگاه باشد.
- نباید تضمین Rollback کامل برای عملیاتی بدهد که دیتابیس آن را پشتیبانی نمی‌کند.
- اگر یک Dialect محدودیت دارد، این محدودیت باید مستند و قابل تشخیص باشد.
- DDL Failure Policy و رفتار موجود Operation باید حفظ شوند.

همچنین Migrationها نباید خارج از Transaction جاری Side Effect غیرقابل بازگشت ایجاد کنند.

---

# 15. Development Architecture and Module Boundaries

مرزبندی فعلی ماژول‌ها باید حفظ شود.

```text
Migration API
    |
    v
Migration Registration
    |
    v
Migration Executor
    |
    +------> Operation
    |
    +------> QueryExecutor
    |
    +------> SchemaExecutor
```

## Migration Core

مسئول:

- Migration contract
- MigrationDefinition
- MigrationIdentity
- System Migration schema
- System Migration models
- System Migration query declarations

Core نباید به Executor وابسته شود.

## Migration API

مسئول:

- Migration registration contracts
- Migration configuration
- Public builders
- MigrationVersion registration
- MigrationGroup registration

## Migration Executor

مسئول:

- اجرای Configuration
- انتخاب Versionهای موردنیاز
- اجرای Forward workflow
- اجرای Rollback workflow
- هماهنگی Transactionها
- هماهنگی Reader و Recorder
- توقف و گزارش Failure

## Operation

مالک Connection و Transaction lifecycle باقی می‌ماند.

هیچ تغییری در معماری تثبیت‌شده Operation صرفاً برای پیاده‌سازی Versioning مجاز نیست، مگر نیاز واقعی و مشخصی اثبات شود.

---

# 16. Testing Strategy

تست‌ها باید بدون نیاز به دیتابیس واقعی، رفتار Migration System را بررسی کنند.

## 16.1 Registration Tests

- ثبت Version مستقل.
- ثبت چند Version.
- ثبت Migration مستقل داخل Version.
- ثبت Group داخل Version.
- حفظ ترتیب Registrationها.
- رد Version تکراری.
- رد Migration تکراری.
- رد Migration ثبت‌شده در چند Version.
- رد Version خالی، مطابق قرارداد نهایی.

## 16.2 Forward Workflow Tests

- اجرای موفق یک Version.
- اجرای چند Version به ترتیب.
- ایجاد Transaction مستقل برای هر Version.
- استفاده از Transaction مشترک برای تمام Migrationهای یک Version.
- Commit موفق Version.
- Rollback کامل Version ناموفق.
- باقی ماندن Versionهای موفق قبلی.
- توقف فرایند پس از Failure.

## 16.3 Rollback Workflow Tests

- انتخاب آخرین Version.
- اجرای Versionها به ترتیب معکوس.
- اجرای Migrationها به ترتیب معکوس.
- حذف رکورد Migration پس از موفقیت `down()`.
- حذف رکورد Version پس از موفقیت تمام Migrationها.
- Rollback کامل Version ناموفق.
- باقی ماندن تاریخچه Version ناموفق.
- توقف فرایند پس از Failure.

## 16.4 History Tests

- خواندن Versionها.
- خواندن Migrationهای متعلق به هر Version.
- تطبیق تاریخچه با Configuration.
- تشخیص Version ناسازگار.
- تشخیص Migration حذف‌شده از Configuration.
- تشخیص Migration منتقل‌شده به Version دیگر.
- تشخیص تاریخچه ناقص.

## 16.5 Transaction Scope Tests

با Fake Executor و Fake Transaction بررسی شود:

- تمام Queryهای تاریخچه از `tx.queryExecutor` استفاده می‌کنند.
- تمام SchemaOperationها از Scope همان Transaction استفاده می‌کنند.
- هیچ Connection مستقل ایجاد نمی‌شود.
- شکست Insert باعث Rollback Transaction می‌شود.
- شکست Delete باعث Rollback Transaction می‌شود.

تست‌های واقعی دیتابیس باید جداگانه و بر اساس Dialectهای پشتیبانی‌شده انجام شوند.

---

# 17. Development Roadmap

پیاده‌سازی باید مرحله‌ای باشد تا معماری موجود بدون نیاز به بازنویسی گسترده توسعه پیدا کند.

## Phase 1: Version Registration

- اضافه‌کردن `MigrationVersion` به Registration API.
- اضافه‌کردن `MigrationVersionBuilder`.
- پشتیبانی از Migration و MigrationGroup داخل Version.
- حفظ ترتیب Registrationها.
- اعتبارسنجی Version و Migration Identity.
- تست Registration.

**Acceptance Criteria:** Configuration بتواند چند Version مستقل را با ترتیب مشخص نگهداری کند.

## Phase 2: Database Schema

- اضافه‌کردن `SystemMigrationVersionTable`.
- اضافه‌کردن `SystemMigrationVersionModel`.
- تغییر `SystemMigrationTable`.
- حذف ستون `batch`.
- اضافه‌کردن ستون `version_id`.
- تعریف Foreign Key و Unique Constraints.
- بررسی سازگاری Schema با Dialectهای پشتیبانی‌شده.

**Acceptance Criteria:** ساختار تاریخچه بتواند رابطه یک‌به‌چند Version و Migration را نگهداری کند.

## Phase 3: System Migration Queries

- اضافه‌کردن Version Insert Query.
- اضافه‌کردن Version Delete Query.
- اضافه‌کردن Version Reader Query.
- اصلاح Migration Insert Query برای استفاده از `version_id`.
- اصلاح Migration Delete Query در صورت نیاز.
- تست SQL و پارامترها.

**Acceptance Criteria:** تمام عملیات تاریخچه از Queryهای مرکزی استفاده کنند.

## Phase 4: History Reader and Recorder

- توسعه Reader برای خواندن Versionها و Migrationهای آن‌ها.
- توسعه Recorder برای ثبت Version.
- توسعه Recorder برای ثبت Migration با `version_id`.
- حذف رکوردهای Migration هنگام Rollback.
- حذف رکورد Version پس از Rollback کامل آن.
- حفظ اجرای تمام Queryها در Transaction جاری.

**Acceptance Criteria:** تاریخچه Version و Migration به‌درستی خوانده، ثبت و حذف شود.

## Phase 5: Forward Migrator

- مقایسه Configuration با تاریخچه.
- تشخیص Versionهای اجرا‌نشده.
- اجرای هر Version در Transaction مستقل.
- اجرای تمام Migrationهای Version در Transaction مشترک.
- ثبت تاریخچه در همان Transaction.
- توقف فرایند در صورت شکست.

**Acceptance Criteria:** اجرای موفق و شکست هر Version مطابق قرارداد Transaction رفتار کند.

## Phase 6: Rollback Migrator

- انتخاب Versionهای قابل Rollback.
- اجرای Versionها به ترتیب معکوس.
- اجرای Migrationها به ترتیب معکوس.
- اجرای `down()` در Transaction مستقل هر Version.
- حذف تاریخچه در همان Transaction.
- توقف فرایند در صورت شکست.

**Acceptance Criteria:** Rollback مرحله‌ای و قابل پیش‌بینی باشد.

## Phase 7: Consistency and Compatibility

- بررسی تغییر Versionهای منتشرشده.
- تشخیص تغییر ترتیب Versionها.
- تشخیص تغییر Migrationهای قبلی.
- بررسی محدودیت Transactional DDL.
- مستندسازی رفتار Dialectها.
- تکمیل تست‌های Regression.

**Acceptance Criteria:** سیستم در برابر Configuration ناسازگار رفتار صریح و امن داشته باشد.

---

# 18. Important Architectural Decisions

تصمیم‌های اصلی این معماری:

| Decision | Description |
|---|---|
| Version-based execution | Version واحد اجرای مرحله‌ای است |
| Independent transactions | هر Version یک Transaction مستقل دارد |
| Atomic version | تمام Migrationهای یک Version در یک Transaction هستند |
| Explicit registration | توسعه‌دهنده Versionها را تعریف می‌کند |
| Group is organizational | Group واحد اجرایی مستقل نیست |
| No automatic batch | Batch از مدل حذف می‌شود |
| Reverse rollback | Rollback در ترتیب معکوس انجام می‌شود |
| History consistency | تاریخچه باید با Configuration سازگار باشد |
| Stop on failure | شکست یک Version اجرای مراحل بعدی را متوقف می‌کند |
| Existing Operation ownership | Operation مالک Transaction باقی می‌ماند |

---

# 19. Final Vision

هدف این معماری این نیست که صرفاً Migrationها را به چند گروه تقسیم کند.

هدف این است که توسعه‌دهنده بتواند تغییرات Schema را به مراحل مشخص انتشار تقسیم کند و سیستم مسئولیت اجرای امن و قابل پیش‌بینی آن مراحل را بر عهده بگیرد.

در این مدل:

- Migration تعریف‌کننده یک تغییر Schema است.
- MigrationGroup ابزار سازمان‌دهی Migrationها است.
- MigrationVersion واحد انتشار و اجرای اتمیک است.
- Operation مسئول مدیریت Transaction است.
- QueryExecutor و SchemaExecutor عملیات واقعی را اجرا می‌کنند.
- Migration Executor ترتیب و جریان اجرای مراحل را مدیریت می‌کند.
- System Migration History وضعیت واقعی مراحل اعمال‌شده را نگهداری می‌کند.

جریان نهایی:

```text
Migration Configuration
        |
        v
Migration Versions
        |
        v
Version Selection
        |
        v
Independent Transaction
        |
        v
Execute Migration Operations
        |
        v
Record Migration History
        |
        v
Commit Version
        |
        v
Next Version
```

در صورت شکست:

```text
Version Failure
        |
        v
Rollback Current Transaction
        |
        v
Preserve Previous Versions
        |
        v
Stop Migration Process
```

**اصل نهایی طراحی:**

هر Version یک مرحله مستقل از تغییرات Schema است که باید به‌صورت کامل اجرا و ثبت شود یا به‌طور کامل به وضعیت قبل بازگردد؛ در حالی که Versionهای قبلی موفق دست‌نخورده باقی می‌مانند.

این مرز مشخص، اساس کنترل، بازیابی و توسعه قابل اعتماد Schema در Migration System خواهد بود.