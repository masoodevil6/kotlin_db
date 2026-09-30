# مسیر عملیاتی‌کردن `data_base:migration:create_table`

این سند، ساختار فعلی `data_base:query` را با زیرساخت migration مقایسه می‌کند و مسیر پیشنهادی برای کامل‌کردن اولین عملیات migration یعنی `CREATE TABLE` را مشخص می‌کند. هدف این است که migration از همان زنجیرهٔ لایه‌ای query استفاده کند:

```text
API → AST → Builder → Dialect/Renderer → Executor → Database
```

در query این زنجیره برای ساختن و اجرای دستورهای SQL به‌کار می‌رود. در migration نیز قراردادها و شکل کلی زنجیره باید هم‌راستا باشد، اما AST و renderer باید مفهوم schema و DDL را مدل کنند؛ مدل select/query نباید مستقیماً بازاستفاده شود.

## الگوی قابل اقتباس از `data_base:query`

| لایه در query | نقش | معادل در migration برای create table |
|---|---|---|
| `query:api` | قراردادهای fluent برای اجزای query | قراردادهای table، column و create-table |
| `query:ast` | interfaceها و داده‌های ساخت‌یافتهٔ query | AST مستقل برای table، column و create-table |
| `query:builder` | پیاده‌سازی API و ساخت AST | builderهایی که بلوک‌های Kotlin را به AST تبدیل کنند |
| `query:dialect` | قرارداد renderer، registry و context | قرارداد renderer و registry برای ASTهای migration |
| `query:renderer` | ثبت rendererهای dialect و تولید SQL | renderer مربوط به MySQL برای table/column/create-table |
| `query:executor` | اتصال builder، renderer و اجرای DB | اجرای migration با executor دیتابیس |
| `query:example` | نمونهٔ end-to-end | نمونهٔ تعریف، render و اجرای create-table |

در query، API، AST، builder و renderer از هم جدا هستند و rendererهای node در registry ثبت می‌شوند. migration نیز همین جداسازی را شروع کرده است؛ اکنون باید قراردادها و اتصال میان لایه‌ها کامل و قابل‌آزمایش شوند. زیرماژول والد `migration` نباید محل پیاده‌سازی جایگزین باشد؛ پیاده‌سازی هر نقش در زیرماژول متناظر آن قرار بگیرد.

## وضعیت فعلی create table

بخش‌های اولیهٔ API، AST، builder، قراردادهای dialect، renderer MySQL و executor وجود دارند. بخشی از زنجیره در این دور کامل شده و بخش اجرای عملیاتی هنوز باقی است:

**تکمیل‌شده:** ناسازگاری نوع AST، پارامترهای `Varchar` و `Decimal`، SQL پایهٔ MySQL، quoting identifier و default literalهای پشتیبانی‌شده، validation اولیهٔ جدول/ستون، DSL عمومی `createTable` و مثال render. مثال اجرای migration را از طریق `MigrationExecutor` به `data_base:manager` واگذار می‌کند و خودش config یا اتصال دیتابیس نمی‌سازد. API ستون نیز helperهای `nullable`, `notNull`, `autoIncrement` و `primaryKey` دارد، executor امکان تزریق `IQueryExecute` را می‌دهد و اجرای موفق DDL دیگر به مقدار Boolean خروجی `Statement.execute()` وابسته نیست.

**باقی‌مانده:** تست‌ها، validation کامل‌تر، اجرای DDL با config/connection قابل تزریق، بررسی نتیجه و خطا در دیتابیس واقعی، transaction و قابلیت‌های بعدی DDL.


## محدودهٔ نسخهٔ اول

برای عملی‌شدن اولین برش، فقط این قابلیت‌ها را کامل کنید:

- ساخت یک جدول با حداقل یک ستون؛
- نوع‌های پایهٔ `Int`، `Boolean`، `Decimal`، `Text`، `Varchar` و `Json`؛
- `NULL` / `NOT NULL`، مقدار پیش‌فرض ثابت، `AUTO_INCREMENT` و کلید اصلی؛
- ساخت SQL معتبر MySQL؛
- validation پیش از render؛
- امکان render مستقل از اتصال دیتابیس؛
- اجرای دستور از مسیر executor و گزارش نتیجه/خطای واقعی؛
- مثال end-to-end و تست‌های واحد renderer و validation.

`ALTER TABLE`، `DROP TABLE`، foreign key، index، اجرای مجموعه‌ای از migrationها، history table و rollback خارج از این برش هستند. ساختارشان بعداً می‌تواند با همان زنجیرهٔ API → AST → Builder → Renderer → Executor توسعه یابد.

## قرارداد پیشنهادی API

API باید نحوهٔ استفاده را تعریف کند، نه SQL را تولید کند. شکل مفهومی DSL می‌تواند این باشد:

```kotlin
createTable {
    table("users")
    column("id", IntType).autoIncrement().primaryKey()
    column("name", VarcharType(120)).notNull()
    column("active", BooleanType).default(true)
}
```

این نمونه قرارداد هدف را نشان می‌دهد؛ نام دقیق متدها می‌تواند با سبک موجود پروژه هماهنگ شود. قراردادهای جدا برای `IMigrationTableApi`، `IMigrationColumnApi` و `IMigrationRenderCreateTableApi` حفظ شوند. API ستون بهتر است وضعیت را با متدهای روشن مثل `nullable()`, `notNull()`, `default(value)`, `autoIncrement()` و `primaryKey()` بیان کند، نه Booleanهای مبهمی مثل `isNullable(false)`.

مقدار پیش‌فرض DDL با پارامتر bind شدهٔ query یکی نیست: placeholderهای `PreparedStatement` معمولاً برای identifier یا بخش‌های ثابت schema قابل استفاده نیستند. برای نسخهٔ اول، مقدار default باید فقط از نوع‌های literal پشتیبانی‌شده render شود و رشته‌ها با قواعد dialect quote شوند. اگر پشتیبانی از عبارت‌هایی مثل `CURRENT_TIMESTAMP` نیاز شد، آن را نوع صریح expression معرفی کنید؛ رشتهٔ ورودی را به‌عنوان SQL خام قبول نکنید.

## مسئولیت هر زیرماژول در این مسیر

### `migration:params`

- نوع‌های دادهٔ مستقل از dialect و پارامترهای واقعی آن‌ها را تعریف کند؛ نمونه: `VarcharType(length: Int)` و `DecimalType(precision: Int, scale: Int)`.
- محدودیت‌های پارامترها را بررسی‌پذیر کند؛ مثل طول مثبت و `scale <= precision`.
- SQL type مخصوص MySQL را در این ماژول hard-code نکند.

### `migration:ast`

- interfaceهای AST و کلاس‌های دادهٔ متناظر را نگه دارد.
- AST ستون نوع را به `MigrationColumnDataType` متصل کند.
- وضعیت‌ها و محدودیت‌ها را به‌صورت دادهٔ ساخت‌یافته ذخیره کند؛ renderer نباید مجبور باشد از روی رشته یا متن SQL معنای schema را حدس بزند.

### `migration:api`

- قرارداد DSL برای table، column و create-table را تعریف کند.
- API باید نوع AST متناظر را از `IMigrationApi<Ast>` بگیرد، همان‌طور که APIهای query قراردادهای node خود را به AST مرتبط می‌کنند.
- جزئیات dialect و اتصال JDBC را وارد API نکند.

### `migration:builder`

- پیاده‌سازی قراردادها را انجام دهد و AST را بسازد.
- builderهای nested باید داده را به AST والد اضافه کنند و فهرست parameterها را بی‌دلیل share نکنند؛ DDL نسخهٔ اول برای default literalها نیازی به query parameter ندارد.
- ساخت `createTable` را به‌عنوان نقطهٔ ورود public فراهم کند؛ فراخواننده نباید کلاس implementation را مستقیماً بسازد.

### `migration:dialect`

- renderer، registry و render context مستقل از MySQL را تعریف کند؛ این جداسازی هم‌نقش `query:dialect` است.
- نوع خروجی render را برای این عملیات روشن کند: SQL معتبر همراه با اطلاعات لازم برای اجرا، یا خطای validation/render با علت مشخص. بازگرداندن `String?` و استفاده از null برای همهٔ خطاها کافی نیست.

### `migration:renderer`

- `MySqlDialect`، انتخاب dialect و rendererهای table، column و create-table را ثبت کند.
- identifierها را با روش quote/escape مخصوص MySQL render کند.
- typeها، defaultها، nullability، primary key و auto-increment را با grammar معتبر MySQL render کند.
- ستون‌ها را ابتدا به رشته‌های معتبر تبدیل کند و سپس با join ویرگول جدا کند؛ ستون نامعتبر نباید بی‌صدا حذف شود.
- validation ساختاری را پیش از تولید SQL انجام دهد و خطای دقیق برگرداند.

### `migration:executor`

- SQL تولیدشده را با executor دیتابیس اجرا کند.
- وابستگی به global `DefaultDatabaseConfig` و ساخت مستقیم `QueryExecute` را پشت وابستگی قابل تزریق (config، connection provider یا executor) قرار دهد.
- خطای اتصال، render و اجرای SQL را از هم قابل‌تشخیص نگه دارد.
- API callback موجود را می‌توان موقتاً حفظ کرد، ولی نتیجه باید دقیقاً یک‌بار و برای موفقیت/شکست هر دو گزارش شود.

### `migration:example`

- DSL واقعی create-table را صدا بزند.
- امکان دیدن SQL renderشده بدون اتصال به DB را داشته باشد.
- اجرای واقعی را پشت config روشن و قابل‌تنظیم انجام دهد تا اجرای مثال به مقادیر پیش‌فرض یا دیتابیس محلی پنهان وابسته نباشد.

نمونه با `:data_base:migration:example:run` SQL را چاپ می‌کند. تابع `executeExampleMigration` اجرای DDL را به `MigrationExecutor` می‌سپارد؛ برنامهٔ میزبان باید config مشترک manager را پیش از فراخوانی آماده کرده باشد.

## مسیر پیاده‌سازی مرحله‌ای

### مرحلهٔ ۱: هم‌ترازکردن مدل و قراردادها

1. ناسازگاری نوع `MigrationColumnAst` و interface آن را رفع کنید.
2. `VarcharType` و `DecimalType` را از `IntType` به پارامترهای عددی واقعی تغییر دهید.
3. پیش‌فرض nullability را روشن و یکدست تعریف کنید؛ پیشنهاد: ستون‌ها به‌صورت پیش‌فرض `NOT NULL` یا `NULL` نباشند تا builder مقدار را صریح کند، یا رفتار پیش‌فرض مستند و ثابت بماند.
4. فهرست دقیق قابلیت‌های نسخهٔ اول و رفتار پیش‌فرض هر constraint را در API تثبیت کنید.

### مرحلهٔ ۲: validation AST

validator مستقلی اضافه کنید که پیش از render بررسی کند:

- جدول نام غیرخالی و حداقل یک ستون دارد؛
- هر ستون نام و type دارد؛
- نام ستون‌ها تکراری نیستند؛
- پارامترهای type در محدودهٔ معتبرند؛
- default با type و nullability ناسازگار نیست؛
- auto-increment فقط روی type عددی مجاز است و ستون مناسب کلید است؛
- قواعد primary key و تعداد کلیدهای مجاز رعایت شده‌اند.

خطاها باید شامل مسیر node (مثلاً `table.users.columns[1]`) و علت باشند تا مصرف‌کننده بتواند ورودی را اصلاح کند.

### مرحلهٔ ۳: renderer MySQL و خروجی مرجع

1. `CREATE TABLE` و quoting identifierهای جدول و ستون را پیاده کنید.
2. render هر type را با پارامتر عددی درست کنید.
3. grammar مربوط به nullability، default، auto-increment و primary key را بسازید.
4. چند ستون را فقط پس از render موفق همهٔ nodeها با `joinToString` کنار هم قرار دهید.
5. SQL مرجع برای مثال ساده را تثبیت کنید، مثلاً:

```sql
CREATE TABLE `users` (
  `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(120) NOT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1
)
```

جزئیات ترتیب constraintها و نوع Boolean باید با پشتیبانی واقعی dialect MySQL و تست‌ها هماهنگ باشد.

### مرحلهٔ ۴: تست renderer و validation

- برای هر type و هر ویژگی ستون تست SQL مستقل بنویسید.
- تست‌های quote/escape نام‌ها و رشته‌های default را اضافه کنید.
- برای جدول یا ستون ناقص و ترکیب constraint نامعتبر، تست خطا بنویسید.
- تست کنید که شکست render هیچ ستون یا constraintی را بی‌صدا حذف نمی‌کند.
- این تست‌ها باید بدون دیتابیس اجرا شوند.

### مرحلهٔ ۵: اتصال builder تا render

- یک factory یا نقطهٔ ورود عمومی برای DSL بسازید.
- تست end-to-end بدون دیتابیس بنویسید: DSL → AST → validation → SQL.
- نمونهٔ Hello Kotlin را با نمونهٔ واقعی create-table جایگزین کنید.

### مرحلهٔ ۶: اجرای DDL

- executor/config/connection provider را قابل تزریق کنید.
- اجرای `CREATE TABLE` را با connection واقعی یا دیتابیس تست انجام دهید.
- callback/result باید برای موفقیت و خطا فراخوانی شود و exception اصلی را حفظ کند.
- اگر executor اجازهٔ استفاده از connection موجود را می‌دهد، مرز مالکیت و بستن connection را مستند کنید.
- رفتار transaction را مطابق dialect و driver تعیین و مستند کنید؛ فرض نکنید همهٔ DDLها در همهٔ دیتابیس‌ها rollback می‌شوند.

### مرحلهٔ ۷: معیار پذیرش نسخهٔ اول

نسخهٔ اول create-table زمانی آماده است که:

1. پروژه و ماژول‌های migration کامپایل شوند.
2. DSL مثال، AST کامل بسازد.
3. ورودی نامعتبر پیش از اجرا با خطای قابل‌فهم رد شود.
4. SQL تولیدشده با SQL مرجع و تست‌های MySQL سازگار باشد.
5. اجرای موفق یک جدول بسازد و اجرای ناموفق خطای واقعی DB را گزارش کند.
6. تست‌های واحد مستقل از DB همیشه اجرا شوند و تست integration فقط با config مشخص اجرا شود.

پس از این معیار، عملیات‌های `drop_table` و `alter_table` را به‌صورت nodeهای جدید در همان لایه‌ها اضافه کنید. سپس `MigrationRunner`، تاریخچهٔ اجرا و rollback را به‌عنوان قابلیت جداگانه طراحی کنید؛ اجرای یک دستور DDL به‌تنهایی هنوز مدیریت چرخهٔ عمر migration نیست.

## مرز اقتباس از query

از query باید جداسازی مسئولیت‌ها، قراردادهای interfaceمحور، registry/context برای rendererهای dialect و زنجیرهٔ builder تا executor را اقتباس کرد. خود AST query، قرارداد پارامترهای query یا رندر placeholderها را برای schema کپی نکنید. در DDL، identifier و تعریف type ساختار دستور هستند و باید توسط dialect ساخته و escape شوند؛ پارامتر bind فقط برای مقدارهای اجرایی مناسب است، نه نام جدول و ستون.
