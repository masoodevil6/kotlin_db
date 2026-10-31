<a id="query-builder-examples"></a>
# مثال‌های Query Builder

<a id="query-builder-version-comparison"></a>
## خلاصه تفاوت نسخه‌ها

| نسخه | SELECT output و دسترسی | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| V1 | alias و DataType صریح؛ خواندن با alias | generated key: Long | affected rows: Int | affected rows: Int |
| V2 | Model property؛ خواندن با Model::property | Model | Model | Model |
| V3 | Query property و SQL alias اعلام‌شده؛ خواندن با `Query::property` | Model V3 | Model V3 | Model V3 |
| V3.1 | اجرای typed CTE و دریافت `ICollection<T>`؛ خواندن با propertyهای declaration | — | — | — |

`QueryConsumer.execute()` همهٔ ردیف‌ها را به‌صورت eager و با نوع `ExecuteResult<List<QueryRow>>` برمی‌گرداند؛ `first()` فقط ردیف اول را می‌خواند. متد `execute()` در Consumerهای INSERT، UPDATE و DELETE نیز همان لحظه عملیات را اجرا می‌کند و به‌ترتیب `ExecuteResult<Long>` یا `ExecuteResult<Int>` می‌دهد. متد `sql()` فقط SQL و bindingهای آن را می‌سازد و Query را اجرا نمی‌کند.

پارامترهای جدید به‌صورت AST تعریف می‌شوند: شرط‌ها از `sideValueParam { paramName(...); paramValue(...) }`، مقدارهای INSERT/UPDATE از `setValue { ... }` و مجموعه‌ها از `sideValueCollection { addParam { ... } }` استفاده می‌کنند. اگر `paramName` داده نشود، Builder نام تعیین می‌کند؛ نوع SQL از مقدار پارامتر resolve می‌شود. تعریف‌های هم‌نام فقط وقتی مقدار و SQL type یکسان داشته باشند در scope مشترک reuse می‌شوند؛ تفاوت مقدار یا نوع به binding مستقل می‌انجامد. Renderer پارامترمحور، SQL نمایشی با نام‌هایی مانند `:id1` و SQL JDBC با `?` تولید می‌کند و binding هر placeholder را در ترتیب ظهور SQL نگه می‌دارد. Executor این دنباله را از طریق `BoundQuery` به‌صورت positional bind می‌کند؛ بنابراین ترتیب نهایی SQL، از جمله برای SELECT تو‌در‌تو و CTE، مرجع ترتیب binding است. مسیر قدیمی `BuiltQuery` با placeholder نام‌دار نیز برای ورودی‌های legacy حفظ شده است.

پیش از اجرای SELECT، alias و `ExecutionType` تمام خروجی‌ها اعتبارسنجی می‌شوند. هنگام materialization نیز تعداد خروجی‌های ResultSet و label هر ستون با SELECT تطبیق داده می‌شود؛ مقدار هر خانه با `ExecutionType` متناظر خوانده می‌شود و `QueryRow` برای دسترسی رشته‌ای از SQL alias و برای دسترسی typed از `PropertyReference` همان خروجی استفاده می‌کند.

### مشاهدهٔ Metadata مؤثر SELECT با `details`

`QueryConsumer.details()`، Metadata مؤثر SELECT را از Provider همان Query دریافت می‌کند. نسخهٔ callback یعنی `details { metadata -> ... }` Metadata را به callback می‌دهد و همان Consumer را برمی‌گرداند؛ بنابراین می‌توان آن را میان `.sql { ... }` و `.execute()` زنجیره کرد:

~~~kotlin
import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .details { metadata -> println(formatEffectiveQueryMetadata(metadata)) }
    .execute()
~~~

تابع formatEffectiveQueryMetadata در ماژول Example اطلاعات را در دو جدول کنسولی، یکی برای Relationها و دیگری برای خروجی‌ها، نمایش می‌دهد. مقدار null با `<null>`، رشتهٔ خالی با `<empty>` و فهرست بدون عضو با `<none>` نمایش داده می‌شود. Metadata فعلی CTE و Query را گزارش می‌کند؛ sourceهای عادی Model یا جدول در این فهرست Relationها قرار نمی‌گیرند، بنابراین SELECT بدون CTE در هر دو بخش `<none>` نشان می‌دهد.

مقدار `metadata` از نوع `EffectiveQueryMetadata` است و فهرست Relationها و خروجی‌های مؤثر را نگه می‌دارد؛ از جمله نام منطقی Query، نام runtime مربوط به CTE، نام SQL در `WITH`، alias مربوط به occurrence، نام منطقی خروجی، alias منتشرشده در SQL و `executionType`. فیلدهای nullable در صورت نبود مقدار `null` باقی می‌مانند و خروجی‌ها و Relationها ترتیب Query را حفظ می‌کنند.

فراخوانی `details` به‌تنهایی SQL را render نمی‌کند و Query را اجرا نمی‌کند. اگر SELECT سفارشی Provider مربوط به `EffectiveQueryMetadata` را نداشته باشد، `details()` با `IllegalStateException` روشن خطا می‌دهد؛ این محدودیت بر `.sql()` و `.execute()` اثری ندارد. این API متعلق به SELECT است و برای Consumerهای INSERT، UPDATE و DELETE ارائه نشده است.
