# نمای درختی AST در Query و Schema

> این سند بر اساس ساختار فعلی کد در ۷ نوامبر ۲۰۲۶ نوشته شده است. هدف، نشان دادن شکل داده‌ای است که DSL می‌سازد و مسیر تبدیل آن به SQL؛ این نمودار، ERD جدول‌های دیتابیس نیست.

## AST در این پروژه چه نقشی دارد؟

در این پروژه، DSL کوئری یا عملیات Schema را مستقیماً به یک رشتهٔ SQL تبدیل نمی‌کند. ابتدا Builderها ساختارهای AST را می‌سازند. سپس Renderer با توجه به Dialect، گره‌ها را به SQL تبدیل می‌کند و مسیر اجرا آن را به Executor می‌سپارد.

~~~mermaid
flowchart LR
    QDSL["Query DSL / API"] --> QB["Query Builder"]
    QB --> QAST["Query AST"]
    QAST --> QR["Dialect + Renderer"]
    QR --> QSQL["BuiltQuery / SQL"]
    QSQL --> QEXEC["Query Executor"]

    SDSL["Schema DSL / API"] --> SB["Schema Builder"]
    SB --> SAST["Schema AST"]
    SAST --> SR["Dialect + Renderer"]
    SR --> SSQL["DDL SQL"]
    SSQL --> SEXEC["Schema Executor"]
~~~

Query و Schema دو خانوادهٔ AST جدا دارند: Query از قرارداد `IQueryAst` و Schema از `ISchemaAst` استفاده می‌کند. هر دو قرارداد، گره را به یک `metadata` نوع‌دار وصل می‌کنند. رابطهٔ گره‌ها و فرزندانشان در propertyهای AST قرار دارد؛ نام‌ها، مقادیر و گزینه‌های scalar عمدتاً در `metadata` همان گره نگهداری می‌شوند.

در نمودارها:

- `?` یعنی فرزند اختیاری؛ `[]` یعنی فهرست گره‌ها.
- مسیرهای پیوندخورده نسبت به پوشهٔ `documents` هستند.
- نمونه‌های درختی، قراردادهای اصلی را نشان می‌دهند و تمام فیلدهای هر metadata را فهرست نمی‌کنند.

## لایه‌های مرتبط

| لایه | Query | Schema |
| --- | --- | --- |
| قرارداد DSL | `data_base/operation/query/api` | `data_base/operation/schema/api` |
| ساخت AST | `data_base/operation/query/builder` | `data_base/operation/schema/builder` |
| قراردادها و گره‌های AST | `data_base/operation/query/ast` | `data_base/operation/schema/ast` |
| Dialect و Registry رندر | `data_base/operation/query/dialect` | `data_base/operation/schema/dialect` |
| پیاده‌سازی Renderer | `data_base/operation/query/renderer` | `data_base/operation/schema/renderer` |
| اجرا | `data_base/operation/query/executor` | `data_base/operation/schema/executor` |

ساختار ماژول‌ها در `settings.gradle.kts` ثبت شده است. Query همچنین ماژول‌های `cte` و `meta_data` دارد.

## درخت Query

### ریشهٔ SELECT

Builder اصلی SELECT، گره‌های قابل‌استفاده در کوئری را به `QueryRenderSelectAst` متصل می‌کند:

~~~text
QueryRenderSelectBuilder
└── ast: IQueryRenderSelectAst → QueryRenderSelectAst
    ├── withs?: QueryWithsAst
    │   └── withs[]: QueryWithsItemAst
    │       └── withBody?: IQueryRenderSelectAst
    ├── select?: QuerySelectAst
    │   └── columns[]: QueryColumnsAst
    │       ├── Column?: QueryColumnsBaseAst
    │       └── ColumnMethod*: یکی از گره‌های تابعی مثل Count / Sum / Avg / Min / Max / Concat
    ├── table?: QueryTableAst
    │   └── derivedQuery?: IQueryRenderSelectAst
    ├── joins?: QueryJoinsAst
    │   └── joins[]: QueryJoinsItemAst
    │       ├── joinTable: QueryTableAst
    │       └── joinConditions: QueryConditionsGroupsAst
    ├── where?: QueryWhereAst
    │   └── condition?: QueryConditionsGroupsAst
    ├── optionGroup?: QueryOptionGroupAst
    ├── optionOrder?: QueryOptionOrderAst
    ├── optionLimit?: QueryOptionLimitAst
    ├── optionOffset?: QueryOptionOffsetAst
    └── lockForUpdate?: QuerySelectLockRowAst
~~~

در `QueryColumnsAst`، alias خروجی، نوع خروجی، ارجاع property مدل و مشخصات تابع در metadata ثبت می‌شوند. `QueryTableAst` نیز metadata مربوط به نام و alias منبع را نگه می‌دارد و برای query مشتق‌شده می‌تواند یک SELECT دیگر را به‌عنوان فرزند داشته باشد.

### شرط‌ها و پارامترها

`WHERE` و شرط `JOIN` از گروه‌های شرط استفاده می‌کنند. گروه می‌تواند زیرگروه داشته باشد؛ شرط منفرد دو طرف مقایسه و metadata مربوط به عملگر و منطق اتصال را نگه می‌دارد.

~~~text
QueryConditionsGroupsAst
├── conditions[]: IQueryIsConditionAst<*>
│   ├── QueryConditionsGroupsAst       گروه تو در تو
│   ├── QueryConditionsAst             شرط منفرد
│   │   ├── conditionSideLeft?: QueryColumnsBaseAst
│   │   └── conditionSideRight?: یکی از مسیرهای پشتیبانی‌شده، مانند:
│   │       ├── QueryColumnsBaseAst
│   │       ├── QueryParameterAst<*>
│   │       └── QueryConditionsCollectionAst
│   └── QueryConditionsCollectionAst   مجموعهٔ ورودی شرط
└── metadata: conditionLogical
~~~

پارامتر مستقل Query در `QueryParameterAst` نگهداری می‌شود؛ metadata آن شامل نام، مقدار و SQL type است. در INSERT و UPDATE هم هر گرهٔ مقدار ستون می‌تواند به یک `parameterAst` وصل شود. Builder از `ParameterBindingScope` برای نام‌گذاری و یکی‌کردن تعریف‌های پارامتر هم‌ارز در محدودهٔ ساخت یک Query استفاده می‌کند.

### INSERT، UPDATE و DELETE

سه عمل تغییر داده، ریشه‌های مستقل دارند و در صورت نیاز از گرهٔ جدول، گره‌های ستون، پارامتر و شرط‌های مشترک استفاده می‌کنند:

~~~text
QueryRenderInsertAst
├── table?: QueryTableAst
└── columns[]: QueryColumnInsertAst
    └── parameterAst?: QueryParameterAst<*>

QueryRenderUpdateAst
├── table?: QueryTableAst
├── columns[]: QueryColumnUpdateAst
│   └── parameterAst?: QueryParameterAst<*>
└── where?: QueryWhereAst

QueryRenderDeleteAst
├── table?: QueryTableAst
└── where?: QueryWhereAst
~~~

نام ستون و جزئیات همان operation در metadata گرهٔ مربوط به ستون یا ریشه ذخیره می‌شود. وجود گرهٔ اختیاری در AST به‌تنهایی کامل‌بودن دستور را تضمین نمی‌کند؛ Renderer برای بعضی ساختارها پیش‌شرط‌هایی مثل نام جدول و تعریف ستون‌ها را بررسی می‌کند.

### CTE و وابستگی‌ها

قرارداد `Cte` یک تعریف SELECT را همراه با نام و فهرست وابستگی‌ها نگه می‌دارد. `CteAstComposer` از این تعریف‌ها گره‌های `WITH` و منبع جدول CTE را در AST کوئری مصرف‌کننده می‌سازد.

این بخش دو شکل ارتباط دارد:

1. بدنهٔ هر CTE خودش یک درخت SELECT است.
2. فهرست `dependencies` بین CTEها یک گراف وابستگی می‌سازد؛ Composer آن را برای قراردادن وابستگی‌ها پیش از مصرف‌کننده پیمایش می‌کند.

در نسخهٔ فعلی، Composer برای یک SELECT فقط یک منبع CTE فعال می‌پذیرد. تعریف CTE باید SELECT داشته باشد و خروجی‌هایش alias غیرخالی و یکتا داشته باشند.

## درخت Schema

### CREATE TABLE

Schema هم همان الگوی «ساختار فرزندها در AST، مقدارهای scalar در metadata» را دارد. ریشهٔ CREATE TABLE، جدول، ستون‌ها و constraintها را کنار هم نگه می‌دارد:

~~~text
SchemaRenderCreateTableAst
├── metadata
│   └── ifNotExists: Boolean
├── schemaTableAst?: SchemaTableAst
│   └── metadata.tableName
├── columns[]: SchemaColumnAst
│   └── metadata:
│       ├── columnName
│       ├── columnDataType
│       ├── columnDefault / hasColumnDefault
│       ├── columnNullable
│       ├── columnAutoIncrement
│       └── columnPrimary
└── definitions[]: ISchemaCreateTableDefinitionAst<*>
    ├── PrimaryKeyDefinitionAst
    ├── UniqueDefinitionAst
    ├── IndexDefinitionAst
    ├── FullTextIndexDefinitionAst
    └── ForeignKeyDefinitionAst
~~~

جزئیات هر constraint در metadata نوع‌دار همان definition قرار می‌گیرد؛ برای نمونه، foreign key نام constraint، ستون‌های مبدأ و مقصد و actionهای حذف و به‌روزرسانی را دارد. Builderهای جداگانهٔ جدول، ستون و definitionها این گره‌ها را می‌سازند.

### سایر عملیات DDL

Schema از یک درخت عمومی واحد برای همهٔ دستورات استفاده نمی‌کند. هر نوع DDL ریشهٔ AST و metadata متناسب خودش را دارد:

~~~text
SchemaAddColumnAst
├── metadata.tableName
└── columnAst?: SchemaColumnAst

SchemaDropTableAst
├── metadata.tableName
└── ifExistAst?: SchemaIfExistAst

SchemaRenameColumnAst
└── metadata: tableName, name, to

دیگر ریشه‌های مستقل:
├── ModifyColumn
├── DropColumn
├── RenameTable
├── Create / Drop Index
├── Create Unique / Multi-column / Full-text / Spatial Index
└── Create / Drop Foreign Key
~~~

برای نمونه، `SchemaDropTableAst` نام جدول را در metadata و گزینهٔ `IF EXISTS` را در یک گرهٔ اختیاری مجزا نگه می‌دارد. `SchemaAddColumnAst` هم نام جدول را در metadata و تعریف ستون را در فرزند `columnAst` قرار می‌دهد.

`SchemaOperation` که از Facade عمومی برمی‌گردد، یک handle پوشانندهٔ یک عملیات است؛ ریشهٔ AST نیست. عملیات ساخته‌شده با Dialect متناسب render می‌شود. Renderer از Registry برای انتخاب renderer هر نوع گره استفاده می‌کند و `SchemaExecutor` اجرای SQL را در scope عملیات مدیریت می‌کند.

## Query و Schema در یک نگاه

| ویژگی | Query AST | Schema AST |
| --- | --- | --- |
| مدل اصلی | درختی از اجزای SELECT و DML | درخت یک فرمان DDL |
| ریشه‌ها | SELECT، INSERT، UPDATE و DELETE هرکدام ریشه دارند | هر عملیات DDL ریشهٔ متناسب خودش را دارد |
| ترکیب | زیرکوئری، شرط تو در تو و CTE | جدول، ستون، constraint و گزینه‌های عملیات |
| metadata | مشخصات ستون، alias، پارامتر و عملگر | نام جدول و ستون، نوع داده و گزینه‌های DDL |
| گسترش به SQL | Rendererهای وابسته به Dialect | Rendererهای وابسته به Dialect |

نکتهٔ معماری قابل‌انتقال به پست: DSL ابتدا «معنای عملیات» را به یک ساختار قابل‌بررسی تبدیل می‌کند. لایهٔ Dialect بعداً همان ساختار را با قواعد پایگاه‌دادهٔ هدف به SQL تبدیل می‌کند. این جداسازی، منطق ساخت Query و Schema را از جزئیات نوشتار SQL جدا می‌کند.

## جمع‌بندی آماده برای استفاده در پست

در Query Builder این پروژه، درخواست‌های خواندن و تغییر داده ابتدا به AST تبدیل می‌شوند؛ ساختارهایی برای SELECT، شرط‌های تو در تو، پارامترها، CTE و عملیات INSERT/UPDATE/DELETE. در بخش Schema نیز دستورهایی مثل CREATE TABLE به درختی از جدول، ستون‌ها و constraintها مدل می‌شوند.

Builderها این ساختار را می‌سازند و Rendererهای هر Dialect آن را به SQL تبدیل می‌کنند. نتیجه، معماری‌ای است که در آن DSL، مدل AST و SQL هرکدام مسئولیت روشن خودشان را دارند و توسعهٔ رفتارهای وابسته به پایگاه‌داده در لایهٔ رندر انجام می‌شود.

## مسیرهای کد برای بررسی

| موضوع | فایل |
| --- | --- |
| ثبت ماژول‌ها | [settings.gradle.kts](../settings.gradle.kts) |
| قرارداد پایهٔ Query AST | [IQueryAst.kt](../data_base/operation/query/ast/src/main/kotlin/interfaces/IQueryAst.kt) |
| ریشهٔ SELECT در Query | [QueryRenderSelectAst.kt](../data_base/operation/query/ast/src/main/kotlin/schema/select_schema/query_render_select/QueryRenderSelectAst.kt) |
| Builder اصلی SELECT | [QueryRenderSelectBuilder.kt](../data_base/operation/query/builder/src/main/kotlin/ast/select_builder/query_render_select/QueryRenderSelectBuilder.kt) |
| ساخت شرط و پارامتر شرط | [QueryConditionsBuilder.kt](../data_base/operation/query/builder/src/main/kotlin/ast/select_builder/conditions_item/QueryConditionsBuilder.kt) |
| ساختار شرط‌ها | [QueryConditionsGroupsAst.kt](../data_base/operation/query/ast/src/main/kotlin/schema/select_schema/conditions_group/QueryConditionsGroupsAst.kt) |
| تعریف پارامتر Query | [QueryParameterAst.kt](../data_base/operation/query/ast/src/main/kotlin/schema/parameter/QueryParameterAst.kt) |
| قرارداد CTE | [Cte.kt](../data_base/operation/query/api/src/main/kotlin/interfaces/relations/Cte.kt) |
| ساخت WITH و ترتیب وابستگی‌ها | [CteAstComposer.kt](../data_base/operation/query/builder/src/main/kotlin/ast/select_builder/cte/CteAstComposer.kt) |
| رندر ریشهٔ SELECT در MySQL | [MySqlQueryCapability.kt](../data_base/operation/query/renderer/src/main/kotlin/nodes/select_nodes/query/MySqlQueryCapability.kt) |
| قرارداد پایهٔ Schema AST | [ISchemaAst.kt](../data_base/operation/schema/ast/src/main/kotlin/interfaces/ISchemaAst.kt) |
| ریشهٔ CREATE TABLE | [SchemaRenderCreateTableAst.kt](../data_base/operation/schema/ast/src/main/kotlin/schema/create_table/render_query_create_table/SchemaRenderCreateTableAst.kt) |
| ساخت AST مربوط به CREATE TABLE | [SchemaRenderCreateTableBuilder.kt](../data_base/operation/schema/builder/src/main/kotlin/ast/create_table/render_schema_create_table/SchemaRenderCreateTableBuilder.kt) |
| خانوادهٔ constraintهای CREATE TABLE | [SchemaCreateTableDefinitions.kt](../data_base/operation/schema/ast/src/main/kotlin/interfaces/create_table/definition/SchemaCreateTableDefinitions.kt) |
| انتخاب Dialect برای Schema | [DialectSelector.kt](../data_base/operation/schema/renderer/src/main/kotlin/manager/DialectSelector.kt) |
| رندر CREATE TABLE در MySQL | [MySqlSchemaRenderCreateTableCapability.kt](../data_base/operation/schema/renderer/src/main/kotlin/nodes/create_table/render_schema_create_table/MySqlSchemaRenderCreateTableCapability.kt) |
| نقطهٔ اتصال رندر و اجرا | [SchemaExecutionIntegration.kt](../data_base/operation/schema/executor/src/main/kotlin/integration/SchemaExecutionIntegration.kt) |
