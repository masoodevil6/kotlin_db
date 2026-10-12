# قواعد Resolve کردن و اولویت‌بندی Metadata

## مقدمه

فرآیند Resolve کردن Metadata مشخص می‌کند که هنگام ساخت Query، چه مقادیری برای نام جدول یا Relation، alias، نام ستون و نوع داده استفاده شوند.

این فرآیند از سه مرحله تشکیل می‌شود:

1. **مقادیر پیش‌فرض:** مقادیر اولیه بر اساس نام کلاس، نام Property و نوع تعریف‌شده Property ساخته می‌شوند.
2. **اعمال Annotation:** مقادیر تعریف‌شده در Annotationهای کلاس و Property، مقادیر پیش‌فرض متناظر را override می‌کنند.
3. **اعمال تنظیمات صریح DSL:** تنظیمات صریح هنگام ساخت Query، در فیلدهایی که برای تغییر آن‌ها تعریف شده‌اند، بر Metadata قبلی اولویت دارند.

این مراحل برای هر فیلد به‌صورت مستقل اجرا می‌شوند. تغییر یک فیلد نباید باعث تغییر ناخواسته فیلدهای دیگر شود.

قواعد `Model` و `Query` از نظر ساختار کلی مشابه‌اند، اما معنای فیلدها و رفتار موجود آن‌ها باید مستقل باقی بماند.

## فهرست

- [Model](#۱-metadata-مربوط-به-model)
- [Query](#۲-metadata-مربوط-به-query)

---

## ۱. Metadata مربوط به Model

### ۱.۱. Metadata سطح کلاس

تعریف Model:

```kotlin
@QBTable(name = "user_users", alias = "uu")
class UserModel : IModelBase
```

#### مرحله اول: مقادیر پیش‌فرض

Engine مقادیر اولیه را از نام کلاس استخراج می‌کند:

```text
tableName  = user
tableAlias = user
```

در این مثال، پسوند انتهایی `Model` از نام کلاس حذف و نام باقی‌مانده به `snake_case` تبدیل می‌شود.

#### مرحله دوم: اعمال `QBTable`

مقادیر Annotation جایگزین مقادیر پیش‌فرض متناظر می‌شوند:

```text
tableName  = user_users
tableAlias = uu
```

- `QBTable.name` نام جدول منبع را مشخص می‌کند.
- `QBTable.alias`، alias پیش‌فرض جدول را مشخص می‌کند.

#### مرحله سوم: اعمال تنظیمات DSL

```kotlin
table {
    table(UserModel::class)
    alias("uu1")
}
```

مقادیر نهایی:

```text
tableName  = user_users
tableAlias = uu1
```

alias صریح DSL بر alias تعریف‌شده در Annotation اولویت دارد، اما نام جدول را تغییر نمی‌دهد.

#### ترتیب اولویت در سطح کلاس

| فیلد | ترتیب Resolve |
|---|---|
| `tableName` | نام پیش‌فرض حاصل از کلاس ← `QBTable.name` |
| `tableAlias` | alias پیش‌فرض ← `QBTable.alias` ← alias صریح DSL |

در این مثال، چون override مستقیمی برای `tableName` در DSL وجود ندارد، مقدار Annotation حفظ می‌شود.

### ۱.۲. Metadata سطح Property

تعریف Property:

```kotlin
@QBColumn(
    name = "user_id",
    alias = "user_id",
    primaryKey = true
)
val id: Int = 0
```

فرض می‌کنیم Metadata جدول قبلاً Resolve شده است.

#### مرحله اول: مقادیر پیش‌فرض

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = id
columnAlias    = id
columnDataType = INT
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- `columnName` از نام Property گرفته می‌شود.
- `columnAlias` به‌صورت پیش‌فرض برابر نام Property است.
- `columnDataType` از نوع تعریف‌شده Property، یعنی `Int`، استنباط می‌شود.

#### مرحله دوم: اعمال `QBColumn`

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = user_id
columnAlias    = user_id
columnDataType = INT
```

در این مرحله:

- `QBColumn.name` نام ستون منبع را override می‌کند.
- `QBColumn.alias`، alias پیش‌فرض ستون را override می‌کند.
- نوع داده همچنان `INT` باقی می‌ماند، زیرا Annotation در این مثال نوع داده را تغییر نمی‌دهد.

همچنین Metadata مربوط به `primaryKey = true` باید مستقل از نام و alias ستون حفظ شود.

#### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    column(UserModel::class, UserModel::id)
    alias("user_id_out")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = user_id
columnAlias    = user_id_out
columnDataType = LONG
```

نتیجه:

- `columnName` برابر `user_id` باقی می‌ماند.
- `columnAlias` به `user_id_out` تغییر می‌کند.
- `columnDataType` به دلیل فراخوانی `execute(DataType.LONG)` برابر `LONG` می‌شود.

تغییر نوع خروجی Query نباید نوع تعریف‌شده Property یا Metadata اصلی Model را تغییر دهد.

#### ترتیب اولویت در سطح Property

| فیلد | ترتیب Resolve |
|---|---|
| `columnName` | نام پیش‌فرض Property ← `QBColumn.name` |
| `columnAlias` | alias پیش‌فرض Property ← `QBColumn.alias` ← alias صریح DSL |
| `columnDataType` | نوع استنباط‌شده از Property ← نوع صریح DSL از طریق `execute(DataType)` |

---

## ۲. Metadata مربوط به Query

در Query، چند مفهوم مستقل وجود دارند که نباید با یکدیگر ادغام شوند:

- نام منطقی تعریف Query
- نام Relation مورد استفاده در SQL
- alias پیش‌فرض Query
- alias یک نمونه مشخص از Relation در Query
- نام منطقی خروجی Property
- SQL alias خروجی
- نوع داده خروجی

این مقادیر حتی اگر در یک مثال برابر باشند، الزاماً معنای یکسانی ندارند.

### ۲.۱. Metadata سطح کلاس

تعریف Query:

```kotlin
@QBQuery(
    name = "v3_user_display",
    alias = "uud"
)
class UserDisplayQuery :
    QueryDeclaration<UserDisplayQueryV3.RelationFilters>()
```

#### مرحله اول: مقادیر پیش‌فرض

Engine مقادیر اولیه را از نام کلاس استخراج می‌کند:

```text
tableName  = user_display
tableAlias = user_display
```

در این مثال، فقط پسوند انتهایی `Query` حذف و نام باقی‌مانده به `snake_case` تبدیل می‌شود.

#### مرحله دوم: اعمال `QBQuery`

```text
tableName  = v3_user_display
tableAlias = uud
```

در این مرحله:

- `QBQuery.name` نام منطقی تعریف Query را تعیین می‌کند.
- `QBQuery.alias`، alias پیش‌فرض Query را تعیین می‌کند.

این مقادیر به تعریف Query تعلق دارند و لزوماً نام و alias نهایی تمام Relationهایی نیستند که از آن ساخته می‌شوند.

#### مرحله سوم: ساخت Relation و اعمال DSL

```kotlin
.table(
    table(
        UserDisplayQuery().queryRelation(
            UserDisplayQueryV3.RelationFilters()
        )
    )
    alias("cte_user_display")
)
```

مقادیر نهایی Relation در این مثال:

```text
    tableName  = v3_user_display
    tableAlias = cte_user_display
```

توضیح:

- نام runtime Query یعنی `v3_user_display` از `QBQuery.name` resolve می‌شود؛ در این مسیر declaration نام صریح مستقلی ندارد.
- alias صریح `cte_user_display`، alias همان نمونه از Relation را override می‌کند.
- مقدار `uud` همچنان alias پیش‌فرض تعریف Query است، اما در این نمونه به دلیل وجود override صریح DSL استفاده نمی‌شود.

**نکته مهم:** نام runtime از `QBQuery.name` می‌آید، مگر آنکه caller نام صریحی مستقیماً به factory بدهد؛ نام صریح factory مقدم است. `QBQuery.alias` فقط alias پیش‌فرض occurrence است و alias صریح `from(query, alias)` همان occurrence را override می‌کند.

#### ترتیب اولویت در سطح کلاس

| فیلد یا مفهوم | ترتیب Resolve |
|---|---|
| نام منطقی Query | نام پیش‌فرض حاصل از کلاس ← `QBQuery.name` |
| نام runtime Query | نام صریح factory → `QBQuery.name` → convention مجاز |
| alias پیش‌فرض Query | alias پیش‌فرض ← `QBQuery.alias` |
| alias نمونه Relation | alias صریح DSL ← alias حاصل از Metadata، با حفظ رفتار فعلی در نبود override |

نام metadata در `QBQuery.name` و نام runtime `QueryRelation.name` دو نقش مستقل دارند: در مسیر declaration نام runtime از `QBQuery.name` می‌آید، اما factoryای که caller مستقیماً با نام صریح فراخوانی کند می‌تواند نام runtime متفاوتی تعیین کند.

### ۲.۲. Metadata سطح Property

تعریف Property خروجی:

```kotlin
@QBQuerySelect(
    name = "user_name",
    alias = "user_name"
)
val fullName: String?
    get() = error("Typed output property must not be read")
```

Getter عمداً خطا ایجاد می‌کند تا مشخص شود Engine باید Metadata را از Declaration، Reflection و Annotation استخراج کند، نه از خواندن مقدار Property.

#### مرحله اول: مقادیر پیش‌فرض

با فرض اینکه Relation قبلاً Resolve شده باشد:

```text
tableName      = user_display
tableAlias     = cte_user_display
-------------------------
columnName     = full_name
columnAlias    = full_name
columnDataType = STRING
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- `columnName` از نام Property یعنی `fullName` استخراج و به `full_name` تبدیل می‌شود.
- `columnAlias` به‌صورت پیش‌فرض برابر نام Property است.
- `columnDataType` از نوع تعریف‌شده Property یعنی `String?` استنباط می‌شود و به `STRING` نگاشت می‌شود.

#### مرحله دوم: اعمال `QBQuerySelect`

```text
tableName      = user_display
tableAlias     = cte_user_display
-------------------------
columnName     = user_name
columnAlias    = user_name
columnDataType = STRING
```

در این مرحله:

- `QBQuerySelect.name` نام منطقی خروجی Property را تعیین می‌کند.
- `QBQuerySelect.alias`، SQL alias پیش‌فرض خروجی را تعیین می‌کند.
- نوع داده همچنان `STRING` باقی می‌ماند.

نام منطقی خروجی و SQL alias دو فیلد مستقل هستند، حتی اگر در این مثال هر دو برابر `user_name` باشند.

#### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    column(UserDisplayQuery::class, UserDisplayQuery::fullName)
    alias("name")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
tableName      = user_display
tableAlias     = cte_user_display
-------------------------
columnName     = user_name
columnAlias    = name
columnDataType = LONG
```

نتیجه:

- `columnName` برابر `user_name` باقی می‌ماند؛ زیرا نام منطقی خروجی از `QBQuerySelect.name` آمده است.
- `columnAlias` به `name` تغییر می‌کند؛ زیرا alias صریح DSL بر `QBQuerySelect.alias` اولویت دارد.
- `columnDataType` به `LONG` تغییر می‌کند؛ زیرا `execute(DataType.LONG)` نوع خروجی را صریحاً تعیین می‌کند.

این تغییر نوع فقط به خروجی Query مربوط است و نباید نوع تعریف‌شده Property یا Metadata اصلی Query را تغییر دهد.

**توجه:** این مثال صرفاً برای توضیح اولویت‌بندی Metadata است. با توجه به اینکه Property از نوع `String?` است، تعیین `DataType.LONG` لزوماً با نوع واقعی مقدار خروجی سازگار نیست. بررسی سازگاری نوع داده موضوعی مستقل از اولویت‌بندی Metadata است.

Engine همچنین نباید Getter مربوط به `fullName` را برای Resolve کردن Metadata فراخوانی کند.

#### ترتیب اولویت در سطح Property

| فیلد | ترتیب Resolve |
|---|---|
| نام منطقی خروجی | نام پیش‌فرض Property ← `QBQuerySelect.name` |
| SQL alias خروجی | alias پیش‌فرض Property ← `QBQuerySelect.alias` ← alias صریح DSL |
| نوع داده خروجی | نوع استنباط‌شده از Property ← نوع صریح DSL از طریق `execute(DataType)` |

تغییر SQL alias نباید نام منطقی خروجی را تغییر دهد. به همین ترتیب، تغییر نوع خروجی نباید نوع تعریف‌شده Property را بازنویسی کند.

---

## ۳. تفاوت مقدار تعریف‌نشده و رشته خالی

مقدار تعریف‌نشده و رشته خالی الزاماً معنای یکسانی ندارند.

برای فیلدهای جدید Annotationهای Query، در صورت نیاز به تشخیص این دو حالت، می‌توان از یک مقدار قراردادی مانند `QB_METADATA_UNSET` استفاده کرد.

- **تعریف‌نشده:** مقداری برای فیلد ارائه نشده است؛ بنابراین مقدار پیش‌فرض یا fallback متناظر قابل استفاده است.
- **رشته خالی صریح:** مقدار فیلد عمداً خالی تعیین شده است؛ اگر آن فیلد الزاماً باید مقدار غیرخالی داشته باشد، باید اعتبارسنجی آن را رد کند.

رفتار فعلی Annotationهای Model باید حفظ شود. به‌ویژه، مقادیر پیش‌فرض رشته خالی در `QBTable` و `QBColumn` نباید صرفاً برای یکسان‌سازی با Query تغییر کنند.

استفاده از `QB_METADATA_UNSET` نباید بدون بررسی سازگاری به فیلدهای موجود Model تعمیم داده شود.

---

## ۴. قواعد عمومی Resolve کردن Metadata

قواعد زیر برای فرآیند Resolve کردن Metadata اعمال می‌شوند:

1. مقادیر پیش‌فرض پیش از Annotationها تعیین شوند.
2. هر Annotation فقط فیلدهای متناظر خود را تغییر دهد.
3. تنظیمات صریح DSL در مرحله آخر اعمال شوند و فقط فیلدهایی را تغییر دهند که آن عملیات برای override کردنشان تعریف شده است.
4. نام منطقی، نام منبع، نام Relation، SQL alias و نوع داده مستقل باقی بمانند.
5. یکسان بودن مقدار دو فیلد به معنای یکسان بودن مفهوم آن‌ها نیست.
6. Getter مربوط به Propertyهای خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
7. `relationName` بخشی از قرارداد Query نیست؛ نام runtime از precedence تعریف‌شده در Plan 10.4 resolve می‌شود و `QueryRelation` runtime type حفظ می‌شود.
8. رفتار موجود Model در مواردی که با قواعد جدید Query متفاوت است، حفظ شود.
9. override نوع خروجی از نوع تعریف‌شده Property مستقل باقی بماند.
10. اعتبارسنجی Metadata پس از تکمیل Declaration مربوط به SELECT و پیش از ساخت `BuiltQueryDefinition` انجام شود.

### الزامات اعتبارسنجی

- اعتبارسنجی یکتایی SQL aliasها حفظ شود.
- یکتایی نام‌های منطقی خروجی در میان خروجی‌های Typed متعلق به قرارداد فعلی Query بررسی شود.
- تعلق یک خروجی به Query بر اساس رابطه موجود `declarationOwner` تعیین شود، نه صرفاً بر اساس کلاسی که Property در آن تعریف شده است.
- Property معتبر به‌ارث‌رسیده نباید صرفاً به دلیل تعریف‌شدن در کلاس والد حذف شود.
- Propertyهای منبع Model نباید به‌عنوان خروجی‌های Query شناسایی شوند.
- برای اعتبارسنجی، مقدار Property نباید خوانده شود.

---

## ۵. سازگاری و محدوده تغییرات

این سند قرارداد Resolve کردن و اولویت‌بندی Metadata را مشخص می‌کند و به‌خودی‌خود مجوز تغییر سایر بخش‌های معماری Query Builder نیست.

مگر اینکه یک مانع فنی مشخص، تغییر دیگری را ضروری کند:

- AST، Executor، `QueryRow`، Materializer و امضاهای API عمومی تغییر نکنند.
- API موجود `QueryRelation` حفظ شود.
- صرفاً برای یکسان‌سازی ظاهری Metadataهای Model و Query، abstraction عمومی جدیدی اضافه نشود.
- رفتار فعلی رشته خالی در Annotationهای Model تغییر نکند.
- وجود `QBQuery.name` به‌تنهایی باعث تغییر قرارداد نام Relation نشود.
- Resolve کردن Metadata از اجرای Query و استخراج مقدار Property مستقل باقی بماند.

هر تغییر در این محدوده‌ها باید جداگانه توجیه و تأیید شود، نه اینکه به‌صورت ضمنی وارد بازنویسی Metadata شود.

---

## ۶. معیارهای پذیرش

پیاده‌سازی زمانی با این قرارداد سازگار است که:

1. مثال کلاس Model به `tableName = user_users` و `tableAlias = uu1` برسد.
2. مثال Property در Model به `columnName = user_id`، `columnAlias = user_id_out` و `columnDataType = LONG` برسد.
3. نام منطقی و alias تعریف‌شده در Annotation مربوط به Query حفظ شوند.
4. مثال Query به `tableName = v3_user_display` و `tableAlias = cte_user_display` برسد: نام runtime از `QBQuery.name` و alias از override همان occurrence می‌آید.
5. مثال خروجی Query به نام منطقی `user_name`، SQL alias برابر `name` و نوع خروجی `LONG` برسد.
6. هر override فقط فیلد متناظر خود را تغییر دهد.
7. Getter مربوط به Property خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
8. نام منطقی Query و نام Relation مستقل باقی بمانند.
9. رفتار موجود Model و APIهای عمومی Query حفظ شوند.
10. اعتبارسنجی Metadata پیش از ساخت `BuiltQueryDefinition` انجام شود.
