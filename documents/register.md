# راهنمای سادهٔ مقایسهٔ when و Registry در AST

این راهنما توضیح می‌دهد یک Query Builder چگونه گره‌های AST را به SQL تبدیل می‌کند و دو روش انتخاب renderer برای گره‌ها را با هم مقایسه می‌کند: شاخه‌بندی مرکزی با `when` و استفاده از `Registry`.

در پایان، یک متن آماده و مرتب برای انتشار در لینکدین هم آمده است.

## ۱. یک Query را به شکل AST ببینیم

فرض کنیم می‌خواهیم این Query را بسازیم:

~~~sql
SELECT id
FROM users
WHERE id = :userId
~~~

Query Builder ابتدا آن را به ساختاری از گره‌ها تبدیل می‌کند. درخت زیر ساده‌شده است، اما نام گره‌ها و رابطه‌های اصلی با ساختار فعلی پروژه هماهنگ‌اند:

~~~text
QueryRenderSelectAst
├── select: QuerySelectAst
│   └── columns[]
│       └── QueryColumnsAst
│           └── Column: QueryColumnsBaseAst
│               └── metadata.columnNameFromAst = "id"
├── table: QueryTableAst
│   └── metadata.nameFromAst = "users"
└── where: QueryWhereAst
    └── condition: QueryConditionsGroupsAst
        └── conditions[]
            └── QueryConditionsAst
                ├── conditionSideLeft: QueryColumnsBaseAst
                │   └── metadata.columnNameFromAst = "id"
                ├── metadata.conditionOperation = "="
                └── conditionSideRight: QueryParameterAst
                    └── metadata
                        ├── name = "userId"
                        └── value = 42
~~~

گره‌ها رابطهٔ بین بخش‌های Query را نشان می‌دهند؛ مثلاً اینکه شرط داخل WHERE قرار گرفته و مقدار سمت راست آن پارامتر است. اطلاعاتی مثل نام جدول، نام ستون و مقدار پارامتر هم در `metadata` ذخیره می‌شوند.

جای‌نگهدار `:userId` برای خوانایی مثال انتخاب شده است؛ شکل placeholder نهایی می‌تواند به شیوهٔ رندر پارامتر وابسته باشد.

## ۲. چرا به dispatch نیاز داریم؟

Renderer باید برای هر گره تصمیم بگیرد چه SQLی بسازد. جدول به قاعدهٔ خودش تبدیل می‌شود، شرط به قاعدهٔ خودش و ریشهٔ SELECT هم ترتیب و ترکیب این بخش‌ها را مشخص می‌کند.

دو سؤال جدا وجود دارد:

1. از قواعد SQL کدام دیتابیس استفاده کنیم؟ **انتخاب Dialect**، مثلاً MySQL.
2. برای این گره چه rendererای اجرا شود؟ **انتخاب handler از Registry**.

مسیر کلی:

~~~text
Query DSL
   ↓
Builder و ساخت AST
   ↓
انتخاب Dialect
   ↓
Registry و پیدا کردن renderer گره
   ↓
Renderer و ساخت SQL
~~~

`DialectSelector` مسئول انتخاب Dialect است؛ Registry مسئول پیدا کردن renderer برای گرهٔ AST است. این دو نقش با هم فرق دارند.

## ۳. روش اول: when بزرگ

می‌توان یک تابع مرکزی نوشت که نوع هر گره را بررسی کند:

~~~kotlin
when (node) {
    is SelectNode -> renderSelect(node)
    is TableNode  -> renderTable(node)
    is WhereNode  -> renderWhere(node)
    is JoinNode   -> renderJoin(node)
}
~~~

این مثال مفهومی است و نام کلاس‌ها لزوماً نام کلاس‌های پروژه نیست.

می‌توان این روش را به مسئول پذیرشی تشبیه کرد که همهٔ درخواست‌ها را خودش بررسی می‌کند و به بخش مناسب می‌فرستد. برای تعداد کمی حالت، این روش ساده و روشن است.

اما با زیادشدن انواع گره‌ها، عملیات Query و تفاوت‌های دیتابیس، شاخه‌های بیشتری به همان نقطهٔ مرکزی اضافه می‌شوند. در نتیجه مرور و تغییر آن بخش دشوارتر می‌شود.

## ۴. روش دوم: Registry و rendererهای جدا

Registry را مثل یک دفترچهٔ راهنما در نظر بگیرید:

~~~text
SelectNode → SelectRenderer
TableNode  → TableRenderer
WhereNode  → WhereRenderer
JoinNode   → JoinRenderer
~~~

Registry renderer مربوط به گره را پیدا می‌کند. آن renderer بخش خودش از SQL را می‌سازد و در صورت نیاز rendererهای فرزندانش را هم صدا می‌زند.

~~~text
گرهٔ AST
   ↓
Registry: این گره را کدام renderer می‌سازد؟
   ↓
Renderer همان گره
   ↓
SQL این بخش
~~~

Renderer والد ترتیب اجزا را می‌داند و فرزندانش را برای رندر به Registry می‌سپارد. برای مثال، renderer ریشهٔ SELECT بخش‌هایی مثل WITH، SELECT، FROM، JOIN و WHERE را به rendererهای مربوط می‌فرستد.

**Registry به‌تنهایی کل درخت را نمی‌گردد.** Renderer والد مشخص می‌کند کدام فرزندان و با چه ترتیبی رندر شوند؛ Registry renderer هر گرهٔ ارسال‌شده را پیدا می‌کند.

## ۵. این سازوکار در پروژه

### Query

`MySqlDialect` rendererهای جداگانه‌ای برای ریشهٔ SELECT، جدول، شرط‌ها، JOIN، توابع ستونی، پارامترها و عملیات INSERT، UPDATE و DELETE ثبت می‌کند.

Registry نوع گرهٔ ورودی را با interfaceهای ثبت‌شده تطبیق می‌دهد و renderer مناسب را اجرا می‌کند. Renderer ریشهٔ SELECT نیز اجزای خودش را دوباره به Registry می‌سپارد.

### Schema

Schema هم همین ایده را به‌کار می‌گیرد. برای نمونه، renderer مربوط به CREATE TABLE، جدول، ستون‌ها و constraintها را جداگانه رندر می‌کند و نتیجه را در دستور نهایی کنار هم می‌گذارد.

Dialectها می‌توانند registrationهای متفاوت داشته باشند. در پروژه، `MariaDbDialect` در بخش Schema بعضی rendererهای MySQL را با handlerهای سازگار با قابلیت‌ها و نسخه‌های MariaDB جایگزین می‌کند. در بخش Query، `MariaDbDialect` فعلاً registrationهای `MySqlDialect` را به ارث می‌برد.

## ۶. مقایسهٔ سریع

| پرسش | when مرکزی | Registry |
| --- | --- | --- |
| انتخاب کجا انجام می‌شود؟ | در شاخه‌های یک یا چند تابع مرکزی | در نگاشت نوع گره به renderer |
| افزودن گره چه می‌خواهد؟ | افزودن شاخه و معمولاً تغییر تابع مرکزی | ثبت renderer و وصل‌کردن گره به مسیر رندر والد |
| تفاوت Dialect چطور بیان می‌شود؟ | افزودن شرط یا شاخهٔ Dialect | ثبت renderer مخصوص یا جایگزینی handler |
| چه زمانی ساده‌تر است؟ | وقتی تعداد حالت‌ها کم و ثابت است | وقتی گره‌ها و Dialectها زیاد می‌شوند |

هیچ‌کدام همیشه انتخاب بهتر نیست. برای چند حالت ثابت، `when` می‌تواند روشن‌تر باشد. وقتی تعداد گره‌ها و تفاوت‌های Dialect بیشتر می‌شود، تقسیم مسئولیت بین Registry و rendererهای مستقل می‌تواند نگهداری را ساده‌تر کند.

## ۷. آیا Registry همهٔ whenها را حذف می‌کند؟

خیر. dispatch اصلی بین گره‌های AST عمدتاً از Registry انجام می‌شود، اما بعضی انتخاب‌های محدود و محلی هنوز با شرط‌ها پیاده شده‌اند. برای نمونه:

- renderer گروه شرط‌های Query بررسی می‌کند فرزندش شرط منفرد است یا گروه تو در تو.
- renderer تعریف‌های CREATE TABLE در Schema با `when` بین Primary Key، Unique، Index و Foreign Key انتخاب می‌کند.

توصیف دقیق‌تر این است:

> به‌جای یک شاخه‌بندی بزرگ و مرکزی برای کل AST، انتخاب rendererها عمدتاً به Registry سپرده شده و شرط‌های محلی برای حالت‌های محدود باقی مانده‌اند.

## ۸. چند نکتهٔ فنی دربارهٔ Registry فعلی

در پیاده‌سازی فعلی، کلیدهای Registry از نوع `KClass` هستند و معمولاً interface گره‌های AST ثبت می‌شود. Registry هنگام رندر بررسی می‌کند کدام نوع ثبت‌شده می‌تواند پیاده‌سازی واقعی گره را پوشش دهد.

این روش انعطاف‌پذیر است، ولی چند نکته دارد:

- Registry مدخل‌ها را بررسی می‌کند تا renderer منطبق را پیدا کند؛ بنابراین این جست‌وجو، lookup مستقیم بر اساس کلید دقیق نیست.
- اگر renderer ثبت نشده باشد، خطا هنگام اجرا رخ می‌دهد.
- اگر چند registration با گره منطبق شوند، اولین مدخل منطبق انتخاب می‌شود؛ پس registrationهای مبهم مناسب نیستند.
- افزودن فرزند به AST کافی نیست؛ renderer والد هم باید آن را برای رندر فراخوانی کند.

این موارد ایراد قطعی نیستند؛ مسئولیت ثبت handlerها و مسیر رندر باید روشن و قابل‌ردیابی بماند.

## ۹. متن آمادهٔ پست لینکدین

### از یک Query ساده تا Registry

**یک Query Builder چطور می‌فهمد هر قسمت از Query را به چه SQLی تبدیل کند؟**

فرض کنیم می‌خواهیم این Query را بسازیم:

~~~sql
SELECT id
FROM users
WHERE id = :userId
~~~

درون برنامه، این درخواست ابتدا به درختی از اجزا تبدیل می‌شود:

~~~text
Query
├── SELECT
├── FROM
└── WHERE
    └── شرط: id = پارامتر
~~~

به این ساختار، **AST** می‌گوییم. AST هنوز SQL نهایی نیست؛ نمایش منظمی از معنای Query است.

برای تبدیل آن به SQL می‌توانیم یک `when` بزرگ داشته باشیم که نوع هر گره را بررسی کند:

~~~kotlin
when (node) {
    is SelectNode -> ...
    is TableNode  -> ...
    is WhereNode  -> ...
}
~~~

این روش مثل یک مسئول پذیرش است که همهٔ درخواست‌ها را می‌گیرد و به بخش مناسب می‌فرستد. برای چند حالت محدود ساده است؛ اما با بیشترشدن گره‌ها و تفاوت‌های دیتابیس، فهرست شاخه‌ها هم بزرگ‌تر می‌شود.

راه دیگر استفاده از **Registry** است؛ مثل یک دفترچهٔ راهنما:

~~~text
SelectNode → SelectRenderer
TableNode  → TableRenderer
WhereNode  → WhereRenderer
~~~

Registry renderer مناسب را پیدا می‌کند. آن renderer SQL بخش خودش را می‌سازد و در صورت نیاز rendererهای فرزندان را هم صدا می‌زند.

در پروژهٔ ما ابتدا Dialect دیتابیس انتخاب می‌شود؛ بعد Registry همان Dialect renderer گره‌ها را پیدا می‌کند. این کار مسئولیت ساخت SQL را میان بخش‌های کوچک‌تر تقسیم می‌کند. در بخش Schema نیز بعضی rendererهای MariaDB جای handlerهای MySQL را می‌گیرند تا تفاوت‌های دیتابیس در همان قسمت مدیریت شوند.

Registry خودش SQL نمی‌سازد و به‌تنهایی درخت را هم نمی‌گردد. Renderer والد ترتیب بخش‌ها را مشخص می‌کند و Registry renderer هر گره را پیدا می‌کند. بعضی انتخاب‌های محلی هم همچنان می‌توانند با `when` انجام شوند.

**خلاصه:** `when` مرکزی همهٔ انتخاب‌ها را یک‌جا نگه می‌دارد؛ Registry آن‌ها را بین rendererهای بخش‌های مختلف تقسیم می‌کند. برای ساختارهای بزرگ‌تر و چند Dialect، این تقسیم‌بندی می‌تواند فهم و نگهداری کد را ساده‌تر کند.

#Kotlin #Database #QueryBuilder #AST #SoftwareArchitecture

## ۱۰. مسیرهای کد برای بررسی

| موضوع | فایل |
| --- | --- |
| انتخاب Dialect در Query | [DialectSelector.kt](../data_base/operation/query/renderer/src/main/kotlin/manager/DialectSelector.kt) |
| Registry مربوط به Query | [RendererRegistry.kt](../data_base/operation/query/dialect/src/main/kotlin/manager/RendererRegistry.kt) |
| ثبت rendererهای MySQL برای Query | [MySqlDialect.kt](../data_base/operation/query/renderer/src/main/kotlin/dialects/MySqlDialect.kt) |
| ترکیب اجزای SELECT | [MySqlQueryCapability.kt](../data_base/operation/query/renderer/src/main/kotlin/nodes/select_nodes/query/MySqlQueryCapability.kt) |
| گره‌های شرط Query | [MySqlQueryConditionGroupCapability.kt](../data_base/operation/query/renderer/src/main/kotlin/nodes/select_nodes/condition_group/MySqlQueryConditionGroupCapability.kt) |
| انتخاب Dialect در Schema | [DialectSelector.kt](../data_base/operation/schema/renderer/src/main/kotlin/manager/DialectSelector.kt) |
| ثبت rendererهای MySQL برای Schema | [MySqlDialect.kt](../data_base/operation/schema/renderer/src/main/kotlin/dialects/MySqlDialect.kt) |
| تفاوت registrationهای MariaDB در Schema | [MariaDbDialect.kt](../data_base/operation/schema/renderer/src/main/kotlin/dialects/MariaDbDialect.kt) |
| رندر جدول و constraintها | [MySqlSchemaRenderCreateTableCapability.kt](../data_base/operation/schema/renderer/src/main/kotlin/nodes/create_table/render_schema_create_table/MySqlSchemaRenderCreateTableCapability.kt) |
| شاخه‌بندی محلی definitionهای Schema | [MySqlSchemaCreateTableDefinitionCapability.kt](../data_base/operation/schema/renderer/src/main/kotlin/nodes/create_table/definition/MySqlSchemaCreateTableDefinitionCapability.kt) |
