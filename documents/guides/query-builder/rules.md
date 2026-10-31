# Rules

## ساختار Metadata در AST فعلی

Metadata منبع، selector ستون پایه و خروجی SELECT در ساختارهای جدا نگه‌داری می‌شوند:

| ساختار | مقادیر | ترتیب Resolve یا کاربرد |
|---|---|---|
| `QueryTableAstMetadata` | `nameFromModel`، `nameFromAnnotation`، `nameFromAst`؛ و فیلدهای مشابه `aliasFrom...` | `resolveName()` و `resolveAlias()` هر دو AST ← Annotation ← Model |
| `QueryColumnsBaseAstMetadata` | `tableAliasFrom...`، `columnNameFrom...`، `columnAliasFromModel` و `columnAliasFromAnnotation` | alias منبع و نام ستون: AST ← Annotation ← Model؛ alias پیش‌فرض ستون: Annotation ← Model |
| `QueryColumnsAstMetadata` | `ColumnAlias`، `ExecutionType`، `IsExecutionTypeExplicit`، `PropertyReference` و `ColumnMethod` | alias/نوع/هویت خروجی SELECT؛ مستقل از نام و alias منبع |

در منبع derived SELECT، AST نگه‌دارندهٔ `derivedQuery` است و فیلدهای نام منبع خالی می‌مانند؛ alias occurrence جداگانه Resolve می‌شود. `cte(String)` نام CTE را در Metadata منبع می‌گذارد، درحالی‌که `cte(declaration)` هویت relation را در context composition نگه می‌دارد. در API فعلی `execute(DataType)` روی خروجی SELECT (`IQueryColumnsApi`) اعمال می‌شود، نه روی selector ستون پایه؛ مقدار صریح آن فقط `ExecutionType` خروجی را override می‌کند و نوع Property یا Metadata منبع را تغییر نمی‌دهد.

<a id="rules-model"></a>
## Model

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
    column {
        tableSelector(UserModel::class)
        columnSelector(UserModel::id)
    }
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

<a id="rules-query"></a>
## Query

در Query، چند مفهوم مستقل وجود دارند که نباید با یکدیگر ادغام شوند:

- نام منطقی تعریف Query
- نام CTE مورد استفاده در SQL
- alias پیش‌فرض Query
- alias یک occurrence مشخص از CTE در Query
- نام منطقی خروجی Property
- SQL alias خروجی
- نوع داده خروجی

این مقادیر حتی اگر در یک مثال برابر باشند، الزاماً معنای یکسانی ندارند.

### ۲.۱. Metadata سطح کلاس

تعریف Query:

```kotlin
@QBQuery(
    name = "user_display_query_v3",
    alias = "v3_user_display"
)
class UserDisplayQueryV3(
    params: UserDisplayQueryV3.RelationFilters,
) : CteDeclaration<UserDisplayQueryV3.RelationFilters>(params) {
    data class RelationFilters(val userId: Int? = null)
}
```

#### مرحله اول: مقادیر پیش‌فرض

Engine نام‌های اولیه را از قرارداد نام‌گذاری کلاس استخراج می‌کند:

```text
cteName             = user_display_query_v3
defaultOccurrenceAlias = user_display_query_v3
```

در این نام کلاس، نام به `snake_case` تبدیل می‌شود؛ قرارداد حذف پسوند `Query` فقط هنگامی اعمال می‌شود که نام کلاس واقعاً به آن پسوند ختم شود.

#### مرحله دوم: اعمال `QBQuery`

```text
cteName               = user_display_query_v3
defaultOccurrenceAlias = v3_user_display
```

در این مرحله:

- `QBQuery.name` نام پیش‌فرض runtime برای CTE را تعیین می‌کند.
- `QBQuery.alias`، alias پیش‌فرض occurrence منبع را تعیین می‌کند.

این مقادیر مستقل‌اند: نام CTE هویت SQL در `WITH` را مشخص می‌کند و alias فقط source occurrence را نام‌گذاری می‌کند.

#### مرحله سوم: ساخت CTE و اعمال DSL

```kotlin
from {
    cte(UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters()))
    alias("cte_user_display")
}
```

مقادیر نهایی CTE و occurrence در این مثال:

```text
runtime CTE name       = user_display_query_v3
source occurrence alias = cte_user_display
```

توضیح:

- نام runtime CTE از `QBQuery.name` resolve می‌شود؛ در این مسیر declaration نام صریح مستقلی ندارد.
- alias صریح `cte_user_display`، alias همان source occurrence را override می‌کند.
- مقدار `v3_user_display` همچنان alias پیش‌فرض occurrence است، اما در این نمونه به دلیل وجود override صریح DSL استفاده نمی‌شود.

**نکتهٔ نام‌گذاری:** helper ساخت CTE از Declaration، `QBQuery.name` و سپس convention کلاس را به‌کار می‌گیرد. Factoryای که نام runtime را صریحاً دریافت می‌کند، آن نام را بر metadata مقدم می‌کند. `QBQuery.alias` فقط alias پیش‌فرض occurrence است و `alias("...")` در بلوک `from { cte(declaration); ... }` فقط همان occurrence را override می‌کند. `with(cte)` از `Cte.name` استفاده می‌کند؛ `with(withName, cte)` نام صریح caller را برای SQL `WITH` و ارجاع‌های وابستگی مقدم می‌کند، بدون تغییر `Cte.name` یا alias occurrence.

#### ترتیب اولویت در سطح کلاس

| فیلد یا مفهوم | ترتیب Resolve |
|---|---|
| نام پیش‌فرض CTE از Declaration | convention کلاس ← `QBQuery.name` |
| نام CTE در factory دارای نام صریح | convention کلاس ← `QBQuery.name` ← `name` صریح factory |
| نام CTE در `WITH` | `Cte.name` برای `with(cte)` ← `withName` صریح برای `with(withName, cte)` |
| alias پیش‌فرض occurrence | convention کلاس ← `QBQuery.alias` |
| alias occurrence در DSL | alias صریح `from { ... }` ← alias حاصل از metadata |

نام `Cte.name` از نام انتخاب‌شده هنگام ساخت CTE پیروی می‌کند. `with(withName, cte)` نام شیء CTE را تغییر نمی‌دهد؛ فقط نام SQL همان مورد WITH و ارجاع‌های وابسته را با نام صریح caller هماهنگ می‌کند.

#### افزودن CTE به `WITH`

`IQueryWithsItemApi` دو ورودی برای CTE جدید دارد. Declaration ابتدا به یک شیء `Cte` تبدیل می‌شود؛ سپس یکی از دو overload زیر آن را به `WITH` اضافه می‌کند:

~~~kotlin
val declaration = UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters())
val cte = declaration.createCte(declaration.params)

val useCteName = QueryRenderSelectBuilder().withs {
    addWith { with(cte) }
}

val useExplicitName = QueryRenderSelectBuilder().withs {
    addWith { with("published_user_display", cte) }
}
~~~

`with(cte)` از `Cte.name` استفاده می‌کند. `with(withName, cte)` نام صریح را برای تعریف SQL `WITH` به کار می‌برد و آن را در ارجاع‌های dependency نیز هماهنگ می‌کند؛ خود `Cte.name` تغییر نمی‌کند. این نام‌گذاری از `QBQuery.alias` و alias occurrence مستقل است. نام خالی و نام مؤثر تکراری در composition پذیرفته نمی‌شوند. overloadهای legacy `with(name, ICte)` و `with(name, block)` حفظ شده‌اند.

### ۲.۲. Metadata سطح Property

تعریف Property خروجی:

```kotlin
@QBQuerySelect(
    name = "full_name",
    alias = "fullName"
)
val fullName: String?
    get() = error("Typed output property must not be read")
```

Getter عمداً خطا ایجاد می‌کند تا مشخص شود Engine باید Metadata را از Declaration، Reflection و Annotation استخراج کند، نه از خواندن مقدار Property.

#### مرحله اول: مقادیر پیش‌فرض

با فرض اینکه CTE و source occurrence قبلاً Resolve شده باشند:

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = full_name
columnDataType = STRING
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- نام منطقی و SQL alias پیش‌فرض از Property یعنی `fullName` به `snake_case` یعنی `full_name` تبدیل می‌شوند.
- `columnDataType` از نوع تعریف‌شده Property یعنی `String?` استنباط می‌شود و به `STRING` نگاشت می‌شود.

#### مرحله دوم: اعمال `QBQuerySelect`

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = fullName
columnDataType = STRING
```

در این مرحله:

- `QBQuerySelect.name` نام منطقی خروجی Property را تعیین می‌کند.
- `QBQuerySelect.alias`، SQL alias منتشرشدهٔ خروجی را تعیین می‌کند.
- نوع داده همچنان `STRING` باقی می‌ماند.

نام منطقی خروجی و SQL alias دو فیلد مستقل هستند؛ در این مثال `full_name` و `fullName` مقادیر متفاوتی دارند.

#### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    cteOutput(UserDisplayQueryV3::fullName)
    alias("name")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = name
columnDataType = LONG
```

نتیجه:

- نام منطقی خروجی `full_name` باقی می‌ماند؛ زیرا alias صریح خروجی آن را تغییر نمی‌دهد.
- SQL alias خروجی به `name` تغییر می‌کند؛ زیرا alias صریح DSL بر `QBQuerySelect.alias` اولویت دارد.
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
4. نام منطقی، نام منبع، نام CTE، SQL alias و نوع داده مستقل باقی بمانند.
5. یکسان بودن مقدار دو فیلد به معنای یکسان بودن مفهوم آن‌ها نیست.
6. Getter مربوط به Propertyهای خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
7. `relationName` بخشی از قرارداد Query نیست؛ نام runtime پیش‌فرض CTE از `QBQuery.name` و سپس convention مجاز resolve می‌شود، درحالی‌که نام صریح factory بر آن مقدم است.
8. رفتار موجود Model در مواردی که با قواعد جدید Query متفاوت است، حفظ شود.
9. override نوع خروجی از نوع تعریف‌شده Property مستقل باقی بماند.
10. اعتبارسنجی Metadata پس از تکمیل Declaration مربوط به SELECT و پیش از ساخت `BuiltCteComposition` انجام شود.

### الزامات اعتبارسنجی

- اعتبارسنجی یکتایی SQL aliasها حفظ شود.
- یکتایی نام‌های منطقی خروجی در میان خروجی‌های Typed متعلق به قرارداد فعلی Query بررسی شود.
- تعلق یک خروجی به Query بر اساس رابطه موجود `declarationOwner` تعیین شود، نه صرفاً بر اساس کلاسی که Property در آن تعریف شده است.
- Property معتبر به‌ارث‌رسیده نباید صرفاً به دلیل تعریف‌شدن در کلاس والد حذف شود.
- Propertyهای منبع Model نباید به‌عنوان خروجی‌های Query شناسایی شوند.
- برای اعتبارسنجی، مقدار Property نباید خوانده شود.

---

## ۵. سازگاری و محدوده تغییرات

این سند قرارداد Resolve کردن و اولویت‌بندی Metadata را مشخص می‌کند. محدودیت‌های این بخش فقط دامنهٔ تغییرات Metadata را مشخص می‌کنند؛ مسیر AST-based پارامتر و اجرای positional که جداگانه در بالای سند آمده، بخشی مستقل از این قرارداد است.

مگر اینکه یک مانع فنی مشخص، تغییر دیگری را ضروری کند:

- برای تغییرات محدود به Metadata، AST، Executor، `QueryRow`، Materializer و امضاهای API عمومیِ نامرتبط تغییر نکنند.
- در تغییرات محدود به Metadata، قرارداد `Cte` و semantics composition آن تغییر نکند. مهاجرت مستقل و تأییدشدهٔ V3.0 عمداً `ICteDeclaration.params`، constructor پارامترپذیر `CteDeclaration`، `execute(queryManager)` و overload `cte(declaration)` را به ورودی صریح منتقل می‌کند؛ overloadهای `with(Cte)` و `with(withName, Cte)` و معنای نام CTE/alias occurrence حفظ می‌شوند.
- صرفاً برای یکسان‌سازی ظاهری Metadataهای Model و Query، abstraction عمومی جدیدی اضافه نشود.
- رفتار فعلی رشته خالی در Annotationهای Model تغییر نکند.
- وجود `QBQuery.name` نباید نام صریحی را که caller به factory ساخت CTE داده است بازنویسی کند.
- Resolve کردن Metadata از اجرای Query و استخراج مقدار Property مستقل باقی بماند.

هر تغییر در این محدوده‌ها باید جداگانه توجیه و تأیید شود، نه اینکه به‌صورت ضمنی وارد بازنویسی Metadata شود.

---

## ۶. معیارهای پذیرش

پیاده‌سازی زمانی با این قرارداد سازگار است که:

1. مثال کلاس Model به `tableName = user_users` و `tableAlias = uu1` برسد.
2. مثال Property در Model به `columnName = user_id`، `columnAlias = user_id_out` و `columnDataType = LONG` برسد.
3. نام منطقی و alias تعریف‌شده در Annotation مربوط به Query حفظ شوند.
4. مثال Query به `cteName = user_display_query_v3` و `occurrenceAlias = cte_user_display` برسد: نام runtime از `QBQuery.name` و alias از override همان occurrence می‌آید.
5. مثال خروجی Query به نام منطقی `full_name`، SQL alias برابر `name` و نوع خروجی `LONG` برسد.
6. هر override فقط فیلد متناظر خود را تغییر دهد.
7. Getter مربوط به Property خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
8. نام منطقی Query، نام runtime CTE و alias occurrence مستقل باقی بمانند؛ نام `withName` صریح فقط در SQL `WITH` override شود.
9. رفتار موجود Model و APIهای عمومی Query و CTE حفظ شوند.
10. اعتبارسنجی Metadata پیش از ساخت `BuiltCteComposition` انجام شود.
