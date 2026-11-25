# قواعد Metadata در Query و CTE

در Query چند مفهوم مستقل وجود دارد: نام منطقی Declaration، نام CTE در SQL، alias پیش‌فرض Query، alias یک occurrence در FROM، نام منطقی خروجی، SQL alias خروجی و نوع دادهٔ خروجی.

## Metadata سطح کلاس Query

```kotlin
@QBQuery(name = "user_display_query_v3", alias = "v3_user_display")
class UserDisplayQueryV3(
    params: UserDisplayQueryV3.RelationFilters,
) : CteDeclaration<UserDisplayQueryV3.RelationFilters>(params) {
    data class RelationFilters(val userId: Int? = null)
}
```

نام اولیه از convention کلاس ساخته می‌شود. در نمونه، نام کلاس به `user_display_query_v3` تبدیل می‌شود؛ پسوند `Query` فقط وقتی حذف می‌شود که نام کلاس واقعاً به آن ختم شود.

`QBQuery.name` نام پیش‌فرض runtime برای CTE را تعیین می‌کند و `QBQuery.alias` alias پیش‌فرض occurrence منبع را مشخص می‌کند. در ساخت CTE و DSL زیر:

```kotlin
from {
    cte(UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters()))
    alias("cte_user_display")
}
```

نام runtime CTE برابر `user_display_query_v3` و alias همین occurrence برابر `cte_user_display` است. alias صریح DSL مقدار پیش‌فرض `v3_user_display` را فقط برای این occurrence override می‌کند.

| مفهوم | ترتیب Resolve |
|---|---|
| نام پیش‌فرض CTE از Declaration | convention کلاس ← `QBQuery.name` |
| نام CTE در factory نام‌دار | convention کلاس ← `QBQuery.name` ← نام صریح factory |
| نام در `WITH` با `with(cte)` | `Cte.name` |
| نام در `WITH` با `with(withName, cte)` | نام صریح `withName` برای آن SQL WITH و ارجاع‌های وابستگی |
| alias پیش‌فرض occurrence | convention کلاس ← `QBQuery.alias` |
| alias occurrence در DSL | alias صریح همان `from { ... }` ← alias Metadata |

نام صریح factory بر Metadata مقدم است. `with(withName, cte)` خود `Cte.name` یا alias occurrence را تغییر نمی‌دهد. helper ساخت CTE از Declaration، `QBQuery.name` و سپس convention کلاس را به کار می‌گیرد.

نمونهٔ افزودن CTE به WITH:

```kotlin
val declaration = UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters())
val cte = declaration.createCte(declaration.params)

val useCteName = QueryRenderSelectBuilder().withs {
    addWith { with(cte) }
}

val useExplicitName = QueryRenderSelectBuilder().withs {
    addWith { with("published_user_display", cte) }
}
```

نام خالی و نام مؤثر تکراری در composition پذیرفته نمی‌شوند. overloadهای legacy `with(name, ICte)` و `with(name, block)` حفظ شده‌اند.

## Metadata سطح Property خروجی

```kotlin
@QBQuerySelect(name = "full_name", alias = "fullName")
val fullName: String?
    get() = error("Typed output property must not be read")
```

Getter عمداً خطا می‌دهد تا نشان دهد Resolve باید از Declaration، Reflection و Annotation استفاده کند، نه از خواندن مقدار Property.

مقدارهای پیش‌فرض از نام Property می‌آیند: `fullName` به `full_name` تبدیل می‌شود و برای نوع `String?` نوع `STRING` استنباط می‌شود. سپس `QBQuerySelect.name` نام منطقی خروجی و `QBQuerySelect.alias` SQL alias منتشرشده را تعیین می‌کنند.

DSL می‌تواند SQL alias و نوع خروجی را override کند:

```kotlin
addColumn {
    cteOutput(UserDisplayQueryV3::fullName)
    alias("name")
    execute(DataType.LONG)
}
```

خروجی این نمونه نام منطقی `full_name`، SQL alias برابر `name` و نوع خروجی `LONG` دارد. alias، نام منطقی را عوض نمی‌کند و نوع خروجی، نوع Property را بازنویسی نمی‌کند. این مثال فقط اولویت Metadata را نشان می‌دهد؛ `LONG` ممکن است با مقدار واقعی Property از نوع `String?` سازگار نباشد.

| فیلد خروجی | ترتیب Resolve |
|---|---|
| نام منطقی | نام پیش‌فرض Property ← `QBQuerySelect.name` |
| SQL alias | alias پیش‌فرض Property ← `QBQuerySelect.alias` ← alias صریح DSL |
| نوع داده | نوع استنباط‌شده از Property ← نوع صریح DSL با `execute(DataType)` |

## مرز مفاهیم

نام منطقی Query، نام runtime CTE، alias occurrence، نام منطقی خروجی، SQL alias و نوع داده را جدا نگه دارید. برابر بودن رشته‌ها در یک Query به معنای یکی بودن این نقش‌ها نیست. برای قواعد عمومی Resolve به [نقشهٔ Metadata](metadata-resolution.md) مراجعه کنید.
