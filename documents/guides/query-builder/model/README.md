# تعریف Model برای Query Builder

در Query Builder، یک Model کلاس Kotlinای است که نام جدول و ستون‌ها را به Query Builder معرفی می‌کند. Model کمک می‌کند منبع جدول و propertyهای آن را با reference تایپ‌شده انتخاب کنید؛ خودش رکوردها را بارگذاری یا ذخیره نمی‌کند و از روی کلاس، جدول دیتابیس نمی‌سازد.

## تعریف یک Model

برای معرفی جدول از `@QBTable` و برای معرفی propertyهای قابل استفاده به‌عنوان ستون از `@QBColumn` استفاده کنید. Modelهایی که با ورودی typed مانند `table(Model::class)` به کار می‌روند، `IModelBase` را پیاده‌سازی می‌کنند:

```kotlin
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase

@QBTable(name = "user_users", alias = "uu")
class UserModelV2 : IModelBase {
    @QBColumn(name = "id", alias = "user_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "name", alias = "user_name")
    val name: String? = null

    @QBColumn(name = "family", alias = "user_family")
    val family: String? = null

    @QBColumn(name = "age", alias = "user_age")
    val age: Int? = null
}
```

در این مثال، `QBTable.name` و `QBColumn.name` به‌ترتیب نام جدول و ستون در دیتابیس را مشخص می‌کنند؛ `alias` نام پیش‌فرض source یا خروجی SELECT است. `UserModelV2::id` و بقیهٔ property referenceها را می‌توان در Query به‌کار برد؛ برای نمونه:

```kotlin
import gog.my_project.data_base_query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder

val query = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::id)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::name)
            }
        }
    }
    .from {
        model(UserModelV2::class)
    }
```

همین Model را می‌توان برای DML نیز استفاده کرد. در INSERT و UPDATE، ستون با property reference انتخاب می‌شود و مقدار با `setValue` داده می‌شود:

```kotlin
import gog.my_project.data_base_query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder

val insert = QueryRenderInsertBuilder()
    .table { table(UserModelV2::class) }
    .addValue {
        column(UserModelV2::class, UserModelV2::name)
        setValue {
            paramName("name")
            paramValue("Sara")
        }
    }
```

## نام‌های پیش‌فرض و override

`@QBTable` باید روی کلاس باشد. اگر `name` یا `alias` آن را ننویسید، Query Builder از نام کلاس استفاده می‌کند: پسوند `Model` حذف و نام باقی‌مانده به `snake_case` تبدیل می‌شود. برای مثال، `UserProfileModel` به‌طور پیش‌فرض `user_profile` می‌شود. همین convention برای نام و alias جدول به‌کار می‌رود.

برای هر propertyای که به‌صورت typed به‌عنوان ستون انتخاب می‌شود، `@QBColumn` بگذارید. اگر `name` مشخص نشود، نام Kotlin property نام ستون می‌شود. اگر `alias` مشخص نشود، alias پیش‌فرض نیز نام property است. مقدار صریح Annotation برای همان فیلد جای convention را می‌گیرد.

| Metadata | مقدار پیش‌فرض | Override |
|---|---|---|
| نام جدول | نام کلاس پس از حذف پسوند `Model` و تبدیل به `snake_case` | `QBTable.name` |
| alias جدول | همان convention نام کلاس | `QBTable.alias`، سپس alias صریح در DSL |
| نام ستون | نام Kotlin property | `QBColumn.name` |
| alias پیش‌فرض ستون در SELECT | نام Kotlin property | `QBColumn.alias`، سپس alias صریح خروجی در DSL |

نام جدول و ستون به شیء دیتابیس اشاره می‌کنند؛ alias برای نام‌گذاری source یا خروجی Query است. تغییر alias، نام واقعی جدول یا ستون را عوض نمی‌کند.

## نوع property و خروجی SELECT

برای خروجی property-backed در SELECT، نوع Kotlin در حال حاضر برای `Int`، `Long` و `String` به‌ترتیب به `DataType.INT`، `DataType.LONG` و `DataType.STRING` نگاشت می‌شود. نوع‌های nullable مانند `Int?` نیز همان نوع پایه را دارند. برای نوع‌های دیگر نوع استخراج به‌صورت خودکار تعیین نمی‌شود؛ اگر API خروجی SELECT اجازه دهد، نوع را در همان خروجی صریحاً با `execute(DataType...)` مشخص کنید.

این نوع، قرارداد خواندن خروجی SELECT است؛ نوع ستون دیتابیس را تغییر نمی‌دهد و property کلاس را نیز بازنویسی نمی‌کند. نام property و alias آن را مستقل نگه دارید تا خواندن نتیجه روشن باشد.

## نکته‌های اعتبارسنجی و استفاده

- حذف کامل `@QBTable` یا `@QBColumn` برای metadata موردنیاز خطای اعتبارسنجی می‌دهد.
- برای `QBTable.name` و `QBColumn.name` رشتهٔ خالی معتبر نیست؛ برای استفاده از convention، آرگومان را حذف کنید.
- `QBColumn.alias` خالی مانند alias تنظیم‌نشده به نام property برمی‌گردد. برای خوانایی، alias غیرخالی انتخاب کنید.
- `QBTable.alias` خالی به‌عنوان مقدار صریح نگه داشته می‌شود؛ آن را خالی نگذارید.
- Resolve کردن metadata با reference و Annotation انجام می‌شود؛ Getter property برای خواندن مقدار آن فراخوانی نمی‌شود.
- `primaryKey` یک فیلد در `QBColumn` است؛ Query Builder به‌طور خودکار شرط WHERE یا رفتار CRUD را از آن تولید نمی‌کند. شرط‌ها و ستون‌های موردنیاز را در خود Query مشخص کنید.
- Model بخشی از قرارداد Query Builder است، نه ابزار تعریف Schema؛ برای ساخت یا تغییر جدول از APIهای Schema استفاده کنید.

## مثال‌های مرتبط

- [Modelهای نمونهٔ V2](../examples/v2.0/models.md)
- [SELECT با Model و property reference](../examples/v2.0/select.md)
- [INSERT با Model](../examples/v2.0/insert.md)
- [قواعد Resolve شدن Metadata مدل](../rules/model-metadata.md)
- [فهرست مثال‌های نسخه‌بندی‌شده](../examples/README.md)
