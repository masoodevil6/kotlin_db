# قواعد Metadata مدل

Metadata مدل دو سطح دارد: Metadata جدول در کلاس و Metadata ستون در Property. نمونه‌های این صفحه از قراردادهای `QBTable` و `QBColumn` استفاده می‌کنند.

## Metadata سطح کلاس

```kotlin
@QBTable(name = "user_users", alias = "uu")
class UserModel : IModelBase
```

نام پیش‌فرض از اسم کلاس استخراج می‌شود. در این قرارداد، پسوند انتهایی `Model` حذف و نام باقی‌مانده به `snake_case` تبدیل می‌شود؛ بنابراین مقدارهای اولیه `tableName = user` و `tableAlias = user` هستند.

`QBTable.name` و `QBTable.alias` مقدارهای پیش‌فرض متناظر را جایگزین می‌کنند. تنظیم DSL می‌تواند alias را دوباره override کند:

```kotlin
table {
    table(UserModel::class)
    alias("uu1")
}
```

نتیجه در این مثال `tableName = user_users` و `tableAlias = uu1` است. alias صریح DSL نام جدول را عوض نمی‌کند.

| فیلد | ترتیب Resolve |
|---|---|
| `tableName` | نام پیش‌فرض کلاس ← `QBTable.name` |
| `tableAlias` | alias پیش‌فرض ← `QBTable.alias` ← alias صریح DSL |

## Metadata سطح Property

```kotlin
@QBColumn(name = "user_id", alias = "user_id", primaryKey = true)
val id: Int = 0
```

با فرض Resolve شدن جدول، مقدارهای اولیه ستون از Property می‌آیند: نام ستون و alias پیش‌فرض از نام Property و نوع داده از نوع Kotlin آن. برای `id: Int` مقدار نوع اولیه `INT` است.

سپس `QBColumn.name` نام ستون منبع و `QBColumn.alias` alias پیش‌فرض ستون را override می‌کنند. Metadata مربوط به `primaryKey` مستقل از نام و alias باقی می‌ماند.

تنظیم DSL می‌تواند alias و نوع خروجی Query را تغییر دهد:

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

نتیجه: `columnName = user_id` حفظ می‌شود، `columnAlias = user_id_out` می‌شود و `columnDataType = LONG` برای خروجی Query انتخاب می‌شود. تغییر نوع خروجی نباید نوع تعریف‌شدهٔ Property یا Metadata اصلی Model را عوض کند.

| فیلد | ترتیب Resolve |
|---|---|
| `columnName` | نام پیش‌فرض Property ← `QBColumn.name` |
| `columnAlias` | alias پیش‌فرض Property ← `QBColumn.alias` ← alias صریح DSL |
| `columnDataType` | نوع استنباط‌شده از Property ← نوع صریح DSL با `execute(DataType)` |

نام ستون منبع و alias خروجی دو مفهوم جدا هستند؛ تغییر alias خروجی نام ستون فیزیکی را بازنویسی نمی‌کند.
