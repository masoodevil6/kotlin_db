# ساخت و استفاده از CTE

CTE یک Query موقت و نام‌دار در عبارت `WITH` است. در Query Builder می‌توانید آن را مستقیم با DSL بسازید یا با یک declaration تایپ‌شده تعریف کنید. CTE بخشی از همان SELECT است؛ جدول یا داده‌ای در دیتابیس ایجاد نمی‌کند.

این راهنما API فعلی `Cte` و `CteDeclaration` را توضیح می‌دهد. مثال‌های کامل‌تر در [راهنمای مثال‌های نسخه‌بندی‌شده](../examples/README.md) و [نمونهٔ افزودن CTE به WITH در V3.0](../examples/v3.0/with.md) آمده‌اند.

## ساخت CTE مستقیم با DSL

برای CTEای که خروجی‌هایش را با aliasهای SQL مصرف می‌کنید، از `cte(name) { ... }` استفاده کنید. هر ستون خروجی باید alias غیرخالی و یکتا داشته باشد:

```kotlin
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder

val activeUsers = cte("active_users") {
    select {
        addColumn {
            column {
                tableSelector("u")
                columnSelector("id")
            }
            alias("user_id")
            execute(DataType.INT)
        }
        addColumn {
            column {
                tableSelector("u")
                columnSelector("name")
            }
            alias("user_name")
            execute(DataType.STRING)
        }
    }
    from {
        table("user_users")
        alias("u")
    }
}

val query = QueryRenderSelectBuilder()
    .withs {
        addWith { with(activeUsers) }
    }
    .from {
        cte(activeUsers.name)
        alias("active")
    }
    .select {
        addColumn {
            column {
                tableSelector("active")
                columnSelector("user_id")
            }
            alias("id")
            execute(DataType.INT)
        }
        addColumn {
            column {
                tableSelector("active")
                columnSelector("user_name")
            }
            alias("name")
            execute(DataType.STRING)
        }
    }
```

`with(activeUsers)` شیء CTE را به `WITH` اضافه می‌کند و از `activeUsers.name` به‌عنوان نام SQL استفاده می‌کند. سپس `from { cte(activeUsers.name) }` آن را به‌عنوان source انتخاب می‌کند و `alias("active")` نام همان occurrence را در Query بیرونی تعیین می‌کند. در این شکل، ستون‌ها با aliasهای SQL انتخاب می‌شوند.

شکل کلی SQL حاصل چنین است؛ quoting و جزئیات نهایی را Renderer و Dialect تعیین می‌کنند:

```sql
WITH active_users AS (
    SELECT u.id AS user_id, u.name AS user_name
    FROM user_users u
)
SELECT active.user_id AS id, active.user_name AS name
FROM active_users active
```

اگر نام SQL را برای استفاده در `WITH` جداگانه انتخاب می‌کنید، از `with(withName, cte)` استفاده کنید و همان نام را در `from { cte(withName) }` بدهید:

```kotlin
val queryWithCustomName = QueryRenderSelectBuilder()
    .withs {
        addWith { with("published_users", activeUsers) }
    }
    .from {
        cte("published_users")
        alias("active")
    }
    // select { ... }
```

نام صریح فقط نام SQL برای این composition است؛ مقدار `activeUsers.name` را تغییر نمی‌دهد. نام CTE و alias occurrence دو مفهوم جدا هستند.

## CTE به‌عنوان source مستقیم

`from { cte(cteObject) }` نیز پذیرفته می‌شود، اما این overload، تعریف SELECT را به‌صورت source مشتق‌شده در `FROM` قرار می‌دهد. برای ساخت صریح عبارت نام‌دار `WITH` از الگوی `withs { ... }` به‌همراه `from { cte(name) }` استفاده کنید.

این تفاوت در SQL نهایی مهم است: اولی source مشتق‌شده است؛ دومی نام را در `WITH` تعریف می‌کند و بعد با همان نام به آن ارجاع می‌دهد. در هر دو حالت Query با مسیر معمول Query Builder اجرا می‌شود.

## Declaration تایپ‌شده

وقتی می‌خواهید خروجی CTE را با property reference انتخاب کنید، یک declaration بسازید که از `CteDeclaration<Parameters, Self>` ارث ببرد. `@QBQuery` نام و alias پیش‌فرض declaration را معرفی می‌کند؛ `@QBQuerySelect` نام منطقی و SQL alias هر خروجی را مشخص می‌کند.

نمونهٔ زیر از Model موجود `UserModelV4` استفاده می‌کند:

```kotlin
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.cte.declaration.CteDeclaration
import gog.my_project.data_base_query.example.v4.queries.models.UserModelV4

@QBQuery(name = "active_users", alias = "active_users")
class ActiveUsersQuery : CteDeclaration<ActiveUsersQuery.Filters, ActiveUsersQuery>() {
    data class Filters(val minAge: Int? = null)

    @QBQuerySelect(name = "id", alias = "user_id")
    var id: Int = 0

    @QBQuerySelect(name = "name", alias = "user_name")
    var name: String? = null

    override fun createCte(params: Filters): Cte = buildCte {
        select {
            addColumn {
                column {
                    tableSelector(UserModelV4::class)
                    columnSelector(UserModelV4::id)
                }
                alias(ActiveUsersQuery::id)
            }
            addColumn {
                column {
                    tableSelector(UserModelV4::class)
                    columnSelector(UserModelV4::name)
                }
                alias(ActiveUsersQuery::name)
            }
        }
        from { model(UserModelV4::class) }

        params.minAge?.let { minAge ->
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserModelV4::class)
                            columnSelector(UserModelV4::age)
                        }
                        operationGreaterThanOrEqual()
                        sideValueParam {
                            paramName("minimum_age")
                            paramValue(minAge)
                        }
                    }
                }
            }
        }
    }
}
```

`createCte(params)` با پارامترهای همان فراخوانی، تعریف CTE را می‌سازد. شرط اختیاری در مثال فقط وقتی `minAge` مقدار دارد به AST اضافه می‌شود. مقادیر ورودی شرط با `sideValueParam` bind می‌شوند؛ آن‌ها را با الحاق String به SQL وارد نکنید.

### استفاده از خروجی تایپ‌شده در Query دیگر

```kotlin
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder

val relation = ActiveUsersQuery().createCte(
    ActiveUsersQuery.Filters(minAge = 18),
)

val query = QueryRenderSelectBuilder()
    .withs {
        addWith { with(relation) }
    }
    .from {
        cte(relation.name)
        alias("active")
    }
    .select {
        addColumn {
            column {
                tableSelector(ActiveUsersQuery::class)
                columnSelector(ActiveUsersQuery::id)
            }
            alias("id")
            execute(DataType.INT)
        }
        addColumn {
            column {
                tableSelector(ActiveUsersQuery::class)
                columnSelector(ActiveUsersQuery::name)
            }
            alias("name")
            execute(DataType.STRING)
        }
    }
```

در اینجا `relation.name` نام SQL در `WITH` و `active` alias منبع در Query بیرونی است. `tableSelector(ActiveUsersQuery::class)` و `columnSelector(ActiveUsersQuery::property)` هویت declaration و خروجی را به Builder می‌دهند؛ aliasهای `id` و `name` در SELECT بیرونی نام خروجی همین Query هستند.

## اجرای مستقیم Declaration

`CteDeclaration` علاوه بر استفاده در `WITH`، متد `execute(queryManager, params)` دارد. این متد SELECT داخل declaration را از مسیر معمول executor اجرا و ردیف‌ها را به `ICollection<Self>` تبدیل می‌کند:

```kotlin
import gog.my_project.collection.ICollection
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.toCollection

fun loadActiveUsers(queryManager: IQueryBuilderExecutor): ICollection<ActiveUsersQuery> =
    ActiveUsersQuery()
        .execute(queryManager, ActiveUsersQuery.Filters(minAge = 18))
        .toCollection()
```

برای mapping پیش‌فرض، خروجی‌های `@QBQuerySelect` باید propertyهای `var` با setter عمومی باشند؛ declaration باید constructor بدون آرگومان داشته باشد. Mapper پیش‌فرض نوع‌های `Int`، `Long` و `String` و شکل nullable آن‌ها را پشتیبانی می‌کند. اگر mapping دیگری لازم دارید، `fromQueryRow(row)` در declaration قابل override است.

اجرای مستقیم declaration همان SELECT را اجرا می‌کند؛ به‌خودی‌خود آن را در یک `WITH` بیرونی قرار نمی‌دهد. برای استفاده از آن به‌عنوان CTE یک Query دیگر، `createCte(params)` را بگیرید و در Query بیرونی composition را بسازید.

## وابستگی بین CTEها

یک CTE می‌تواند به CTE دیگری وابسته باشد. وقتی در تعریف CTE از relationهای `Cte` استفاده می‌کنید و آن‌ها را در `withs` وارد composition می‌کنید، Builder وابستگی‌ها را پیش از CTE مصرف‌کننده در عبارت `WITH` می‌آورد. برای ارجاع به CTE با نام SQL، نامی را به کار ببرید که در `with(...)` مؤثر شده است.

در `with(cte)` نام خود relation استفاده می‌شود؛ در `with(withName, cte)` نام صریح برای SQL `WITH` و ارجاع‌های وابسته به کار می‌رود. نام خالی رد می‌شود، اما Builder یکتایی نام‌های مؤثر `WITH` را در همهٔ ترکیب‌ها تضمین نمی‌کند؛ نام‌های تکراری ممکن است به SQL نامعتبر یا ارجاع مبهم منجر شوند، پس نام‌های CTE را یکتا انتخاب کنید. alias occurrence که در `from { ...; alias("...") }` تعیین می‌کنید جدا باقی می‌ماند.

## اعتبارسنجی و خطاهای رایج

- CTE باید دست‌کم یک خروجی SELECT داشته باشد.
- هر خروجی باید SQL alias غیرخالی داشته باشد و aliasهای خروجی یک CTE نباید تکراری باشند.
- در declaration تایپ‌شده، نام‌های منطقی خروجی متعلق به همان declaration نیز باید یکتا باشند.
- نام CTE و alias source نباید خالی باشند.
- در mapping پیش‌فرض خروجی تایپ‌شده، نوع property باید از نوع‌های پشتیبانی‌شده باشد و مقدار SQL با نوع و nullability آن سازگار باشد.
- برای `@QBQuerySelect`، alias منتشرشده در SQL باید با alias خروجی SELECT تطبیق داشته باشد تا mapper بتواند مقدار را به property درست بنویسد.

APIهای قدیمی مانند `with(name, ICte)` و `with(name, block)` هنوز در interface وجود دارند؛ مثال‌های جدید این راهنما بر `Cte` و `CteDeclaration` تمرکز دارند.

## پیوندهای مرتبط

- [راهنمای ساخت Model](../model/README.md)
- [قواعد Metadata مربوط به Query](../rules/query-metadata.md)
- [مثال‌های CTE در V3.0](../examples/v3.0/README.md)
- [اجرای مستقیم typed CTE در V3.1](../examples/v3.1/typed-cte-execution.md)
- [راهنمای اجرای Query و نتیجه](../executor/README.md)
