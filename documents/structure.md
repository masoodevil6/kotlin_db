# ساختار ماژولار پروژه

این پروژه یک پروژه چندماژوله Kotlin/Gradle است که هسته آن برای ساخت، رندر و اجرای کوئری‌های SQL و همچنین تعریف و اجرای migration طراحی شده است. ماژول‌ها در `settings.gradle.kts` ثبت شده‌اند و عمدتاً از Kotlin/JVM با toolchain نسخه 24 استفاده می‌کنند.

## نمای کلی درخت پروژه

```text
my_project/
├── app/                              # برنامه نمونه و نقطه ورود اصلی
├── utils/                            # ابزارهای عمومی قابل استفاده مجدد
├── tools/                            # ابزارهای داخلی و اسکریپت‌های کمکی
├── data_base/                        # بدنه اصلی کتابخانه دیتابیس
│   ├── core/                         # قراردادها و مدل‌های پایه
│   ├── manager/                      # مدیریت اتصال و اجرای عملیات دیتابیس
│   │   ├── connection/               # ایجاد و مدیریت اتصال
│   │   └── execute/                  # اجرای کوئری و دریافت نتیجه
│   ├── models/                       # لایه مدل‌ها
│   │   └── eloquent/                 # مدل‌سازی به سبک Eloquent
│   ├── query/                        # زیرسیستم ساخت و اجرای کوئری
│   │   ├── api/                      # APIهای عمومی و interfaceها
│   │   ├── ast/                      # ساختار درخت نحوی کوئری
│   │   ├── builder/                   # builderهای fluent
│   │   ├── dialect/                   # مدل داده و قرارداد رندر
│   │   ├── renderer/                 # رندر SQL، فعلاً برای MySQL
│   │   ├── executor/                 # اتصال builder/renderer به اجرا
│   │   └── example/                   # مثال‌های query builder
│   └── migration/                     # زیرسیستم migration و ساخت schema
│       ├── params/                    # انواع داده ستون‌ها
│       ├── ast/                       # AST مربوط به migration
│       ├── api/                       # قراردادهای عمومی migration
│       ├── builder/                   # builderهای جدول و ستون
│       ├── dialect/                   # قرارداد و مدل رندر migration
│       ├── renderer/                  # رندر migration، فعلاً برای MySQL
│       ├── executor/                  # اجرای migration
│       └── example/                   # مثال‌های migration
├── buildSrc/                          # منطق مشترک build و convention pluginها
├── gradle/                            # wrapper و version catalog
├── gradlew / gradlew.bat              # Gradle Wrapper
├── settings.gradle.kts                # تعریف پروژه‌ها و subprojectها
└── gradle.properties                  # تنظیمات عمومی Gradle
```

## ماژول‌های ریشه

### `app`

برنامه اجرایی نمونه پروژه است. نقطه ورود آن در `app/src/main/kotlin/App.kt` قرار دارد و این ماژول به `utils` وابسته است.

### `utils`

کدهای عمومی و قابل استفاده مجدد را نگهداری می‌کند. `Utilities.kt` و تست‌های آن در این ماژول قرار دارند.

### `tools`

ابزارهای داخلی پروژه را ارائه می‌کند؛ برای نمونه ابزارهای مرتبط با رشته‌ها در `tools/src/main/kotlin/tools/scripts/StringTools.kt` قرار دارند. بخش‌هایی مانند query و migration از این ماژول استفاده می‌کنند.

### `buildSrc`

منطق مشترک Gradle در این پوشه قرار دارد. فایل `kotlin-jvm.gradle.kts` یک convention plugin فراهم می‌کند تا تنظیمات مشترک Kotlin/JVM تکرار نشود.

### `gradle`

شامل Gradle Wrapper و version catalog پروژه است:

- `gradle/libs.versions.toml`: نسخه پلاگین‌ها و dependencyها
- `gradle/wrapper/`: فایل‌ها و jar مربوط به Gradle Wrapper

## هسته دیتابیس: `data_base`

### `data_base:core`

لایه پایه و مستقل پروژه است. تنظیمات دیتابیس، annotationها، قراردادهای مدل و ابزارهای خواندن query در این ماژول قرار دارند.

```text
core/src/main/kotlin/
├── annotations/
│   ├── ctes/                         # annotationهای CTE
│   └── models/                       # annotationهای جدول و ستون مدل
├── data_base/
│   ├── DatabaseConfig.kt
│   ├── DatabaseConfigBuilder.kt
│   └── DefaultDatabaseConfig.kt
├── managers/models/
│   └── IModelBase.kt
└── query/reader/
    ├── BuiltQuery.kt                 # کوئری ساخته‌شده
    ├── SqlParamData.kt               # پارامترهای SQL
    └── SqlTypeResolver.kt            # تشخیص یا تبدیل نوع SQL
```

### `data_base:manager`

ماژول والد مدیریت دیتابیس است و دو بخش تخصصی دارد:

- `manager:connection`: قرارداد `IDatabaseConnection` و پیاده‌سازی `DatabaseConnection`؛ وابسته به `core` و درایور MySQL.
- `manager:execute`: قرارداد `IQueryExecute` و پیاده‌سازی `QueryExecute`؛ وابسته به connection و core.

### `data_base:models`

ماژول والد مدل‌ها است.

#### `data_base:models:eloquent`

مدل‌سازی به سبک Eloquent را ارائه می‌کند. `BaseModel`، interface `IModel` و نمونه‌هایی مانند `Users` و `UserPhones` در این بخش قرار دارند. این ماژول به `core` وابسته است.

## زیرسیستم Query

مسیر کلی تولید کوئری:

```text
API → AST → Builder → Dialect Data/Context → Renderer → Executor
```

### `data_base:query:api`

قرارداد عمومی query builder را تعریف می‌کند. interfaceهای جداگانه برای `select`، `insert`، `update`، `delete`، ستون‌ها، جدول، شرط‌ها، join، order، limit، offset و CTE در این ماژول قرار دارند. enumهای نوع join، عملگر شرط و ترتیب مرتب‌سازی نیز اینجا هستند.

### `data_base:query:ast`

ساختارهای AST کوئری را نگهداری می‌کند. schemaهای متناظر برای select، insert، update و delete در این لایه قرار دارند.

```text
ast/src/main/kotlin/
├── interfaces/                       # قراردادهای AST
└── schema/
    ├── select_schema/
    ├── insert_schema/
    ├── update_schema/
    └── delete_schema/
```

### `data_base:query:builder`

builderهای fluent را روی AST ارائه می‌کند و ورودی سطح بالای کاربر را به nodeهای AST تبدیل می‌کند. builderهای select، insert، update، delete، شرط‌ها، joinها و optionهای query در این بخش قرار دارند. این ماژول به `core`، `api`، `ast` و `models:eloquent` وابسته است.

### `data_base:query:dialect`

مدل‌های داده و قراردادهای مستقل از یک SQL dialect را تعریف می‌کند؛ از جمله `ISqlDialect`، `IAstRenderer`، `IRenderContext`، `IRendererRegistry`، context/registry رندر و capability interfaceهای nodeهای query.

### `data_base:query:renderer`

پیاده‌سازی رندر SQL برای dialectها است. در وضعیت فعلی، `MySqlDialect` و capabilityهای MySQL برای select، insert، update، delete، شرط‌ها، joinها و optionها در این ماژول قرار دارند.

### `data_base:query:executor`

لایه اتصال query builder به اجرای واقعی است. `QueryBuilderExecutor` با استفاده از AST، builder، dialect، renderer و `manager:execute` کوئری را آماده و اجرا می‌کند.

### `data_base:query:example`

مثال‌های query builder را برای select، insert، update و delete ارائه می‌کند. نمونه‌ها در مسیر `v1/queries` قرار دارند.

## زیرسیستم Migration

مسیر کلی migration:

```text
Params → API/AST → Builder → Dialect → Renderer → Executor
```

### `data_base:migration:params`

انواع داده ستون‌ها مانند `IntType`، `BooleanType`، `DecimalType`، `JsonType`، `TextType` و `VarcharType` را تعریف می‌کند.

### `data_base:migration:ast`

AST مربوط به migration را مدل می‌کند؛ از جمله ساختار جدول، ستون و رندر `create table`.

### `data_base:migration:api`

قراردادهای عمومی migration برای جدول، ستون و رندر ایجاد جدول را ارائه می‌دهد. این ماژول به core، params و ast وابسته است.

### `data_base:migration:builder`

builderهای تعریف migration را فراهم می‌کند: `MigrationTableBuilder`، `MigrationColumnBuilder` و `MigrationRenderCreateTableBuilder`.

### `data_base:migration:dialect`

قراردادها و مدل‌های مستقل از موتور دیتابیس برای رندر migration را نگهداری می‌کند؛ مانند `ISqlDialect`، context/registry رندر و capabilityهای جدول و ستون.

### `data_base:migration:renderer`

رندر dialect-specific برای migration است. پیاده‌سازی فعلی MySQL شامل `MySqlDialect` و capabilityهای جدول، ستون و `create table` است.

### `data_base:migration:executor`

migration ساخته‌شده را اجرا می‌کند. `MigrationExecutor` از API، AST، dialect، renderer و `manager:execute` استفاده می‌کند و به `tools` نیز وابسته است.

### `data_base:migration:example`

نمونه‌های استفاده از migration را نگهداری می‌کند.

## جهت وابستگی لایه‌ها

```text
manager:connection
└── core

manager:execute
├── manager:connection
└── core

query:api
├── core
└── query:ast

query:builder
├── core
├── query:ast
├── query:api
└── models:eloquent

query:dialect
├── core
├── query:ast
├── query:api
└── query:builder

query:renderer
├── core
├── query:api
├── query:ast
└── query:dialect

query:executor
├── tools
├── core
├── query:ast
├── query:builder
├── query:api
├── query:dialect
├── query:renderer
└── manager:execute

migration:ast
├── core
└── migration:params

migration:api
├── core
├── migration:params
└── migration:ast

migration:builder
├── core
├── migration:params
├── migration:ast
└── migration:api

migration:dialect
├── core
└── migration:ast

migration:renderer
├── core
├── migration:params
├── migration:ast
├── migration:api
└── migration:dialect

migration:executor
├── tools
├── core
├── migration:api
├── migration:ast
├── migration:dialect
├── migration:renderer
└── manager:execute
```

## فایل‌ها و تنظیمات مهم ریشه

- `settings.gradle.kts`: ثبت تمام subprojectها
- `gradle.properties`: تنظیمات عمومی Gradle و cache
- `gradle/libs.versions.toml`: مدیریت نسخه dependencyها و pluginها
- `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`: convention مشترک Kotlin/JVM
- `gradlew` و `gradlew.bat`: اجرای build بدون نصب جداگانه Gradle
- `README.md`: راهنمای اولیه اجرای build و taskهای اصلی

## نکته‌های نگهداری ساختار

1. قراردادهای عمومی را در `api` نگه دارید و جزئیات پیاده‌سازی را به `builder`، `renderer` یا `executor` منتقل کنید.
2. منطق وابسته به موتور دیتابیس باید در `renderer` و capabilityهای همان dialect قرار بگیرد.
3. مدل‌های مستقل از SQL engine در `ast` و `dialect` باقی بمانند.
4. برای افزودن موتور جدید، capabilityها و dialect جدید در کنار پیاده‌سازی MySQL اضافه شود؛ builder و API تا حد امکان مستقل بمانند.
5. مثال‌ها در ماژول‌های `query:example` و `migration:example` قرار گیرند تا با کد کتابخانه مخلوط نشوند.
