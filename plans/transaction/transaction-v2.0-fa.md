# Transaction & Connection Scope Plan v2.0

**زبان:** فارسی  
**نسخه:** 2.0  
**وضعیت:** Transaction V2.0 پس از ممیزی نهایی و بررسی XMLهای موجود `Complete` است. Phaseهای 1 تا 6 پیاده‌سازی شده‌اند؛ تست‌های unit/proxy، consumer compile مستقل، Integration واقعی MySQL و رفتار `FOR UPDATE` با دو Connection مستقل شواهد موفق دارند.
**مسیر فایل:** `plans/transaction/transaction-v2.0-fa.md`  
**مبنای طراحی:** پلن `transaction-v1.0-fa.md` و پیاده‌سازی فعلی Transaction و مدیریت Connection

## وضعیت اجرا و آخرین ممیزی

- **تاریخ:** ۲۹ اکتبر ۲۰۲۶
- **مرحلهٔ فعلی:** Finalization تکمیل شد؛ Phase دیگری آغاز نشده است.
- **آخرین اقدام:** ممیزی read-only نتایج موجود `TransactionMySqlIntegrationTest` و `LockForUpdateRenderingTest` را با معیارهای پذیرش تطبیق داد. اجرای تست جدید یا اتصال دیتابیسی در Finalization انجام نشد.
- **نتیجه:** Connection Scope همچنان بدون تغییر `autoCommit` و بدون commit/rollback خودکار است؛ Transaction همچنان مالک `autoCommit=false`، commit، rollback و close است. هر دو از state/guard و coordinator per-root برای Failure latch، thread ownership و جلوگیری از bypass استفاده می‌کنند؛ lifecycle و cleanup اختصاصی هر Scope حفظ شده‌اند. مسیر one-shot `withOwnedConnection` و اجرای مستقیم `BuiltQuery`/`BoundQuery` در Context سطح بالا تغییر نکردند.
- **منبع این سند:** مسیر `plans/transaction/transaction-v2.0-fa.md` پیش از این موجود نبود. متن نسخهٔ اولیه از پیش‌نویس V2.0 ارائه‌شدهٔ کاربر در پیوست قبلی بازیابی و در همین مسیر ثبت شد؛ سپس فقط بر اساس شواهد ممیزی اصلاح شد. پلن `transaction-v1.0-fa.md` حفظ شده و تغییر نکرده است.
- **حفظ Git:** پیش از Phase 2 وضعیت ثبت‌شده ۲۵ مورد ` M`، ۸ مورد `AM` و ۳۰ مورد `??` بود. پس از آن، وضعیت شامل ۲۷ مورد ` M`، همان ۸ مورد `AM` و همان ۳۰ مورد `??` است؛ index هنوز دقیقاً همان هشت فایل LockForUpdate را دارد. چون چند فایل executor از قبل تغییرات موجود داشتند، Git status فقط وضعیت هر مسیر را نشان می‌دهد و افزایش شمار مسیرهای ` M` را نمی‌توان به‌طور کامل به خطوط این Phase نسبت داد. هیچ فایل stage یا commit نشد.
- **اعتبارسنجی Phase 1:** `:data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.QueryConsumerExecuteAsTest" --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --rerun-tasks --console=plain` با موفقیت پایان یافت: `QueryConsumerExecuteAsTest` شامل ۷ تست و `TransactionExecutionTest` شامل ۲۹ تست؛ در مجموع ۳۶ موفق، صفر failure، صفر error و صفر skipped. اجرای هر دو کلاس از JDBC proxy/fake استفاده کرد و هیچ اتصال دیتابیسی برقرار نشد.
- **اعتبارسنجی مجدد و Phase 2:** فرمان `:data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --tests "gog.my_project.data_base.query.executer.manager.QueryConsumerExecuteAsTest" --rerun-tasks --console=plain` با JDK 24 و `GRADLE_USER_HOME` محلی پروژه اجرا شد. XML نهایی: `TransactionExecutionTest`، ۴۲ تست؛ `QueryConsumerExecuteAsTest`، ۷ تست؛ جمعاً ۴۹ موفق، صفر failure، صفر error و صفر skipped. Gradle گزارش کرد `BUILD SUCCESSFUL` و ۳۷ task اجرا شد. این‌ها تست‌های unit/proxy هستند؛ `TransactionMySqlIntegrationTest` اجرا نشد و اتصال دیتابیس برقرار نشد.
- **محدودیت محیط اعتبارسنجی Phase 1:** Kotlin daemon نتوانست فایل موقت marker را در `%LOCALAPPDATA%\kotlin\daemon` ایجاد/دسترسی دهد و Gradle برای کامپایل به fallback بدون daemon رفت؛ همان اجرای fallback موفق شد. هشدار ناسازگاری JVM target برای JDK 24 و fallback به JVM 22، و هشدار deprecated بودن `sun.misc.Unsafe::objectFieldOffset` نیز ثبت شدند؛ هیچ‌کدام مانع موفقیت آن اجرا نشدند.
- **حفظ Git در Phase 1:** پیش از تغییرات این مرحله، working tree شامل ۲۵ مورد ` M`، ۸ مورد `AM` و ۳۰ مورد `??` بود و index فقط هشت فایل LockForUpdate را داشت. تغییرات این مرحله به فایل‌های `QueryConsumer.kt`، `QueryBuilderExecutor.kt`، `QueryConsumerExecuteAsTest.kt` و `TransactionExecutionTest.kt` افزوده شد؛ فایل‌های LockForUpdate و index دست‌نخورده ماندند. پلن V2.0 نیز از قبل untracked بود و در این مرحله به‌روزرسانی شد.
- **هشدارها و محدودیت محیط:** Kotlin daemon نتوانست marker موقت را در `%LOCALAPPDATA%\kotlin\daemon` ایجاد/دسترسی دهد؛ Gradle از کامپایل fallback بدون daemon استفاده کرد و اجرا موفق شد. هشدار ناسازگاری JVM target (Java 24 در برابر Kotlin JVM 22)، هشدار deprecated بودن `sun.misc.Unsafe::objectFieldOffset` و چند هشدار API تستی deprecated ثبت شدند؛ هیچ‌کدام مانع build/test نشدند.
- **اعتبارسنجی Phase 3:** `TransactionExecutionTest` شامل ۴۳ تست، `QueryConsumerExecuteAsTest` شامل ۷ تست و `QueryExecuteContractTest` شامل ۲۷ تست بودند؛ جمعاً ۷۷ تست موفق، صفر failure، صفر error و صفر skipped در XMLهای همین اجرا. تمام این تست‌ها از fake/proxy استفاده کردند؛ هیچ اتصال دیتابیسی برقرار نشد.
- **تصمیم lifecycle:** اشتراک state/guard با اشتراک lifecycle یکسان نیست. lease در این سند یعنی abstraction داخلی‌ای که یک Connection تازه را به‌عنوان مالک در اختیار runner می‌گیرد و close-once را مستقل از policy اجرای Scope تضمین می‌کند. چنین leaseای لازم تشخیص داده نشد؛ ownership و cleanup در دو runner باقی می‌مانند تا semantics Connection و Transaction روشن و جدا بماند.
- **فایل‌های تغییرکرده در Phase 3:** فقط همین پلن به‌روزرسانی شد؛ تغییر production، تست یا Gradle لازم نشد، چون تست‌های proxy موجود همهٔ سناریوهای درخواستی را پوشش می‌دادند.
- **فرمان‌ها و XMLهای Phase 3:** `:data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --tests "gog.my_project.data_base.query.executer.manager.QueryConsumerExecuteAsTest" --rerun-tasks --no-build-cache --console=plain`؛ XMLهای `data_base/query/executor/build/test-results/test/TEST-gog.my_project.data_base.query.executer.manager.TransactionExecutionTest.xml` و `...QueryConsumerExecuteAsTest.xml`. سپس `:data_base:manager:execute:test --tests "gog.my_project.data_base.manager.execute.manager.QueryExecuteContractTest" --rerun-tasks --no-build-cache --console=plain`؛ XML: `data_base/manager/execute/build/test-results/test/TEST-gog.my_project.data_base.manager.execute.manager.QueryExecuteContractTest.xml`.
- **هشدارهای اجرای Phase 3:** هر دو فرمان به‌علت `AccessDeniedException` هنگام ایجاد Kotlin daemon marker به fallback بدون daemon رفتند و با `BUILD SUCCESSFUL` خاتمه یافتند. هشدار terminal deprecation مربوط به `sun.misc.Unsafe::objectFieldOffset` و هشدارهای DSL تست deprecated نیز دیده شدند. ناسازگاری JVM target (Java 24 در برابر Kotlin JVM 22) در اعتبارسنجی‌های قبلی ثبت شده بود؛ در خروجی دو فرمان Phase 3 هشدار تازه‌ای دربارهٔ آن ظاهر نشد.
- **حفظ Git در Phase 3:** پیش و پس از کار، ۴۷ مسیر status ثبت شد: ۸ staged، ۳۵ unstaged و ۱۲ untracked. index شامل همان هشت فایل LockForUpdate با `AM` ماند؛ هیچ stage یا commit انجام نشد. پلن پیش از این Phase untracked بود و فقط محتوای همان فایل در Phase 3 تغییر کرد.
- **نتیجهٔ Phase 4:** پذیرش unit/proxy برای رفتارهای بررسی‌شده تأیید می‌شود، اما پذیرش کامل V2.0 به‌علت اجرا نشدن Integration واقعی و ابهام اجرای executor ریشه از thread غیرمالک قطعی نیست. هیچ فایل source/test/build تغییر نکرد و هیچ build/test/Gradle task یا اتصال دیتابیسی انجام نشد.

---

## ۱. هدف

هدف نسخهٔ 2.0 یکپارچه‌سازی معماری اجرای چند Query روی یک Connection و ارائهٔ دو API هماهنگ است:

```kotlin
queryManager.connection { ctx ->
    // اجرای چند Query روی یک Connection مشترک
}
```

و:

```kotlin
queryManager.transaction { ctx ->
    // اجرای چند Query روی یک Connection مشترک
    // همراه با مدیریت تراکنش
}
```

هدف طراحی این است که هر دو API از مسیر مشترک اجرای Query روی Connection استفاده کنند؛ کد فعلی هنوز این زیرساخت مشترک را ندارد. قرارداد این سند مسیر مشترک Scope را تثبیت می‌کند، اما سیاست `autoCommit`، commit، rollback و گزارش خطا را برای Connection و Transaction جدا نگه می‌دارد.

این نسخه امکان ساخت query API در فایل یا لایه‌ای جدا و bind کردن آن به executor داخل Scope را از طریق `ctx.queryBuilder(api)` تضمین می‌کند، بدون اینکه سازندهٔ Query مالک Connection شود. اجرای مستقیم شیء رندرشدهٔ `BuiltQuery`/`BoundQuery` در v2.0 خارج از دامنه است؛ برای آن نیاز مستقلی در کد/مصرف‌کنندگان فعلی اثبات نشده و افزودنش API مجزا و پرهزینه‌تری می‌طلبد.

## ۲. اصول معماری

### ۲.۱. State و guard مشترک، lifecycle متناسب با Scope

Connection Scope و Transaction Scope از `TransactionExecutionScope` و `TransactionGuard` برای state اجرای scoped مشترک استفاده می‌کنند: Connection مرجع همان Scope، thread مالک، وضعیت active، نخستین Failure، Error fatal، و تشخیص nesting/cross-nesting. این اشتراک تضمین می‌کند dispatch از مسیر scoped بماند و Query بعدی پس از Failure رد شود.

اشتراک state/guard به‌معنای اشتراک lifecycle نیست. هر runner Connection خود را از همان factory می‌گیرد و مسئول cleanup متناسب با قرارداد خودش است. بررسی Phase 3 هیچ نقص correctness یا دوبار‌بستن را در این جدایی نشان نداد؛ تست‌ها close-once، rollback و تمایز close پیش/پس از commit را پوشش می‌دهند. مسیر one-shot `withOwnedConnection` برای `BuiltQuery`/`BoundQuery` در `manager:execute` مستقل می‌ماند، چون lifetime آن یک operation است و از executor قابل دسترسی نیست؛ این مسیر هدف refactor v2.0 نیست.

**تعریف lease در این پلن:** lease یک abstraction داخلی مالک Connection تازه است که acquisition را تحویل می‌گیرد، Connection را در اختیار runner scoped می‌گذارد و close-once را تضمین می‌کند؛ lease به‌تنهایی commit/rollback یا policy Failure را اجرا نمی‌کند. صرف داشتن `Connection` در `TransactionExecutionScope` lease محسوب نمی‌شود. Phase 3 نتیجه گرفت چنین abstraction جداگانه‌ای برای قرارداد فعلی لازم نیست: متمرکزکردن lifecycle در wrapper مشترک، درحالی‌که close عادی، rollback-then-close و close پس از commit نتایج متفاوت دارند، پیچیدگی stateful و خطر پنهان‌کردن policy می‌افزاید. lease فقط با نیاز جدید و طراحی/test مشخص دوباره ارزیابی می‌شود، نه به‌عنوان مانع تکمیل v2.0.

هیچ مسیر Query در Scope نباید به Connection مستقل fallback کند. dispatch SELECT/INSERT/UPDATE/DELETE از executor scoped و supplied-Connection overloadها استفاده می‌کند. مسیر one-shot فعلی در `manager:execute` از این Scopeها جداست و در این نسخه بازطراحی نمی‌شود.

### ۲.۲. تفکیک Connection از Transaction

Connection و Transaction دو مفهوم مرتبط، اما متفاوت‌اند:

- **Connection Scope:** مالکیت Connection و اجرای چند Query روی همان اتصال.
- **Transaction Scope:** همان قابلیت اشتراک Connection، به‌اضافهٔ مدیریت وضعیت تراکنش، commit و rollback.

Transaction باید از زیرساخت مشترک استفاده کند، اما نباید صرفاً یک Connection Scope با چند شرط اضافی و مبهم باشد.

### ۲.۳. جداسازی ساخت Query از اجرای آن

ساخت AST/API مربوط به Query نباید مستلزم اجرای فوری آن باشد. API فعلی `queryBuilder(query: IQueryRenderSelectApi/IQueryRenderInsertApi/IQueryRenderUpdateApi/IQueryRenderDeleteApi)` می‌تواند Query ساخته‌شده را به executor موجود bind کند؛ همین مسیر در Scope برای استفادهٔ چندفایلی قابل استفاده است.

`BuiltQuery` و `BoundQuery` نوع‌های پایین‌سطح اجرای SQL هستند. `IQueryExecute` برای هر دو نوع overload با Connection تزریق‌شده دارد، اما `IQueryBuilderExecutor` متدی برای اجرای مستقیم این دو نوع ندارد. تصمیم v2.0: افزودن چنین overloadهایی لازم نیست و خارج از دامنه می‌ماند؛ اگر نیاز مصرف‌کننده‌ای بعداً اثبات شد، باید جداگانه طراحی شود.

مسیر Fluent API فعلی باید حفظ شود؛ `sql()` برای رندر/مشاهدهٔ Query است و به‌تنهایی SQL را اجرا نمی‌کند.

### ۲.۴. مالکیت صریح

هر Connection دقیقاً یک مالک چرخهٔ عمر دارد.

- Connection مستقل متعلق به Scopeای است که آن را دریافت کرده است.
- Connection تزریق‌شده متعلق به فراخواننده است.
- Connection تراکنش متعلق به Transaction Scope است.
- هیچ لایه‌ای نباید Connection متعلق به لایهٔ دیگر را ببندد.

### ۲.۵. حفظ API فعلی

رفتار اجرای مستقل Query، overloadهای دریافت‌کنندهٔ Connection، `ExecuteResult` و API عمومی موجود نباید بدون دلیل و بررسی سازگاری تغییر کند.

افزودن `connection {}` باید با پیاده‌سازی‌هایی که آن را پشتیبانی نمی‌کنند سازگار باشد: پیش‌فرض امن، شکست صریح و عدم اجرای callback یا fallback به Connection مستقل است؛ همان الگویی که `transaction {}` در interface فعلی دارد.

---

## ۳. دامنهٔ نسخه

### داخل دامنه

- افزودن API عمومی `connection {}`.
- هماهنگ‌سازی Connection Scope و Transaction Scope.
- استخراج یا استفادهٔ مجدد از زیرساخت مشترک مدیریت Connection و اجرای Query.
- اجرای ترتیبی چند Query از API/ASTهای آماده‌ای که Context فعلی executor می‌تواند bind کند.
- اجرای مستقیم `BuiltQuery` و `BoundQuery` خارج از دامنهٔ v2.0 است؛ فقط در صورت نیاز مستقل مستند، proposal جدا ارائه شود.
- تعیین رفتار Failure، Exception، cleanup و بستن Connection.
- تست‌های واحد برای lifecycle و قراردادهای اجرایی.
- مستندسازی مثال‌ها و تفاوت Connection با Transaction.

### خارج از دامنه

- تغییر مدل AST یا Renderer.
- تغییر semantics عمومی `ExecuteResult`.
- بازنویسی کامل Query Builder یا Fluent API.
- افزودن coroutine یا اجرای موازی Queryها روی یک Connection.
- ایجاد سیستم جدید Connection Pool.
- تغییر رفتار دیتابیس یا تنظیمات MySQL.
- تغییر قراردادهای موجود Transaction بدون ضرورت اثبات‌شده.

---

## ۴. قرارداد API تثبیت‌شده

قرارداد عمومی v2.0 برای Connection Scope:

```kotlin
fun <T> connection(
    block: (ctx: IQueryBuilderExecutor) -> ExecuteResult<T>,
): ExecuteResult<T>
```

متد روی `IQueryBuilderExecutor` با default fail-closed هم‌الگوی `transaction` تعریف می‌شود: implementation فاقد پشتیبانی `Failure(UnsupportedOperationException)` برمی‌گرداند، callback را اجرا نمی‌کند و به Connection مستقل fallback نمی‌کند. این تصمیم بر اساس `IQueryBuilderExecutor.kt:42–48` است و مانع الزام منبعی test doubleها و implementationهای دیگر به override جدید می‌شود.

شکل مفهومی API:

```kotlin
// Query API آماده؛ deleteApi/updateApi از نوع IQueryRenderDeleteApi/
// IQueryRenderUpdateApi هستند، نه BuiltQuery یا BoundQuery.
queryManager.connection { ctx ->
    val deleted = ctx.queryBuilder(deleteApi).execute()
    if (deleted is ExecuteResult.Failure) return@connection deleted

    val updated = ctx.queryBuilder(updateApi).execute()
    if (updated is ExecuteResult.Failure) return@connection updated

    ExecuteResult.Success("done")
}
```

و:

```kotlin
queryManager.transaction { ctx ->
    val deleted = ctx.queryBuilder(deleteApi).execute()
    if (deleted is ExecuteResult.Failure) return@transaction deleted

    val updated = ctx.queryBuilder(updateApi).execute()
    if (updated is ExecuteResult.Failure) return@transaction updated

    ExecuteResult.Success("done")
}
```

امضای `connection` همان شکل generic `transaction` را دارد: callback یک `ExecuteResult<T>` می‌دهد و Scope نیز `ExecuteResult<T>` برمی‌گرداند؛ نتیجهٔ تو‌در‌تو ساخته نمی‌شود. `ctx.queryBuilder(api)` با overloadهای فعلی سازگار است.

### مقایسهٔ مسیر اجرای Query

| مسیر | پوشش نیاز چندفایلی | پارامترها و binding | پیچیدگی و نگهداری | تصمیم v2.0 |
|---|---|---|---|---|
| `ctx.queryBuilder(api).execute()` | بله؛ فایل سازنده می‌تواند `IQueryRender...Api` بسازد و به تابع اجرای فایل دیگر بدهد. | API/AST موجود به renderer فعلی می‌رود؛ ترتیب occurrenceهای پارامتر از `QueryParameterRendering` حفظ می‌شود و query دوباره دستی render یا bind نمی‌شود. | کم؛ از Fluent API، result typeهای فعلی و مسیر scoped executor استفاده می‌کند. | مسیر عمومی و پشتیبانی‌شدهٔ v2.0. |
| اجرای مستقیم `BuiltQuery` و `BoundQuery` از Context | فعلاً نیازی اثبات‌شده ندارد؛ این نوع‌ها برای مصرف‌کننده‌ای که از قبل SQL رندرشده دارد کاربرد جداگانه‌ای خواهند داشت. | باید همان object/occurrence list و ترتیب binding را بی‌تغییر حفظ کند؛ Context فعلی operation type را از این wrapperها به‌تنهایی تعیین نمی‌کند. | بالاتر؛ نیازمند overload/API اضافی و dispatch نوع‌دار برای SELECT/DML و تست‌های binding مستقل است. | خارج از v2.0؛ API جدید فقط با نیاز مصرف‌کنندهٔ مستند و بررسی جداگانه. |

بنابراین «Query ساخته‌شده در فایل دیگر» در این سند به query API/AST از نوع `IQueryRenderSelectApi`, `IQueryRenderInsertApi`, `IQueryRenderUpdateApi` یا `IQueryRenderDeleteApi` اشاره دارد، نه `BuiltQuery`/`BoundQuery`.

### الزامات API

1. Scope باید query APIهای از قبل ساخته‌شده و قابل bind را از طریق `ctx.queryBuilder(api)` اجرا کند. `BuiltQuery`/`BoundQuery` مستقیم در این نسخه پشتیبانی نمی‌شوند.
2. Queryها باید در ترتیب فراخوانی اجرا شوند.
3. Scope نباید برای اجرای Query به ساخت Connection مستقل جدید منجر شود.
4. خروجی عملیات باید از قرارداد نتیجهٔ فعلی پروژه پیروی کند.
5. callbackهای مصرف‌کنندهٔ ResultSet باید پیش از بسته‌شدن Statement، نتیجه را مصرف کنند.
6. API نباید Connection را برای بستن دستی به کاربر تحویل دهد.
7. فراخوانی‌های معمولی خارج از Scope باید رفتار فعلی خود را حفظ کنند.
8. API باید مسیرهای موجود `queryBuilder(IQueryRender...Api)` و ترتیب occurrenceهای پارامتر را حفظ کند. `sql()` و `details()` صرفاً رندر/تحلیل‌اند و به‌تنهایی SQL اجرا نمی‌کنند؛ مسیر `executeOut()` در Scope رد می‌شود تا callback نتواند `IQueryExecute` مستقل بگیرد.

---

## ۵. معماری هدف

```text
             Query API / AST
                   |
          QueryBuilderExecutor
          render + bind + dispatch
                   |
       shared scoped execution state
       (Connection, active/thread guard,
        first visible Failure, fatal Error)
                   |
          QueryExecute supplied-Connection overloads
                   |
             /                 \
    Connection Scope       Transaction Scope
    acquire/close once      acquire, autoCommit=false,
    no commit/rollback     commit/rollback, then close

    BuiltQuery/BoundQuery one-shot path remains in manager:execute
    and keeps its existing per-operation withOwnedConnection helper.
```

این نمودار معماری تثبیت‌شده و پیاده‌سازی جاری را نشان می‌دهد. Context عمومی جدیدی لازم نیست: callback از `IQueryBuilderExecutor` فعلی استفاده می‌کند و state/guard scoped در `query:executor` مشترک‌اند. هر runner مالک lifecycle Connection خود است؛ `QueryExecute` برای overloadهای دریافت‌کنندهٔ Connection آن را نمی‌بندد. مسیر one-shot سطح پایین در `manager:execute` جدا می‌ماند.

### مسئولیت‌های لایه‌ها

#### زیرساخت Connection مشترک برای Scopeها

- `DatabaseConnection.build()` فقط factory است. `withOwnedConnection` در `manager:execute` مالکیت یک Connection مستقل یک‌عملیاتی را از طریق `use` مدیریت می‌کند.
- `IQueryExecute.execute(BuiltQuery, ...)` و overloadهای مستقل `BoundQuery` از helper یک‌عملیاتی استفاده می‌کنند. این مسیرها در v2.0 دست‌نخورده می‌مانند.
- **تصمیم معماری:** Scopeهای چندعملیاتی در `query:executor` از state/guard داخلی مشترک استفاده می‌کنند؛ API عمومی Scope همان `IQueryBuilderExecutor` است و interface یا Context عمومی جدا لازم نیست. lifecycle/ownership به‌صورت runner-specific باقی می‌ماند؛ lease مشترک شرط معماری نیست.
- **وضعیت پیاده‌سازی:** `QueryBuilderExecutor` تمام SELECT/INSERT/UPDATE/DELETEهای scoped را از overloadهای `QueryExecute(connection, ...)` عبور می‌دهد و fallback مستقل ندارد. `TransactionExecutionScope`/`TransactionGuard` state و guard هر دو Scope را فراهم می‌کنند؛ هر runner acquisition و cleanup متناسب با قراردادش را انجام می‌دهد.
- `QueryExecute` و `DatabaseConnection` مسئول commit/rollback نیستند. Connection Scope فقط close می‌کند؛ runner تراکنش policy `autoCommit=false`, commit/rollback و cleanup را اعمال می‌کند. Phase 3 با تست‌های رگرسیون تأیید کرد که حفظ دو lifecycle جدا رفتارهای قرارداد را روشن‌تر نگه می‌دارد و abstraction lease جدیدی لازم نیست.
- این تصمیم یکپارچگی lifecycle بین Connection Scope و Transaction Scope را پوشش می‌دهد؛ بازطراحی cross-module مسیر one-shot `withOwnedConnection` جزو v2.0 نیست، چون helper داخلی، operation-level و در ماژولی جداست.

#### Connection Scope

- دریافت یک Connection مستقل از factory در ابتدای Scope؛ Connection از بیرون تزریق یا به کاربر تحویل داده نمی‌شود.
- اجرای Queryهای مجاز روی همان Connection، از مسیر supplied-Connection.
- مالکیت close از ابتدا تا انتهای Scope؛ دقیقاً یک close حتی در Failure/Exception/Error.
- عدم تغییر `autoCommit` و عدم فراخوانی خودکار `commit()` یا `rollback()`.
- callback یک بار اجرا می‌شود؛ خطای اصلی حفظ می‌شود و cleanup به‌عنوان خطای ثانویه ثبت می‌شود.

#### Transaction Scope

- **وضعیت موجود:** `QueryBuilderExecutor.transaction` از factory Connection می‌گیرد، `autoCommit=false` می‌کند، scope می‌سازد، commit/rollback و close را اجرا می‌کند.
- مسیر execution state با Connection Scope مشترک است. Transaction runner مالک سیاست `autoCommit=false`, commit, rollback و close باقی می‌ماند؛ این lifecycle با Connection Scope یکی نمی‌شود.
- هیچ مسئولیت commit/rollback به Connection Scope یا `QueryExecute` منتقل نمی‌شود. هر تفاوتی با رفتار فعلی Transaction باید با تست رگرسیون اثبات و جداگانه گزارش شود.

---

## ۶. قرارداد Connection Scope

`IQueryBuilderExecutor.connection {}` و پیاده‌سازی آن در `QueryBuilderExecutor` اکنون موجود است. قرارداد این فصل بر اساس پیاده‌سازی و تصمیم‌های تثبیت‌شدهٔ v2.0 است؛ ارزیابی پذیرش و محدودیت‌های باقی‌مانده در بخش‌های پایانی آمده‌اند.

### ۶.۱. شروع Scope

در آغاز Scope دقیقاً یک Connection مستقل از factory داخلی گرفته می‌شود. تزریق Connection از فراخواننده مجاز نیست و Connection خام به callback داده نمی‌شود.

اگر acquisition با `Exception` شکست بخورد، callback اجرا نمی‌شود و همان خطا در `ExecuteResult.Failure` برمی‌گردد؛ `Error` دوباره پرتاب می‌شود. اگر Connection گرفته شده باشد ولی آماده‌سازی Scope شکست بخورد، close دقیقاً یک بار تلاش و خطای cleanup به خطای اصلی ضمیمه می‌شود.

### ۶.۲. اجرای Query

تمام SELECT/INSERT/UPDATE/DELETEهای اجراشده از طریق Context از Connection همان Scope استفاده می‌کنند. `QueryBuilderExecutor` آن‌ها را به overloadهای `QueryExecute(connection, ...)` می‌فرستد. Connection تزریق‌شده توسط Scope در `QueryExecute` بسته نمی‌شود؛ مالک Scope آن را می‌بندد.

هیچ Query داخل Scope نباید از مسیر مستقل Connection دیگری بگیرد. `executeOut()` در تمام Scopeها پیش از اجرای callback با `UnsupportedOperationException` پرتاب‌شده رد می‌شود (امضای آن `Unit` است)؛ تلاش در state Scope ثبت می‌شود تا حتی اگر callback آن را بگیرد، Scope موفق نشود. پشتیبانی TABLE نیز تا وقتی مسیر supplied-Connection در executor تعریف نشده، داخل Scope تضمین نمی‌شود.

Queryها به‌صورت ترتیبی اجرا می‌شوند. اجرای هم‌زمان یا موازی روی یک Connection در این نسخه پشتیبانی نمی‌شود.

### ۶.۳. پایان موفق

پس از پایان callback، Connection دقیقاً یک بار بسته می‌شود.

Connection Scope نه `commit()` و نه `rollback()` را خودکار اجرا می‌کند؛ مقدار اولیهٔ `autoCommit` را تغییر نمی‌دهد. بنابراین Scope اتمیک نیست و با `autoCommit=true` تغییرات DML موفق ممکن است پیش از پایان callback پایدار شده باشند. با `autoCommit=false` نیز قرارداد Scope وعدهٔ commit یا rollback نمی‌دهد؛ semantics نهایی به Connection/driver واگذار می‌شود.

### ۶.۴. Failure در نتیجهٔ Query

Failure ثبت‌شده در execution state نباید نادیده گرفته شود:

- اولین Failure مشاهده‌شده از مسیر اجرایی scoped ثبت شود.
- پیش از dispatch JDBC هر Query بعدی، state بررسی شود؛ پس از Failure، Query جدید اجرا نمی‌شود و همان Failure برگردانده می‌شود.
- اگر callback با وجود Failure ثبت‌شده `Success` برگرداند، نتیجهٔ نهایی همان Failure ثبت‌شده است؛ اگر callback Failure دیگری برگرداند، Failure نخستین اولویت دارد و خطای بعدی در صورت امکان suppressed می‌شود.
- اگر callback خودش `Failure` برگرداند و Failure قبلی در state ثبت نشده باشد، همان نتیجهٔ callback بدون تبدیل یا پاک‌کردن خطا نتیجهٔ Scope می‌شود.
- Connection در هر مسیر در انتها بسته می‌شود.
- هیچ commit یا rollback خودکاری انجام نمی‌شود.

Failureهای `first/get` از validation و materialization پیش از تحویل به مصرف‌کننده از wrapperهای `QueryBuilderExecutor.first/get` عبور می‌کنند. wrapperها `super.first/get` را اجرا می‌کنند و `executeInScope` نتیجهٔ نهایی را پیش از callback کاربر با `scope.recordResult` ثبت می‌کند؛ بنابراین Failure پیش از dispatch، materialization و اجرای JDBC در Transaction و Connection Scope به state همان Scope latch می‌شوند. اگر callback آن را نادیده بگیرد، Query بعدی رد می‌شود.

شکاف پیشین در `QueryConsumer.executeAs()` این بود که `execute()` ابتدا `List<QueryRow>` را از `executor.get` می‌گیرد، اما mapping تایپ‌شده پس از برگشت آن callback انجام می‌شود (`QueryConsumer.kt:27–56`). بنابراین Failureهای نبودن فهرست materialized و exception mapper در `executeInScope` دیده نمی‌شدند. Phase 1 آن را با observer/helper داخلی برطرف کرد: Failureهای framework-generated به همان state مشترک/`TransactionExecutionScope` گزارش می‌شوند، بدون API عمومی یا latch/state دوم. Failure اجرای JDBC که از `execute()` آمده باشد از قبل ثبت شده است و mapping موفق یا Collection خالی Failure ثبت نمی‌کند.

این قرارداد به Failureهایی محدود است که خود مسیر Query تولید می‌کند. هر `ExecuteResult` دلخواهی که کاربر مستقل از اجرای Query بسازد یا در متغیر نگه دارد، بازرسی نمی‌شود؛ اگر callback نهایی Scope آن را برگرداند، قرارداد نتیجهٔ callback اعمال می‌شود. `sql()` و `details()` به‌تنهایی Query یا mapping اجرا نمی‌کنند و state را تغییر نمی‌دهند.

**محدودیت مهم:** نبود rollback به این معناست که تغییرات موفق قبلی ممکن است قبلاً توسط دیتابیس ثبت شده باشند، به‌خصوص وقتی `autoCommit` فعال است. Connection Scope تضمین اتمیک بودن عملیات را نمی‌دهد.

### ۶.۵. Exception در callback

اگر callback یک `Exception` پرتاب کند:

- Connection بسته شود و Scope، `ExecuteResult.Failure` با همان Exception اصلی برگرداند.
- callback دوباره اجرا نشود.
- خطای cleanup نباید Exception اصلی را بی‌دلیل جایگزین کند؛ در صورت امکان به‌عنوان suppressed exception ثبت شود.

اگر پیش از Exception، Failure در Scope ثبت شده باشد، همان Failure اصلی باقی می‌ماند و Exception callback به‌عنوان suppressed ضمیمه می‌شود؛ callback دوباره اجرا نمی‌شود. این اولویت با `Transaction` فعلی هم‌راستاست.

### ۶.۶. Error

برای `Error`، از جمله Errorی که در execution path ثبت شده اما توسط `QueryConsumer` به Failure ظاهری تبدیل شده باشد:

- cleanup انجام شود.
- همان Error پس از cleanup دوباره پرتاب شود؛ close Exception/Error به‌عنوان suppressed ثبت شود و Error به Failure عادی تنزل نکند.
- Error نباید بدون دلیل به `ExecuteResult.Failure` تبدیل شود.
- اگر Error از mapper در `executeAs()` بیاید، پل داخلی typed mapping آن را پیش از rethrow به‌عنوان fatal در state همان Scope ثبت می‌کند؛ Error عیناً دوباره پرتاب می‌شود و به `IllegalStateException` مربوط به mapper wrap نمی‌شود.

### ۶.۷. خطای بستن Connection

اگر callback/Queryها موفق باشند، اما بستن Connection با `Exception` شکست بخورد، Scope `Failure` با wrapper تشخیصی `IllegalStateException` و cause برابر خطای close برمی‌گرداند؛ پیام باید روشن کند Scope تمام شده، commit/rollback انجام نداده و تغییرات ممکن است قبلاً پایدار شده باشند، پس Failure به معنی rollback نیست و retry کور ایمن فرض نمی‌شود. این wrapper در v2.0 نوع عمومی جدید جداگانه ندارد.

اگر عملیات قبلاً با Failure یا Exception شکست خورده باشد، همان علت اصلی حفظ و close exception به‌عنوان suppressed ضمیمه می‌شود. اگر close خودش `Error` بدهد، Error پس از cleanup پرتاب می‌شود و علت قبلی به‌عنوان suppressed نگه داشته می‌شود.

در همهٔ حالت‌ها callback دقیقاً یک بار اجرا و close دقیقاً یک بار تلاش می‌شود؛ close دوباره توسط QueryExecute یا callback مجاز نیست.

---

## ۷. قرارداد Transaction Scope

Transaction فعلی در `QueryBuilderExecutor` پیاده‌سازی شده است؛ رفتار زیر از سورس قابل‌تأیید است و قرارداد مرجع v2 خواهد بود، مگر اینکه تست یا بررسی دقیق تناقضی نشان دهد.

### موفقیت

1. یک Connection از `connectionFactory` دریافت شود.
2. Transaction فعال شود.
3. تمام Queryها از همان Connection استفاده کنند.
4. اگر callback موفق باشد و هیچ Failure ثبت‌شده‌ای وجود نداشته باشد، commit انجام شود.
5. Connection بسته شود.

### Failure

1. اولین Failure ثبت شود (`TransactionExecutionScope.firstFailure`).
2. اجرای Queryهای بعدی از executor scoped طبق قرارداد فعلی رد شود.
3. commit انجام نشود.
4. rollback اجرا شود.
5. Connection بسته شود.
6. نتیجهٔ اصلی Failure حفظ شود.

### Exception

- Exception callback طبق قرارداد فعلی به Failure تبدیل شود.
- rollback و cleanup انجام شوند.
- callback دوباره اجرا نشود.
- اگر Query Failure پیش از Exception callback ثبت شده باشد، `firstFailure` بر Exception بعدی اولویت دارد.

### Error

- cleanup انجام شود.
- Error اصلی پس از cleanup دوباره پرتاب شود.
- Error ثبت‌شده در مسیر Query نیز در scope به‌عنوان fatal نگه‌داری می‌شود تا Exception-to-Failure در consumer، نتیجهٔ تراکنش را به Failure عادی تقلیل ندهد.

### Commit Failure

- خطای commit گزارش شود.
- rollback طبق رفتار فعلی تلاش شود.
- خطای rollback در صورت وقوع، بدون جایگزینی علت اصلی حفظ شود.
- Connection بسته شود.
- در پیاده‌سازی، شکست commit به `TransactionCommitException` تبدیل می‌شود و نتیجهٔ `Failure` برمی‌گردد، مگر اینکه cleanup یک `Error` fatal ایجاد کند.

### Close Failure پس از Commit موفق

اگر commit موفق بوده و بستن Connection شکست بخورد، نتیجه باید وضعیت commit‌شده را از خطای cleanup متمایز کند. تغییرات ثبت‌شده نباید به‌اشتباه rollback‌شده گزارش شوند.

### محدودیت Transaction

این نسخه نباید رفتارهای زیر را اضافه کند:

- nested transaction بدون طراحی مشخص.
- savepoint خودکار.
- اجرای موازی Queryها روی Connection مشترک.
- پشتیبانی ضمنی از coroutine.

---

## ۸. هم‌راستاسازی دو Scope

Connection Scope و Transaction Scope باید قراردادهای مشترک زیر را داشته باشند:

- استفاده از یک Connection برای همهٔ Queryهای همان Scope.
- ترتیب اجرای قابل‌پیش‌بینی.
- استفاده از مسیر اجرای مشترک.
- ثبت Failure بدون نادیده‌گرفتن آن.
- جلوگیری از اجرای Queryهای بعدی پس از Failure ثبت‌شده.
- آزادسازی منابع در پایان.
- حفظ علت اصلی خطا.
- جلوگیری از بستن Connection توسط کد مصرف‌کننده.

اما این دو Scope نباید در این موارد یکسان شوند:

- وضعیت `autoCommit`.
- commit و rollback.
- معنای موفقیت نهایی.
- گزارش وضعیت ثبت‌شدن تغییرات.
- تضمین اتمیک بودن.

### تصمیم دربارهٔ Scopeهای تو‌در‌تو

در v2.0 هیچ nested scopeای پشتیبانی نمی‌شود: Transaction در Transaction، Connection در Connection، Connection در Transaction، و Transaction در Connection همگی fail-closed رد می‌شوند و Connection دوم نمی‌گیرند.

- nested Transaction همین حالا در `QueryBuilderExecutor.kt:36–50` رد می‌شود.
- guard مشترک scopeها باید تلاش nested را در scope بیرونی به‌عنوان Failure ثبت کند؛ حتی اگر callback آن Failure را نادیده بگیرد، scope بیرونی نباید موفق تلقی شود.
- Query از executor ریشه در همان thread callback scope نیز رد می‌شود تا اجرای مستقل راه فرار نباشد.
- executor scoped پس از پایان Scope هر Query را با Failure صریح رد می‌کند و Connection تازه نمی‌گیرد. ساخت query و عملیات صرفاً تحلیلی مثل `sql()`/`details()` اجرای Query نیستند و مجاز می‌مانند.
- Context فقط در thread مالک و به‌شکل synchronous قابل استفاده است؛ استفاده از Context scoped از thread دیگر، coroutine یا اجرای موازی رد می‌شود. پشتیبانی async/coroutine خارج از v2.0 است.

پیاده‌سازی Phase 2 از `TransactionGuard` مشترک و `TransactionExecutionScope` موجود برای رد nesting/cross-nesting و ثبت Failure در Scope بیرونی استفاده می‌کند. guard همچنان registry/thread-local است؛ اجرای موازی و async خارج از قرارداد باقی می‌ماند.

---

## ۹. اجرای Queryهای ساخته‌شده در فایل‌های مختلف

یکی از اهداف اصلی نسخهٔ 2.0 پشتیبانی از این الگو است:

### فایل A: ساخت Query

```kotlin
fun buildDeleteUserQuery(): IQueryRenderDeleteApi {
    // ساخت API/AST بدون اجرای Query یا گرفتن Connection
}
```

### فایل B: اجرای ترتیبی

```kotlin
fun deleteAndUpdateUsers() {
    val deleteQuery = buildDeleteUserQuery()
    val updateQuery = buildUpdateUserQuery()

    queryManager.connection { ctx ->
        val deleted = ctx.queryBuilder(deleteQuery).execute()
        if (deleted is ExecuteResult.Failure) return@connection deleted
        val updated = ctx.queryBuilder(updateQuery).execute()
        if (updated is ExecuteResult.Failure) return@connection updated
        ExecuteResult.Success(Unit)
    }
}
```

این نمونه قرارداد v2.0 را نشان می‌دهد و Query آماده را به‌معنای `IQueryRender...Api` می‌گیرد، نه SQL رندرشدهٔ `BuiltQuery` یا `BoundQuery`. API/AST در فایل A ساخته و بدون Connection به فایل B داده می‌شود؛ فایل B آن را با `ctx.queryBuilder(api)` به Context scope bind می‌کند. اجرای مستقیم Built/Bound خارج از دامنه است.

الزامات:

- ساخت Query نباید به Connection نیاز داشته باشد.
- اجرای Query باید از Context فعال استفاده کند.
- نتیجهٔ هر عملیات باید قابل‌بررسی باشد و Failureای که state مشترک مشاهده می‌کند latch شود.
- Failureهای validation/materialization در `first/get` و Failureهای framework-generated در `executeAs()` باید به state همان Scope latch شوند؛ callback نباید بتواند با برگرداندن `Success` آن‌ها را نادیده بگیرد.
- اجرای Query آماده نباید Query را مجدداً بسازد یا دوباره اجرا کند.
- اگر نتیجهٔ یک Query برای تصمیم‌گیری دربارهٔ Query بعدی لازم است، Context باید اجرای مرحله‌ای را ممکن کند.
- `QueryConsumer.first()/execute()` ردیف‌ها را داخل محدودهٔ `ResultSet` مادی‌سازی می‌کنند. Raw `ResultSet`ای که از overload پایین‌سطح callback گرفته می‌شود پس از پایان callback/بسته‌شدن Statement قابل نگه‌داری نیست.

---

## ۱۰. استفاده از زیرساخت فعلی

نتیجهٔ ممیزی این فصل:

### مواردی که باید حفظ شوند

- `OwnedConnection.kt` و helper مشترک `withOwnedConnection` برای عملیات independent کوتاه‌عمر.
- overloadهای `IQueryExecute` که `Connection` دریافت می‌کنند.
- مسیر اجرای `BuiltQuery`.
- مسیر اجرای `BoundQuery`.
- بسته‌شدن Statement و مصرف صحیح ResultSet.
- `QueryBuilderExecutor` و `TransactionExecutionScope`.
- قرارداد فعلی `ExecuteResult`.
- تست‌های موجود lifecycle و Transaction.

### شواهد اجرایی و قیود پیاده‌سازی

1. `withOwnedConnection` برای اجرای چند Query قابل reuse نیست: تابع آن در پایان همان operation Connection را می‌بندد؛ همچنین `internal` در ماژول `manager:execute` است و از `query:executor` API قابل‌فراخوانی نیست.
2. `Transaction Scope` می‌تواند از مسیر execution پایین‌سطح `QueryExecute(connection, ...)` استفاده کند؛ همین کار اکنون برای SELECT/INSERT/UPDATE/DELETE انجام می‌شود. مسئولیت commit/rollback و close باید در Transaction بماند.
3. `IQueryBuilderExecutor` Context عمومی Scope است؛ overloadهای `queryBuilder` و default fail-closed `transaction` موجودند. `connection` با همان شکل callback و default failure به آن افزوده می‌شود؛ state scoped برای جلوگیری از fallback مشترک خواهد بود.
4. `BuiltQuery` و `BoundQuery` در `IQueryExecute` overloadهای supplied-Connection دارند، اما `IQueryBuilderExecutor` آن‌ها را نمی‌پذیرد. این مسیر مستقیم در v2.0 لازم نیست؛ query APIهای چندفایلی با `ctx.queryBuilder(api)` اجرا می‌شوند.
5. نتیجهٔ SELECT عادی از `QueryConsumer.first()/execute()` پیش از بازگشت materialize می‌شود؛ اجرای callback پایین‌سطح باید `ResultSet` را داخل callback مصرف کند. هیچ API نباید آن را از محدودهٔ عمر Statement بیرون ببرد.
6. `executeOut()` در executor یک `QueryExecute()` مستقل و `BuiltQuery` می‌سازد؛ Transaction آن را در scope رد می‌کند. Connection Scope نیز آن را با همان سیاست fail-closed رد می‌کند؛ same-Connection adapter جزو v2.0 نیست.
7. `DatabaseConnection` فقط factory `build(): Connection` است و `IDatabaseConnection` نیز فقط همین متد را تعریف می‌کند؛ pool یا lease abstraction فعلی وجود ندارد. بر اساس تصمیم Phase 3، Scopeها lifecycle خود را در `query:executor` نگه می‌دارند و lease جدیدی افزوده نمی‌شود؛ dependency عمومی یا dependency cycle تازه لازم نیست.

نباید صرفاً برای رسیدن به نام `ConnectionSession` یا `ConnectionContext` کلاس جدیدی اضافه شود، مگر اینکه مسئولیت و ارزش مشخصی داشته باشد.

---

## ۱۱. برنامهٔ تست

### A. Connection Scope

- دریافت دقیقاً یک Connection برای Scope؛ در صورت شکست acquisition، callback اصلاً اجرا نشود.
- اجرای دو یا چند Query روی همان Connection.
- حفظ ترتیب اجرای Queryها.
- بستن Connection دقیقاً یک بار پس از موفقیت.
- بستن Connection پس از Failure.
- بستن Connection پس از Exception.
- حفظ Exception اصلی در حضور خطای cleanup.
- اجرای نشدن Queryهای بعدی پس از Failure ثبت‌شده.
- بازگشت همان callback `Failure` اگر پیش‌تر Failure در state ثبت نشده باشد.
- Failure validation در `first/get` پیش از dispatch، Failure materialization پس از دریافت ResultSet، و Failure typed mapping در `executeAs()` هرکدام جداگانه آزموده شوند؛ callback نتیجه را نادیده بگیرد و `Success` برگرداند، اما Failure نخست در state باقی بماند و Query بعدی dispatch نشود.
- برای typed mapping، هم Failure حاصل از exception معمولی mapper و هم null غیرمنتظرهٔ لیست materialized پوشش داده شوند؛ mapping موفق و Collection خالی نباید Failure ثبت کنند.
- یک آزمون Error در mapper تأیید کند Error به Failure عادی تنزل نمی‌کند و پس از cleanup طبق قرارداد Scope دوباره پرتاب می‌شود؛ خطاهای cleanup نیز نباید Failure نخست را بی‌دلیل جایگزین کنند.
- آزمون‌های `first/get` اثبات کنند Failure materialization که از callback عمومی برگردانده می‌شود پیش از رسیدن به callback کاربر ثبت شده است؛ برای این مسیر observer جداگانه لازم نیست.
- عدم فراخوانی خودکار `commit()` و `rollback()`.
- رد شدن Query پس از پایان Scope.
- عدم امکان استفاده از Context پس از بسته‌شدن Scope.
- اثبات رد `executeOut()` پیش از اجرای callback در Scope و ثبت تلاش در Failure state.
- حفظ `autoCommit` اولیه و نبود commit/rollback خودکار، با تست صریح eventهای JDBC.

### B. Transaction Scope

- اجرای تست‌های فعلی Transaction بدون رگرسیون.
- رد شدن هر نوع nested/cross-nested scope و ثبت Failure در scope بیرونی.
- عدم fallback از executor ریشه یا `executeOut()` به Connection مستقل.
- commit پس از موفقیت کامل.
- rollback پس از Failure.
- rollback پس از Exception.
- جلوگیری از commit پس از Failure ثبت‌شده.
- حفظ رفتار commit failure و rollback failure.
- حفظ وضعیت commit‌شده در صورت خطای close پس از commit موفق.
- حفظ محدودیت اجرای معمولی در هنگام Transaction فعال.

### C. Connection Ownership

- Connection مستقل دقیقاً یک بار بسته شود.
- Connection تزریق‌شده توسط executor بسته نشود.
- Connection متعلق به Transaction توسط helper اتصال مستقل بسته نشود.
- Connection تزریق‌شده توسط overloadهای `QueryExecute` بسته نشود.
- Connection متعلق به هر Scope دقیقاً یک‌بار close شود؛ Connection factory/acquisition برای آن Scope صرفاً یک‌بار فراخوانی شود. این معیار lease مستقل مشترک نمی‌خواهد.
- Statement دقیقاً یک بار بسته شود.
- ResultSet در محدودهٔ معتبر مصرف و بسته شود.
- callback مصرف‌کننده دوباره اجرا نشود.

### D. سازگاری API

- اجرای معمولی `BuiltQuery` بدون تغییر رفتار کند.
- اجرای معمولی `BoundQuery` بدون تغییر رفتار کند.
- مسیرهای SELECT، INSERT، UPDATE و DELETE از Query API پوشش داده شوند؛ TABLE تا ایجاد مسیر scoped same-Connection صریحاً خارج از دامنه است.
- نتیجه و Failure همان semantics فعلی را داشته باشند.
- consumer compile برای API عمومی حفظ شود.
- اجرای query API ساخته‌شده در فایل دیگر با `ctx.queryBuilder(api)` اثبات شود.
- اجرای مستقیم `BuiltQuery`/`BoundQuery` در v2.0 پیاده‌سازی نمی‌شود؛ consumer compile روی `ctx.queryBuilder(api)` را می‌پوشاند.

تست‌های lifecycle باید با mock یا fake کنترل‌شده اجرا شوند. تست‌های واقعی MySQL اختیاری و وابسته به محیط پیکربندی‌شده‌اند؛ نباید برای اعتبارسنجی اولیه به دیتابیس متصل شد.

#### شواهد اجرای focused Phase 2

فرمان اجراشده از ریشهٔ مخزن با `JAVA_HOME=D:\Dev\tools\jdk-24` و `GRADLE_USER_HOME` برابر `.gradle` مخزن:

```powershell
.\gradlew.bat :data_base:query:executor:test `
  --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" `
  --tests "gog.my_project.data_base.query.executer.manager.QueryConsumerExecuteAsTest" `
  --rerun-tasks --console=plain
```

نتیجهٔ XML: `TransactionExecutionTest` برابر ۴۲ تست و `QueryConsumerExecuteAsTest` برابر ۷ تست؛ هر دو suite صفر failure، صفر error و صفر skipped داشتند. تست‌های افزوده/به‌روزشدهٔ Connection Scope شامل connection واحد و binding order، Failure اجرای Query، validation و mapping، callback Exception و Failure، خطای close، حفظ Error اصلی، nesting/cross-nesting، رد executor ریشه و `executeOut()`، Context پس از پایان و خطای acquisition هستند. این اجرای fake/proxy رفتار واقعی MySQL را اثبات نمی‌کند.

---

## ۱۲. مراحل اجرا

### مرحلهٔ ۱: ممیزی

- **تکمیل‌شده در ۲۸ اکتبر ۲۰۲۶، فقط به‌صورت read-only:** خواندن پلن V1.0 و نسخهٔ اولیهٔ V2.0 ارائه‌شده در پیوست.
- بررسی `OwnedConnection.kt`، `IQueryExecute.kt`، `QueryExecute.kt`، `DatabaseConnection`، `IQueryBuilderExecutor`، `QueryBuilderExecutor`، `TransactionExecutionScope` و `TransactionExceptions`.
- بررسی مسیرهای SELECT، INSERT، UPDATE، DELETE، `executeOut`، QueryConsumer، resource lifetime، تست‌های proxy و وضعیت فعلی تست opt-in MySQL.
- ثبت staged/unstaged/untracked و snapshot هشت فایل LockForUpdate؛ در این ممیزی هیچ تغییری خارج از همین پلن انجام نشد.

هیچ build، تست، Gradle task یا اتصال دیتابیسی اجرا نشد.

### مرحلهٔ ۲: تثبیت قرارداد معماری

- **تکمیل‌شده در ۲۸ اکتبر ۲۰۲۶، فقط با تغییر همین سند:** مسیر `ctx.queryBuilder(api)` انتخاب شد؛ direct `BuiltQuery`/`BoundQuery` خارج از v2.0 ماند.
- امضای `connection { (ctx: IQueryBuilderExecutor) -> ExecuteResult<T> }` و default fail-closed مانند `transaction` تثبیت شد.
- مالکیت Connection Scope، عدم تغییر `autoCommit`، عدم commit/rollback، callback یک‌باره، Failure/Exception/Error/close، Context منقضی‌شده، nesting و منع `executeOut()` مشخص شدند.
- معماری هدف: state/guard scoped مشترک در `query:executor`؛ lifecycle و policyهای Connection Scope و Transaction مستقل می‌مانند. lease مشترک مستقل پیش‌فرض یا شرط پذیرش نیست.
- تصمیم Failure observation نهایی شده است: `first/get` با wrapper فعلی `QueryBuilderExecutor` Failure نهایی validation/materialization را به state ثبت می‌کنند؛ typed mapping در `QueryConsumer.executeAs()` از پل داخلی و بدون state تکراری استفاده می‌کند. این پل در Phase 1 به latch `TransactionExecutionScope` متصل شد و Phase 2 همان state و `TransactionGuard` را به Connection Scope نیز متصل کرد.

### مرحلهٔ ۳: هم‌راستاسازی زیرساخت scoped و lifecycle

- **تکمیل‌شده در Phase 3 با ممیزی کد و تست‌های regression:** `TransactionExecutionScope` شامل Connection مرجع، thread مالک، active lifetime، Failure latch و fatal Error است و برای هر دو Scope استفاده می‌شود؛ typed mapping نیز همان latch را از پل داخلی گزارش می‌کند. state دوم ساخته نشده است.
- `TransactionGuard` برای nesting/cross-nesting و دسترسی executor ریشه مشترک است. مسیر scoped SELECT/INSERT/UPDATE/DELETE از overloadهای supplied-Connection در `QueryExecute` می‌گذرد؛ `QueryExecute` اتصال تزریق‌شده را نمی‌بندد.
- Acquisition و close در `executeConnectionScope` و `executeTransaction` تکرار جزئی دارند، اما بررسی مسیرها نقص correctness یا دوبار‌بستن نشان نداد. فرق cleanup لازم است: Connection Scope فقط close می‌کند؛ Transaction در Failure rollback سپس close می‌کند و در موفقیت commit سپس close می‌کند. خطای close پس از commit نیز باید committed بودن را حفظ کند.
- **تصمیم نهایی:** lease مشترک مستقل استخراج نشود. معیار lease قبلی به معیار state/guard مشترک و lifecycle مستقلِ درست برای هر Scope اصلاح شد. یک abstraction با مالکیت close-once فقط زمانی ارزش دارد که بتواند این policyهای مجزا را بدون state اضافی و بدون ادغام خطاهای cleanup مدل کند؛ نیاز فعلی چنین چیزی را ثابت نمی‌کند. `withOwnedConnection` یک‌عملیاتی در `manager:execute` دست‌نخورده می‌ماند.

### مرحلهٔ ۴: افزودن Connection Scope

- **تکمیل‌شده در Phase 2:** متد عمومی `connection` با default fail-closed به `IQueryBuilderExecutor` و override آن به `QueryBuilderExecutor` افزوده شد.
- callback یک بار اجرا می‌شود و Context executor-bound برای SELECT/INSERT/UPDATE/DELETE همان Connection تازه را به `QueryExecute(connection, ...)` می‌دهد. `autoCommit` دست‌نخورده می‌ماند؛ commit/rollback خودکار و تزریق Connection خام نداریم.
- `executeOut()`، executor ریشه، nested و cross-nested Scopeها fail-closed رد می‌شوند؛ Failure در state مشترک ثبت، Query بعدی متوقف و Context پس از پایان غیرقابل اجرا می‌شود.
- Exception/Error و خطای close طبق این قرارداد مدیریت می‌شوند و تست‌های proxy اضافه/به‌روزرسانی شدند. direct `BuiltQuery`/`BoundQuery` و TABLE خارج از دامنه باقی ماندند.

### مرحلهٔ ۵: هم‌راستاسازی و رگرسیون Transaction

- **تکمیل‌شده و با تست focused Phase 3 تأییدشده:** `TransactionExecutionTest` شامل ۴۳ تست شد و commit، rollback پس از Failure/Exception/Error، validation و mapping Failure، commit/rollback/close errors، مالکیت Connection، nesting/cross-nesting، جلوگیری از مسیر مستقل، binding order و materialization را با proxy پوشش داد.
- `QueryConsumerExecuteAsTest` نیز ۷ تست را با موفقیت اجرا کرد و رگرسیون typed mapping و Failure propagation را تأیید کرد. `QueryExecuteContractTest` با ۲۷ تست موفق، مسیر one-shot در `manager:execute` را بدون تغییر قرارداد بررسی کرد.
- Transaction همچنان از `TransactionExecutionScope` استفاده می‌کند و runner مالک `autoCommit=false`، commit، rollback و close باقی می‌ماند. Connection Scope این policy را به ارث نمی‌برد.

### مرحلهٔ ۶: اعتبارسنجی و مستندات

- consumer compile مستقل و suiteهای کامل پیشین executor و `manager:execute` در آماده‌سازی Phase 3 اعتبارسنجی شده بودند؛ Phase 3 آن‌ها را دوباره اجرا نکرد.
- consumer fixture مستقل با dependency مستقیم فقط به executor، پس از افزودن probe برای `connection { ctx -> ctx.queryBuilder(updateApi).execute() }`، با `compileKotlin --rerun-tasks --no-build-cache` موفق شد (`BUILD SUCCESSFUL`, ۳۳ task اجراشده). این فقط دسترسی compile-time API را تأیید می‌کند و رفتار runtime را نمی‌آزماید.
- برای gap استفاده از Context از thread غیرمالک، تست proxy در `TransactionExecutionTest.kt` اضافه و در suite کامل executor اجرا شد: owner پیش از تلاش worker یک Query موفق اجرا کرد؛ worker با `IllegalStateException` رد شد، بدون dispatch یا close زودهنگام؛ owner پس از latch و Context نگه‌داشته‌شده پس از پایان Scope نیز بررسی شدند. XML تست جدید را موفق ثبت کرد.
- suite کامل executor با opt-in دیتابیس صریحاً خاموش اجرا شد: ۱۲۸ case در XML، صفر failure/error و ۷ skipped؛ ۱۲۱ case اجراشده موفق بودند. از ۹ case در `TransactionMySqlIntegrationTest`، ۷ مورد skipped و دو مورد تنظیمات/validation بدون اتصال بودند؛ هیچ اتصال MySQL برقرار نشد.
- Regression suite `manager:execute` نیز اجرا شد: ۳۱ case در XML، صفر failure/error و ۱ skipped؛ ۳۰ مورد اجراشده موفق بودند. تنها skip مربوط به `QueryExecuteMySqlIntegrationTest` بود؛ propertyهای اتصال خالی بودند و هیچ اتصال برقرار نشد.
- در build consumer و executor، Kotlin daemon نتوانست فایل marker موقت را به‌علت `AccessDeniedException` بسازد و Gradle از fallback بدون daemon استفاده کرد؛ buildها موفق ماندند. هشدار مستقل JVM target نیز ثبت شد: JDK 24 در Kotlin به target 22 تنزل می‌کند و targetهای Java/Kotlin ناسازگارند. مثال دیتابیس‌محور یا Integration واقعی اجرا نشد.

**نتیجهٔ Phase 3:** ممیزی lifecycle نشان داد state/guard مشترک و lifecycle مستقل runnerها برای قرارداد فعلی کافی است؛ lease مستقل نیاز فنی ندارد و اضافه نشد. اجرای focused سه XML تست را تأیید کرد: `TransactionExecutionTest`، ۴۳/۴۳ موفق؛ `QueryConsumerExecuteAsTest`، ۷/۷ موفق؛ `QueryExecuteContractTest`، ۲۷/۲۷ موفق. مجموع این اجرای focused ۷۷ تست، بدون failure/error/skip بود. تست‌های کامل executor و manager و consumer compile مستقل از اجرای قبلی معتبر باقی ماندند و تکرار نشدند. هیچ اتصال دیتابیسی برقرار نشد. هشت LockForUpdate staged حفظ شدند.

---

## ۱۳. محدودیت‌های Git و محیط

- پیش از تغییر، وضعیت staged، unstaged و untracked ثبت شود.
- هیچ تغییر موجودی reset، restore، حذف یا stage نشود.
- هیچ commit انجام نشود.
- هشت فایل `LockForUpdate` که قبلاً وضعیت `AM` داشتند باید دست‌نخورده باقی بمانند.
- تست دیتابیسی یا اتصال MySQL بدون مجوز و پیکربندی صریح انجام نشود.
- تغییرات نامرتبط در Transaction، `ExecuteResult`، Gradle و سایر ماژول‌ها حفظ شوند.
- در صورت نیاز به تغییر خارج از دامنه، ابتدا دلیل و اثر آن گزارش شود.

---

## ۱۴. معیارهای پذیرش

نسخهٔ 2.0 زمانی قابل‌قبول است که:

1. `IQueryBuilderExecutor.connection((ctx) -> ExecuteResult<T>)` و default fail-closed برای implementation فاقد پشتیبانی مطابق قرارداد مستند پیاده‌سازی و با consumer compile بررسی شده باشد.
2. `connection` و `transaction` از `TransactionExecutionScope`/`TransactionGuard` مشترک برای state و guard scoped استفاده کنند؛ lifecycle، ownership و policyهای `autoCommit`, commit/rollback و final result برای هر Scope جدا بمانند. lease مستقل شرط پذیرش نیست و فقط با نیاز فنی اثبات‌شده دوباره بررسی می‌شود.
3. query API ساخته‌شده در فایل دیگر از طریق `ctx.queryBuilder(api)` به‌ترتیب روی همان Connection اجرا شود؛ پارامترهای positional/occurrence بدون بازسازی یا ترتیب‌دهی مجدد حفظ شوند.
4. direct `BuiltQuery`/`BoundQuery` و TABLE صریحاً خارج از v2.0 بمانند، مگر آنکه مسیر عمومی scoped جداگانه تصویب شود.
5. Scope مالک Connection تازه باشد؛ close دقیقاً یک بار انجام شود و overloadهای supplied-Connection، Statement و ResultSet مالکیت فعلی را حفظ کنند.
6. Connection Scope `autoCommit` را تغییر ندهد، commit/rollback نکند و تضمین اتمیک بودن ندهد؛ Transaction رفتار فعلی را حفظ کند.
7. Failureهای execution، validation و materialization نهایی `first/get` و Failureهای framework-generated در typed mapping `executeAs()` در همان state Scope ثبت شوند؛ Query بعدی پیش از JDBC رد شود و callback نتواند Failure را با `Success` پاک کند. ثبت typed mapping از پل داخلی به latch مشترک انجام شود، بدون API عمومی یا state تکراری. Failure نهایی callback نیز اگر قبلاً latch نشده باشد نتیجهٔ Scope باقی بماند.
8. `executeOut()` پیش از callback رد شود، خطای rejection را در state بیرونی ثبت کند و امکان گرفتن Connection مستقل را ندهد.
9. Exception به Failure تبدیل و Error پس از cleanup دوباره پرتاب شود؛ خطای close علت اصلی را نپوشاند و در موفقیت، durable بودن احتمالی تغییرات را روشن کند.
10. پس از پایان Scope، اجرای Query با Context منقضی‌شده و استفاده از آن از thread غیرمالک رد شود؛ اطلاعات render/metadata غیر اجرایی به‌تنهایی Scope را تغییر ندهد.
11. nested و cross-nested Scopeها fail-closed رد شوند، Connection جدید نگیرند و Failure در Scope بیرونی ثبت شود.
12. **تأییدشده برای unit/proxy:** suite کامل executor شامل ۱۲۸ test case بود، ۱۲۱ موفق و ۷ skipped، بدون failure/error؛ Integration واقعی به‌علت opt-in خاموش اجرا نشد. تست‌های proxy lifecycle، Failure/Exception/Error، ownership، عدم fallback، binding order و regression در suite حضور داشتند.
13. consumer compile، سازگاری implementationهای موجود و نبود تغییر ناخواسته در Git تأیید شود.

### ارزیابی معیارها پس از Phase 2 (ثبت تاریخی)

1. **پیاده‌سازی و compile مستقل تأییدشده؛ runtime suite pending:** `connection` با default fail-closed به interface افزوده شد. تست fake آن نشان می‌دهد callback و Query روی implementation فاقد پشتیبانی اجرا نمی‌شوند؛ fixture مستقل با dependency مستقیم executor، از بیرون ماژول هم `connection` و fluent Query را compile کرد. این شاهد compile-time است، نه اجرای واقعی consumer.
2. **جزئی:** state و guard موجود بین Transaction و Connection به‌کار می‌روند، اما lease مستقل مشترک برای acquisition/close استخراج نشده؛ این بخش هنوز معیار تکمیل کل v2.0 را برآورده نمی‌کند.
3. **پیاده‌سازی و با proxy تأییدشده:** SELECT، INSERT، UPDATE و DELETE از Context یک Connection استفاده کردند؛ ترتیب پارامترهای `11`, `22`, `33` و binding خالی SELECT در تست ثبت شد.
4. **تأییدشده در محدودهٔ تغییر:** API مستقیم `BuiltQuery`/`BoundQuery` یا TABLE اضافه نشد.
5. **تأییدشده با تست proxy:** Connection Scope یک بار factory را صدا می‌زند و Connection را یک بار می‌بندد؛ Statement و ResultSet نیز بسته می‌شوند و خود `QueryExecute` مالک Connection تزریق‌شده نیست.
6. **تأییدشده با تست proxy:** `autoCommit` تغییر نکرد و هیچ commit/rollbackی از Connection Scope صادر نشد.
7. **تأییدشده با تست proxy:** Failure اجرای Query، validation و typed mapping به state می‌رسند؛ Query بعدی رد می‌شود. مسیرهای Phase 1 نیز در اجرای مجدد سالم ماندند.
8. **تأییدشده با تست proxy:** `executeOut()` در Scope رد شد و نه callback آن اجرا شد و نه Connection مستقل ساخته شد.
9. **تأییدشده با تست proxy:** Exception به Failure برمی‌گردد؛ Error اصلی پس از cleanup حفظ می‌شود؛ خطای close در موفقیت پیام روشن دارد و در شکست، علت اصلی حفظ می‌شود.
10. **تأییدشده با تست proxy:** `TransactionExecutionTest.connection scope rejects query from non owner thread without dispatching or closing early` thread غیرمالک را رد کرد؛ Query بعدی در thread مالک همان Failure latchشده را گرفت؛ dispatch اضافی و close پیش از پایان callback رخ نداد؛ Context نگه‌داشته‌شده پس از پایان Scope نیز رد شد.
11. **تأییدشده با تست proxy:** nested و cross-nested تلاش‌ها fail-closed رد شدند، callback تو‌در‌تو اجرا نشد و Connection دوم گرفته نشد.
12. **تأییدشده برای unit/proxy:** suite کامل executor شامل ۱۲۸ test case بود، ۱۲۱ موفق و ۷ skipped، بدون failure/error؛ `manager:execute` نیز ۳۱ case داشت، ۳۰ موفق و ۱ skipped، بدون failure/error. Integration واقعی به‌علت opt-in خاموش اجرا نشد.
13. **تأییدشده با compile و بازبینی Git:** fixture با dependency مستقیم فقط executor `connection` و fluent Query را compile کرد. وضعیت staged/unstaged/untracked در پایان با snapshot آغازین مقایسه شد؛ هیچ path جدیدی stage نشد و هشت LockForUpdate با وضعیت `AM` حفظ شدند.

بنابراین اعتبارسنجی رفتاری Phase 2 با تست proxy و compile مستقل موفق بود؛ وضعیت آن مرحله تاریخی است و تصمیم نهایی دربارهٔ lifecycle در بخش ارزیابی Phase 3 ثبت شده است.

### ارزیابی معیارهای هم‌راستاسازی پس از Phase 3

1. **تأییدشده با شواهد قبلی:** consumer compile مستقل با dependency مستقیم executor موفق بود؛ Phase 3 برای جلوگیری از تست تکراری آن را دوباره اجرا نکرد.
2. **تأییدشده با تحلیل کد و تست regression:** state/guard بین هر دو Scope مشترک است؛ lease مستقل لازم نیست. Lifecycleهای جدا قرارداد هر Scope را حفظ می‌کنند.
3. **تأییدشده با تست proxy:** Queryهای scoped از همان Connection و ترتیب binding مورد انتظار استفاده می‌کنند.
4. **تأییدشده با تحلیل کد:** API سطح بالا برای اجرای مستقیم `BuiltQuery`/`BoundQuery` اضافه نشده و مسیر one-shot جدا مانده است.
5. **تأییدشده با تست proxy:** Connection مالک هر Scope فقط یک بار بسته می‌شود؛ overloadهای supplied-Connection مالک close نیستند و Statement/ResultSet در مسیر Query پاک‌سازی می‌شوند.
6. **تأییدشده با تست proxy:** Connection Scope autoCommit/commit/rollback را دست‌کاری نمی‌کند؛ Transaction مسئول autoCommit=false، commit، rollback و close است.
7. **تأییدشده با تست proxy:** Failureهای execution، validation و typed mapping در state مشترک ثبت و Queryهای بعدی متوقف می‌شوند.
8. **تأییدشده با تست proxy:** `executeOut()` و اجرای executor ریشه در Scope نمی‌توانند از Scope عبور کنند.
9. **تأییدشده با تست proxy:** Exception/Error و خطاهای commit/rollback/close مطابق policy جداگانه حفظ می‌شوند؛ close پس از commit به‌عنوان نتیجهٔ committed مشخص می‌ماند.
10. **تأییدشده با تست proxy قبلی:** thread غیرمالک و Context پس از پایان Scope رد می‌شوند؛ Query dispatch یا close زودهنگام رخ نمی‌دهد.
11. **تأییدشده با تست proxy:** nesting و cross-nesting fail-closed رد می‌شوند.
12. **تأییدشده برای regression هدفمند Phase 3:** XMLها ۴۳ تست Transaction، ۷ تست `executeAs` و ۲۷ تست `manager:execute` را بدون failure/error/skip ثبت کردند. suite کامل executor/manager نتیجهٔ اجرای قبلی است و در Phase 3 تکرار نشد.
13. **تأییدشده با وضعیت Git:** snapshot پیش و پس از کار ۸ staged، ۳۵ unstaged و ۱۲ untracked (۴۷ مسیر status) داشت؛ index و هشت LockForUpdate با وضعیت `AM` ثابت ماندند. تنها محتوای پلن موجود و از قبل untracked در این Phase به‌روزرسانی شد.

**محدودیت اعتبارسنجی:** آزمون‌های این Phase از JDBC fake/proxy استفاده کردند. رفتار واقعی MySQL و `FOR UPDATE` در این اجرا تأیید نشد و هیچ اتصال دیتابیسی برقرار نشد. کل قراردادهای unit-level دو Scope تأیید شدند؛ پوشش Integration دیتابیسی همچنان جداگانه اجرا‌نشده است.

---

## ۱۵. تصمیم معماری تثبیت‌شده

- Public callback Context همان `IQueryBuilderExecutor` است؛ interface عمومی جدیدی برای Connection/Transaction Scope لازم نیست.
- `connection` از شکل `transaction` تبعیت می‌کند و در interface default fail-closed دارد. `ctx.queryBuilder(api)` تنها مسیر سطح بالا در v2.0 است.
- **وضعیت واقعی و تصمیم Phase 3:** `TransactionExecutionScope` و `TransactionGuard` state/guard هر دو runner هستند. abstraction مستقل owned-Connection lease وجود ندارد و برای قرارداد فعلی لازم تشخیص داده نشد؛ acquisition و lifecycle در runnerهای مربوط می‌مانند.
- Connection Scope: Connection تازه، عدم تزریق به‌صورت public، عدم تغییر autoCommit، عدم commit/rollback، اجرای ترتیبی، close-once.
- Transaction Scope: همان مسیر scoped، با `autoCommit=false` و policy فعلی commit/rollback/cleanup.
- اجرای `BuiltQuery`/`BoundQuery` مستقیم و TABLE خارج از این نسخه‌اند. `withOwnedConnection` یک‌عملیاتی در `manager:execute` بدون تغییر می‌ماند.
- تمام nested/cross-nested Scopeها و `executeOut()` fail-closed رد می‌شوند؛ Context پس از پایان Query اجرا نمی‌کند.

این تصمیم‌ها بر امضاهای فعلی `IQueryBuilderExecutor`, `QueryBuilderExecutor`, `QueryExecute` و `TransactionExecutionScope` تکیه دارند. آن‌ها به‌تنهایی مجوز تغییر source نیستند.

## ۱۶. نتیجهٔ ممیزی: شواهد و تصمیم نهایی دربارهٔ Failure

### تأییدشده با شواهد فعلی

- `IQueryBuilderExecutor` در `data_base/query/executor/src/main/kotlin/interfaces/IQueryBuilderExecutor.kt` تعریف شده است. `connection` و `transaction` هر دو default fail-closed دارند و implementation بدون پشتیبانی callback یا اجرای مستقل را فراخوانی نمی‌کند. overloadهای `queryBuilder` همان query API را به executor bind می‌کنند؛ `executeOut` مسیر جداگانهٔ low-level است.
- تنها implementer production این interface، `QueryBuilderExecutor` است. Test doubleها در ماژول‌ها وجود دارند؛ در نتیجه متد جدید باید default fail-closed داشته باشد تا implementerهای فعلی منبعاً مجبور به override نشوند.
- `QueryBuilderExecutor` نگه‌دارندهٔ Connection factory است؛ constructor پیش‌فرض از `DatabaseConnection().build()` استفاده می‌کند. `connection` و `transaction` هرکدام runner scoped خود را دارند.
- Transaction یک Connection می‌گیرد، `autoCommit=false` می‌کند، callback را اجرا می‌کند و بر اساس نتیجه commit/rollback/close را انجام می‌دهد. Connection Scope یک Connection می‌گیرد، `autoCommit` را دست‌نخورده می‌گذارد و فقط close می‌کند. هر دو از `TransactionExecutionScope` و `TransactionGuard` استفاده می‌کنند؛ ownership/cleanup runner-specific است و close-once lease مستقل عمداً وجود ندارد.
- عملیات SELECT/INSERT/UPDATE/DELETE در executor scoped با overloadهای `QueryExecute(connection, ...)` اجرا می‌شوند؛ بدون Scope، مسیر مستقل `QueryExecute` مصرف می‌شود. Failure به state Scope ثبت می‌شود و Query بعدی رد می‌شود.
- Query از executor ریشه در thread فعال هر Scope با guard رد می‌شود؛ `executeOut` نیز پیش از callback رد و Failure آن ثبت می‌شود، چون مسیر interface یک `QueryExecute()` مستقل می‌سازد.
- `QueryConsumer.sql()/details()` در `result/QueryConsumer.kt:59–85` فقط render/analyze می‌کنند. `QueryConsumer.first()/execute()` در خطوط 23–29 از executor استفاده می‌کنند. `IQueryBuilderExecutor.first/get` در `interfaces/IQueryBuilderExecutor.kt:95–193` validation را پیش از dispatch انجام می‌دهند و `ResultSet.use` برای materialization دارند.
- `QueryBuilderExecutor.first/get` هر دو `super.first/get` را داخل `executeInScope` می‌پیچند. نتیجه با `scope.recordResult` پیش از callback مصرف‌کننده ثبت می‌شود؛ بنابراین Failureهای validation، materialization و اجرای JDBC به state Transaction و Connection Scope می‌رسند، حتی اگر callback آن Failure را نادیده بگیرد.
- `QueryConsumer.executeAs()` در `result/QueryConsumer.kt:31–56` پس از پایان `execute()/get` ردیف‌ها را map می‌کند. پیش از Phase 1، mapper exception معمولی به `ExecuteResult.Failure(IllegalStateException(...))` تبدیل می‌شد و نبودن فهرست materialized نیز Failure می‌ساخت، اما این دو نتیجه پس از ساخت از `executeInScope` عبور نمی‌کردند. Phase 1 این شکاف را با گزارش داخلی به همان Scope برطرف کرد. Error mapper همچنان به Failure عادی تبدیل نمی‌شود؛ Scope آن را fatal ثبت می‌کند تا پس از cleanup همان Error دوباره پرتاب شود.
- در `data_base/manager/execute/src/main/kotlin/manager/QueryExecute.kt:36–173`، overloadهای مستقل `BoundQuery` برای SELECT/INSERT/UPDATE/DELETE از `withOwnedConnection` استفاده می‌کنند؛ overloadهای دارای Connection در خطوط مجاور، Connection را نمی‌بندند و اجرای Statement را به helper داخلی `executeWithPreparedStatement` می‌سپارند (خط 328 به بعد). اجرای `BuiltQuery` از `IQueryExecute.execute()` در `interfaces/IQueryExecute.kt:179–210` و helper مشابه عبور می‌کند.
- overloadهای `IQueryExecute` با Connection تزریق‌شده در interface به‌صورت پیش‌فرض Failure صریح می‌دهند؛ `QueryExecute` implementation واقعی آن‌ها را فراهم می‌کند. بنابراین Context scoped باید در زمان اجرا پیاده‌سازی‌ای را به‌کار گیرد که supplied-Connection را پشتیبانی کند و نباید به overload مستقل fallback کند.
- `OwnedConnection.kt:6–8` در ماژول `manager:execute` یک Connection مستقل برای مدت یک operation می‌گیرد و با Kotlin `use` می‌بندد. `DatabaseConnection.build()` در `manager:connection/DatabaseConnection.kt:9–26` تنها factory `DriverManager.getConnection` است؛ `IDatabaseConnection.kt:5–8` lifecycle یا lease دیگری تعریف نمی‌کند. `dbPoolSize` در `data_base/core/src/main/kotlin/data_base/DatabaseConfig.kt:11` و builder/default config تعریف شده، اما در کد production جست‌وجوشده `DataSource` یا pool implementation یافت نشد؛ property تنظیمی به‌تنهایی اثبات pooling نیست.
- در مسیر one-shot، `use` exception بدنه را در صورت خطای close حفظ و close را suppressed می‌کند؛ اگر operation یک مقدار `ExecuteResult.Failure` برگرداند اما close exception بدهد، `use` آن close exception را پرتاب می‌کند. قرارداد close در Scope عمداً نتیجه را به `Failure` تشخیصی تبدیل می‌کند و باید در proxy test از تغییر رفتار one-shot جدا نگه داشته شود.
- مسیرهای production ساخت JDBC Connection در این ممیزی به factory `DatabaseConnection.build()` محدودند؛ MigrationExecutor از `QueryExecute` استفاده می‌کند. تست‌های integration/fake به‌طور مستقل `DriverManager` یا proxy می‌سازند و مالکیت آن‌ها با production path یکی نیست.
- `TransactionExceptions.kt` خطای commit نامعلوم را با `TransactionCommitException`، خطای close پس از commit را با `TransactionCompletionException(committed=true)` و Error متناظر را با `TransactionCompletionError` از نتیجهٔ rollback تفکیک می‌کند.
- `TransactionExecutionTest.kt` و `QueryExecuteContractTest.kt` پوشش proxy/unit برای lifecycle، ownership و رفتار Transaction دارند. `TransactionMySqlIntegrationTest.kt` شامل سناریوهای opt-in و دو اتصال مستقل برای `FOR UPDATE` است؛ پلن V1.0 ثبت می‌کند که اجرای قبلی آن با نبود پیکربندی MySQL پنج تست را skipped کرده است. این ممیزی آن تست‌ها را اجرا نکرده و رفتار واقعی دیتابیس را تأیید نمی‌کند.
- شواهد اجرای ثبت‌شده در پلن V1.0 تاریخی‌اند: `QueryExecuteContractTest` با ۲۶ تست موفق؛ اجرای کامل executor با ۹۷ تست، صفر failure/error و ۵ skipped؛ و اجرای focused `TransactionMySqlIntegrationTest` با ۷ مورد، صفر failure/error و ۵ skipped. این اعداد نتیجهٔ اجرای این ممیزی نیستند و پنج skip اثبات Integration واقعی نیست.

### تصمیم‌های تثبیت‌شده بر مبنای ممیزی

- `BuiltQuery`/`BoundQuery` آماده از Context فعلی قابل اجرا نیستند و برای نیاز چندفایلی لازم هم شناخته نشده‌اند؛ v2.0 فقط `IQueryRender...Api` و `ctx.queryBuilder(api)` را پشتیبانی می‌کند.
- `withOwnedConnection` internal و operation-level است؛ آن را multi-query Scope پایه نمی‌کنند و در Phaseهای 2 و 3 تغییر نکرد. Connection Scope و Transaction هرکدام lifecycle خود را در runner مدیریت می‌کنند؛ Phase 3 نیاز به lease scoped مشترک را رد کرد.
- `executeOut()` داخل هر Scope رد می‌شود؛ چون callback آن به `IQueryExecute` مستقل و مسیر اتصال جدا دسترسی می‌دهد. متد `Unit` است و rejection با exception صریح انجام می‌شود، نه با مقدار بازگشتی `Failure`.
- Scopeهای Query API فقط SELECT/INSERT/UPDATE/DELETE را تضمین می‌کنند. TABLE تا وجود مسیر same-Connection جدا خارج از دامنه است.
- Connection Scope `autoCommit` را تغییر نمی‌دهد و اتمیک نیست؛ Transaction همچنان روی Connection تازه `autoCommit=false` می‌کند.

### تصمیم نهایی: Failureهای تولیدشده در مسیر scoped Query latch می‌شوند

1. Failureهای نهایی `first/get` از validation و materialization هم‌اکنون به state می‌رسند، چون wrapperهای `QueryBuilderExecutor.first/get` نتیجهٔ `super.first/get` را از `executeInScope` عبور می‌دهند. برای این مسیر API یا observer جدید لازم نیست.
2. Failureهای framework-generated در `QueryConsumer.executeAs()` اکنون از observer/helper داخلی به executor scoped گزارش می‌شوند؛ `QueryBuilderExecutor` آن‌ها را با `recordFailure` در همان `TransactionExecutionScope` ثبت می‌کند. همین state برای Transaction و Connection Scope به‌کار می‌رود؛ mapper exception همچنان به `IllegalStateException("Failed to map SELECT result row N", cause)` تبدیل و به‌عنوان Failure برگردانده می‌شود، و نبودن فهرست materialized نیز Failure مشخص می‌سازد. اگر mapper یک `Error` پرتاب کند، Scope آن را fatal ثبت می‌کند و همان Error بدون تبدیل دوباره پرتاب می‌شود. این پل API عمومی یا state دوم اضافه نمی‌کند. در executor غیر-scoped یا implementation دیگری از `IQueryBuilderExecutor` که Scope شناخته‌شده‌ای ندارد، مسیر همچنان نتیجهٔ محلی `executeAs()` را برمی‌گرداند و Scopeای برای latch وجود ندارد.
3. نخستین Failure حفظ می‌شود و اجرای Query بعدی در Scope رد می‌شود. اگر callback با وجود Failure ثبت‌شده `Success` برگرداند، نتیجهٔ Scope همان Failure است. در Transaction این رفتار rollback را الزام می‌کند؛ در Connection Scope فقط اجرای بعدی متوقف و Connection بسته می‌شود، بدون rollback خودکار. اگر `autoCommit=true` باشد، DML موفق قبلی ممکن است قبلاً پایدار شده باشد.
4. اگر callback خود `Failure` برگرداند و Failure مربوط به Query قبلاً latch نشده باشد، همان نتیجهٔ callback حفظ می‌شود: Transaction rollback می‌کند و Connection Scope بدون commit/rollback خودکار پایان می‌یابد. قرارداد latch شامل هر `ExecuteResult` دلخواهی که کاربر مستقل از مسیر Query بسازد یا صرفاً در یک متغیر نگه دارد نیست.
5. Exceptionهای mapper که `executeAs()` طبق قرارداد فعلی به Failure تبدیل می‌کند باید latch شوند. Error نباید به Failure عادی تبدیل شود؛ اگر Error از مسیر mapper بیرون بیاید، Scope cleanup می‌کند و Error را دوباره پرتاب می‌کند. خطای rollback/close باید طبق قرارداد موجود ثانویه بماند و علت نخست را بی‌دلیل جایگزین نکند.
6. تست‌های پذیرش باید Failure validation پیش از dispatch، Failure materialization از ResultSet، mapper exception در `executeAs()`, Failure نادیده‌گرفته‌شده همراه با Query بعدی، rollback تراکنش، پایان Connection Scope بدون rollback خودکار، حفظ Error و عدم تغییر رفتار executor غیر-scoped را جداگانه اثبات کنند. در Phase 1، `QueryConsumerExecuteAsTest` و `TransactionExecutionTest` مسیر mapping موفق، mapping exception latchشده، جلوگیری از Query بعدی، rollback، حفظ و بازپرتاب Error و رگرسیون موجود `first/get` را پوشش دادند؛ validation/materialization `first/get` از قبل latch می‌شدند و رفتارشان تغییر نکرد.

**در قرارداد Failure و lifecycle مشترک تصمیم باز باقی نمانده است:** state/guard مشترک و lifecycle جداگانه تثبیت شده‌اند و lease مستقل لازم نیست. این نتیجه به‌تنهایی پذیرش کامل نیست: Integration واقعی دیتابیس اجرا نشده و root-executor از thread غیرمالک نیز در تست پوشش ندارد. قرارداد هدف no-bypass اکنون در Phase 6 انتخاب شده، اما تا پیاده‌سازی و تست مستقل تأییدشده محسوب نمی‌شود.

## ۱۷. ممیزی نهایی پذیرش Phase 4

**تاریخ ممیزی:** ۲۹ اکتبر ۲۰۲۶  
**دامنه:** بررسی read-only کد، تست‌ها، XMLهای باقی‌مانده و سوابق اعتبارسنجی. در این Phase هیچ build/test/Gradle task و هیچ اتصال دیتابیسی اجرا نشد. هیچ source، test یا Gradle file تغییر نکرد.

این جدول snapshot پذیرش در پایان Phase 4 است؛ نتیجهٔ بعدی معیار root executor و هم‌زمانی در بخش Phase 6 همین سند ثبت شده است.

### وضعیت معیارهای پذیرش

| معیار | وضعیت | شواهد و محدودیت |
|---|---|---|
| `transaction {}` یک Connection می‌گیرد، `autoCommit=false` می‌کند، در موفقیت commit و در Failure/Exception/Error rollback و سپس cleanup می‌کند. | **تأییدشده با تست** | `QueryBuilderExecutor.kt:159–255`; تست‌های `successful callback commits once...`, `query failure rolls back...`, `callback exception...`, `callback Error...` در `TransactionExecutionTest.kt`; XML focused با ۴۳/۴۳ موفق. |
| `connection {}` یک Connection مشترک می‌دهد، `autoCommit` را دست نمی‌زند، commit/rollback خودکار ندارد و Connection را یک‌بار می‌بندد. | **تأییدشده با تست** | `executeConnectionScope` در `QueryBuilderExecutor.kt:53–139`; تست‌های shared connection/no transaction control، Failure، close-once و close failure؛ XML focused موفق. این رفتار اتمیک بودن را تضمین نمی‌کند. |
| SELECT/INSERT/UPDATE/DELETE در Scope از Connection همان Scope استفاده می‌کنند و ترتیب binding حفظ می‌شود. | **تأییدشده با تست** | dispatchهای scoped در `QueryBuilderExecutor.kt:257–353` به overloadهای `QueryExecute(connection, ...)` می‌روند؛ تست `select insert update and delete execute through one connection in binding order` در XML موفق است. |
| Failureهای Query، validation و mapping به state مشترک latch می‌شوند و Query بعدی رد می‌شود؛ Failure نخست حفظ می‌شود. | **تأییدشده با تست** | `executeInScope` در `QueryBuilderExecutor.kt:391–452`, `TransactionExecutionScope.kt:23–49`, و observer داخلی `QueryConsumer.executeAs()`؛ تست‌های connection/transaction برای execution، validation، mapping و ignored Failure؛ XMLهای `TransactionExecutionTest` و `QueryConsumerExecuteAsTest` موفق‌اند. |
| Failure برگشتی خود callback، Exception و Error مطابق قرارداد پردازش می‌شوند؛ Error callback/query/mapper پس از cleanup عادی به Failure تبدیل نمی‌شود. | **تأییدشده با تست** | شاخه‌های callback در `QueryBuilderExecutor.kt:79–126,185–230`; تست‌های callback Failure/Exception/Error و mapper/query Error در `TransactionExecutionTest`; `QueryConsumerExecuteAsTest` با ۷/۷ موفق. |
| خطای commit موجب تلاش rollback می‌شود؛ rollback exception خطای اصلی را جایگزین نمی‌کند؛ close پس از commit، committed بودن را حفظ می‌کند. | **تأییدشده با تست** | `QueryBuilderExecutor.kt:232–254,497–524`; تست‌های commit failure، rollback exception suppression و close-after-commit؛ XML focused موفق. |
| ترکیب‌های هدف Phase 5 برای rollback exception و close exception، rollback `Error` و close failure، و حفظ callback `Error` در cleanup runtime آزمایش شده‌اند. | **تأییدشده با تست برای ترکیب‌های هدف؛ پوشش کامل تمام ترکیب‌های Error باز است** | `rollbackAndClose` در `QueryBuilderExecutor.kt:497–524`; تست‌های `rollback and close failures retain the query failure and cleanup order`, `rollback Error remains fatal and retains query and later close failures`, و `callback Error remains primary when rollback Error and close failure occur` در `TransactionExecutionTest.kt`. XML نهایی ۴۸/۴۸ موفق است. ترکیب close `Error` با rollback failure به‌طور جداگانه آزمایش نشده است. |
| Statement و ResultSet در مسیر Query بسته می‌شوند و ResultSet پیش از پایان Scope materialize می‌شود. | **تأییدشده با تست** | `IQueryBuilderExecutor.kt:109–193` و `QueryExecute.kt:310–328`; تست materialization و تست‌های ownership/close در `TransactionExecutionTest`; XML focused موفق. |
| nested و cross-nested Scope، استفاده از Context منقضی‌شده و Query از Context در thread غیرمالک fail-closed هستند. | **تأییدشده با تست** | `TransactionGuard` و `TransactionExecutionScope` در `TransactionExecutionScope.kt:7–82`; تست‌های nesting/cross-nesting، retained consumer و non-owner thread؛ XML `TransactionExecutionTest` موفق است. این تأیید مربوط به Context scoped است. |
| executor ریشه و `executeOut()` از Scope عبور نمی‌کنند، از جمله در thread غیرمالک. | **در snapshot پایان Phase 4: تأییدنشده؛ نتیجهٔ Phase 6: تأییدشده با تست** | این snapshot تاریخی در Phase 4 نشان می‌داد root executor از worker thread مسیر مستقل می‌گرفت. Phase 6 آن رفتار را با coordinator per-root تغییر داد؛ تست‌های `root executor from non owner thread is rejected during active connection scope`, `root executor from non owner thread is rejected during active transaction`, `root query and executeOut are rejected during starting before a connection is opened` و تست‌های sealed Scope این قرارداد را بررسی کردند. XML نهایی Phase 6: ۵۳ تست Transaction و ۷ تست executeAs، بدون failure/error/skip؛ جزئیات در انتهای بخش Phase 6 است. |
| مسیر one-shot `withOwnedConnection` و مالکیت Connectionهای تزریق‌شده بدون تغییر ناخواسته حفظ شده‌اند. | **تأییدشده با تست** | `OwnedConnection.kt:6–8` و overloadهای independent/supplied-Connection در `QueryExecute.kt`; تست `ordinary query execution still owns and closes its independent connection` و `QueryExecuteContractTest` در اجرای focused موفق‌اند. مسیر one-shot جدا از multi-query Scope است. |
| implementation فاقد پشتیبانی از Scope به callback یا Connection مستقل fallback نمی‌کند. | **تأییدشده با تست** | defaultهای fail-closed در `IQueryBuilderExecutor.kt:42–61`; تست `executor without scoped support fails closed without invoking callback or query execution` در `TransactionExecutionTest`؛ XML focused موفق. |
| API عمومی با consumer مستقل دارای dependency مستقیم فقط به executor قابل کامپایل است. | **تأییدشده با تست** | fixture `.tmp-executor-api-consumer-20261028` با dependency مستقیم executor و composite-build substitution، `connection`, `transaction`, query consumers, typed/raw execution, `sql/details`, `ExecuteResult` و extensionها را compile کرده است (`BUILD SUCCESSFUL`, ۳۳ task در گزارش قبلی). این فقط شاهد compile-time است و در Phase 4 تکرار نشد. |
| رفتار واقعی commit/rollback/cleanup در MySQL و persistence از Connection مستقل تأیید شده است. | **ناقص یا تأییدنشده** | `TransactionMySqlIntegrationTest.kt` شامل تست‌های opt-in است، اما در سوابق suite کامل ۷ سناریوی دیتابیسی skipped بودند و ۲ تست صرفاً پیکربندی/عدم اتصال را بررسی می‌کردند. XML اجرای کامل در پوشهٔ فعلی باقی نمانده؛ شمارش تاریخی در بخش بعد تفکیک شده است. هیچ اتصال MySQL مجاز یا برقرار نشد. |
| `FOR UPDATE` واقعاً قفل ردیف را میان دو Connection مستقل نگه می‌دارد و پس از commit/rollback آزاد می‌کند. | **ناقص یا تأییدنشده** | دو تست opt-in با Connectionهای مستقل، latch و timeout برای commit و rollback در `TransactionMySqlIntegrationTest.kt:230–340` وجود دارند، اما به‌علت نبود opt-in اجرا نشده‌اند. تست رندر SQL به‌تنهایی رفتار قفل MySQL را اثبات نمی‌کند. |
| state/guard مشترک و lifecycle مستقل Connection/Transaction، بدون lease مستقل، با قراردادها سازگار است. | **تأییدشده با بررسی کد** | `executeConnectionScope` و `executeTransaction` lifecycleهای جدا دارند و `TransactionExecutionScope`/`TransactionGuard` state و guard را به‌اشتراک می‌گذارند. proxy tests close-once و تفاوت close عادی، rollback-then-close و close پس از commit را پوشش می‌دهند؛ refactor lease مشترک ضرورت correctness ندارد. |
| اجرای مستقیم `BuiltQuery`/`BoundQuery` از Context، coroutine و query موازی روی یک Connection جزو پذیرش V2.0 هستند. | **خارج از دامنهٔ V2.0** | مسیر عمومی V2.0 `ctx.queryBuilder(api)` است؛ این قابلیت‌ها در قرارداد دامنهٔ نسخه خارج گذاشته شده‌اند. |

### نتایج تست و XMLهای قابل دسترس

- XMLهای فعلی اجرای focused Phase 3: `TransactionExecutionTest.xml` برابر ۴۳ تست، ۰ failure، ۰ error، ۰ skipped؛ `QueryConsumerExecuteAsTest.xml` برابر ۷ تست، ۰ failure، ۰ error، ۰ skipped؛ `QueryExecuteContractTest.xml` برابر ۲۷ تست، ۰ failure، ۰ error، ۰ skipped. جمع این سه اجرای focused: ۷۷ موفق، بدون failure/error/skip. زمان XMLها پس از آخرین تغییر زمان‌دار source این کلاس‌هاست.
- سوابق XML اجرای کامل پیشین که در Phaseهای قبلی ثبت شد: executor برابر ۱۲۸ case، ۱۲۱ موفق و ۷ skipped، بدون failure/error؛ `manager:execute` برابر ۳۱ case، ۳۰ موفق و ۱ skipped، بدون failure/error. XML کامل آن دو suite اکنون در پوشه‌های `build/test-results/test` موجود نیست؛ این اعداد به‌عنوان نتیجهٔ تاریخی ثبت می‌شوند و در این ممیزی دوباره اجرا نشدند.
- در اجرای کامل executor، `TransactionMySqlIntegrationTest` برابر ۹ case ثبت شده بود: ۲ تست تنظیمات/عدم اتصال اجرا شدند و ۷ سناریوی دیتابیس به‌علت خاموش بودن opt-in skipped شدند. این نتیجه Integration واقعی نیست.
- XMLهای فعلی فقط سه کلاس focused بالا را نگه داشته‌اند؛ XML اجرای Integration MySQL در مسیر فعلی موجود نیست. `TransactionMySqlIntegrationTest.settingsOrSkip()` به `execute.test.db.transaction.enabled=true` و هر پنج property اتصال نیاز دارد و نام schema را با `qb_tx_test_` اعتبارسنجی می‌کند. تا ارائهٔ schema ازپیش‌ساخته‌شده، حساب محدود و تنظیمات صریح و تأیید اجرای کاربر، تست Integration اجرا نشود.

### هشدارها، Git و وضعیت پذیرش

- هشدار `AccessDeniedException` برای Kotlin daemon marker موجب fallback کامپایل بدون daemon شد؛ اجراهای focused با `BUILD SUCCESSFUL` تمام شدند. هشدار deprecation مربوط به `sun.misc.Unsafe::objectFieldOffset` و DSLهای تست deprecated خطای تست نبودند. اختلاف JVM target (JDK 24 در برابر Kotlin JVM 22) در اجرای Phase 3 تازه ظاهر نشد و در اعتبارسنجی قبلی ثبت شده بود.
- پیش از ممیزی: ۴۷ مسیر status، شامل ۸ staged، ۳۵ unstaged و ۱۲ untracked. پس از ممیزی نیز همان ۴۷ مسیر و همان هشت فایل LockForUpdate با وضعیت `AM` در index باقی ماندند؛ هیچ stage یا commit انجام نشد. پلن از قبل untracked بود و تنها محتوای آن در این Phase به‌روزرسانی شد.
- **وضعیت پذیرش در snapshot پایان Phase 4: ناقص یا تأییدنشده برای پذیرش کامل.** در آن زمان no-bypass هنوز پیاده‌سازی نشده بود؛ اجرای Phase 6 که در ادامه ثبت شده، اکنون آن معیار را با تست‌های focused تأیید می‌کند. پذیرش کلی همچنان به Integration واقعی MySQL و اجرای دو تست قفل واقعی وابسته است؛ پوشش تمام ترکیب‌های fatal `Error` در cleanup نیز کامل نیست. بنابراین سند ادعای تأیید MySQL/FOR UPDATE یا پوشش کامل تمام حالت‌های cleanup را مطرح نمی‌کند.

## تاریخچهٔ تغییرات پلن

| تاریخ | دلیل | بخش‌های اصلاح‌شده |
|---|---|---|
| ۲۸ اکتبر ۲۰۲۶ | مسیر V2.0 در مخزن موجود نبود؛ متن پیش‌نویس ارائه‌شدهٔ کاربر بازیابی شد و پس از ممیزی سورس، وضعیت جاری و اختلاف‌ها به‌صورت مستند ثبت شد. | وضعیت ابتدا؛ اصول و API پیشنهادی؛ قرارداد Connection/Transaction؛ nesting؛ چندفایلی؛ زیرساخت؛ تست‌ها؛ مراحل؛ معیارهای پذیرش؛ تصمیم‌های باز و نتیجهٔ ممیزی. |
| ۲۸ اکتبر ۲۰۲۶ | مقایسهٔ دو مسیر اجرای Query و نهایی‌کردن قرارداد/معماری `connection {}` بر اساس API و پیاده‌سازی واقعی؛ در آن بازبینی مرز typed mapping باز ماند و در بازبینی بعدی تعیین تکلیف شد. | وضعیت اجرا؛ API و انتخاب `ctx.queryBuilder(api)`؛ قرارداد lifecycle، Failure/Exception/Error/close؛ state/lease مشترک با Transaction؛ nesting و `executeOut`; مراحل و پذیرش؛ یافته‌ها و تصمیم باز در آن تاریخ. |
| ۲۸ اکتبر ۲۰۲۶ | بررسی wrapperهای واقعی `first/get` نشان داد Failureهای validation/materialization از قبل به state تراکنش می‌رسند؛ تصمیم شد Failureهای framework-generated در `executeAs()` نیز از پل داخلی به همان latch مشترک گزارش شوند. | قرارداد Failure در Connection/Transaction Scope؛ تست‌های validation/materialization/typed mapping؛ مراحل ۲ تا ۵؛ معیار پذیرش؛ نتیجهٔ ممیزی و تصمیم نهایی. |
| ۲۸ اکتبر ۲۰۲۶ | اجرای Phase 1 محدودِ Failure propagation: پل داخلی `QueryConsumer.executeAs()` به state موجود Transaction اضافه شد؛ Failureهای mapping و نبودن فهرست materialized latch می‌شوند، mapper `Error` fatal ثبت و بدون تبدیل دوباره پرتاب می‌شود. تست‌های focused هر دو کلاس موفق شدند؛ مراحل بعدی v2.0 آغاز نشدند. | وضعیت اجرا؛ مرحلهٔ ۲ و ۳؛ مرحلهٔ ۵ و ۶؛ نتیجهٔ ممیزی و تصمیم نهایی دربارهٔ Failure؛ نتایج اعتبارسنجی و محدودیت‌های محیطی. |
| ۲۸ اکتبر ۲۰۲۶ | اجرای Phase 2: افزودن `IQueryBuilderExecutor.connection {}` با default fail-closed؛ استفاده از Connection واحد، state/guard موجود، توقف پس از Failure، lifecycle بدون transaction control، nesting/cross-nesting و رد root/`executeOut`. تست focused با proxy موفق شد؛ Integration دیتابیسی اجرا نشد. | وضعیت اجرا؛ قرارداد Connection Scope؛ وضعیت مراحل ۳ تا ۶؛ ارزیابی معیارهای پذیرش؛ نتایج واقعی Gradle/XML؛ محدودیت lease مشترک، consumer compile و MySQL. |
| ۲۸ اکتبر ۲۰۲۶ | تکمیل اعتبارسنجی پیش از Phase 3: تحلیل lease ثبت شد؛ تست thread غیرمالک افزوده و با suite کامل executor تأیید شد؛ consumer مستقل با dependency مستقیم executor کامپایل شد؛ regression suite `manager:execute` نیز موفق بود. تست‌های واقعی دیتابیس opt-in خاموش ماندند. | ممیزی ownership/lease؛ تست thread-owner و context منقضی‌شده؛ consumer compile؛ نتایج XML executor/manager؛ وضعیت معیارها و محدودیت‌های daemon/JVM target و Integration. |
| ۲۸ اکتبر ۲۰۲۶ | اجرای Phase 3: lifecycleهای `executeConnectionScope` و `executeTransaction` با هم‌راستایی state/guard ممیزی شدند؛ lease مستقل به‌دلیل نبود ضرورت correctness رد شد و lifecycleهای متناسب با policy هر Scope حفظ شدند. سه اجرای focused با XML موفق شدند: ۴۳ `TransactionExecutionTest`، ۷ `QueryConsumerExecuteAsTest` و ۲۷ `QueryExecuteContractTest`؛ هیچ DB یا Integration اجرا نشد. | تعریف lease؛ تصمیم معماری و اصلاح معیار 2؛ ارزیابی پذیرش Phase 3؛ نتایج واقعی test XML و هشدارهای Kotlin daemon؛ snapshot Git و حفظ LockForUpdate. |
| ۲۹ اکتبر ۲۰۲۶ | ممیزی نهایی Phase 4 فقط‌خوانی انجام شد. کد، تست‌های focused و XMLهای موجود با معیارهای پذیرش تطبیق داده شدند؛ هیچ build/test/Gradle task یا اتصال DB اجرا نشد. پذیرش کامل باز ماند: Integration MySQL/FOR UPDATE اجرا نشده، خطاهای fatal در cleanup پوشش runtime کامل ندارند و اجرای root executor از thread غیرمالک به‌دلیل guard مبتنی بر `ThreadLocal` نیازمند تصمیم و تست مستقل است. | وضعیت نهایی Phase؛ جدول معیارهای پذیرش با وضعیت‌های چهارگانه؛ تفکیک XMLهای focused از سوابق suite کامل؛ محدودیت MySQL/FOR UPDATE، consumer compile، هشدارهای محیط، و snapshot Git قبل/بعد. |
| ۲۹ اکتبر ۲۰۲۶ | Phase 5: ابهام root executor از thread غیرمالک در پلن با دو گزینه ثبت شد؛ characterization روی Connection/Transaction ثابت کرد مسیر فعلی از اتصال مستقل عبور می‌کند. سه تست focused cleanup برای rollback/close exception و Error افزوده شدند. XML نهایی `TransactionExecutionTest` شامل ۴۸ تست موفق است؛ قرارداد مطلوب thread همچنان تصمیم‌نشده است. | وضعیت و خلاصه Phase 5؛ شواهد و گزینه‌های root-thread؛ جدول پذیرش برای cleanup و root executor؛ فرمان/XML نهایی، هشدارهای Kotlin daemon و snapshot Git. |
| ۲۹ اکتبر ۲۰۲۶ | Phase 6: قرارداد no-bypass همان root lineage، شروع Scope به‌صورت fail-fast در حضور Query عادی، و seal اتمیک در `Active → Finalizing` پیاده‌سازی شد. تست‌های latch-based `TransactionExecutionTest` و `QueryConsumerExecuteAsTest` در XML نهایی ۶۰ موفق، صفر failure/error/skipped ثبت کردند. | پیاده‌سازی coordinator و admission permit؛ قواعد Starting/Active/Finalizing؛ نتایج XML و محدودیت بازیابی‌نشدن کد خروج کنسول؛ snapshot Git؛ موارد MySQL/FOR UPDATE تأییدنشده. |

## Phase 5 — Characterization اجرای root executor و پوشش Cleanup

**تاریخ بررسی:** ۲۹ اکتبر ۲۰۲۶

### شواهد کد و قرارداد فعلی

- تست موجود `connection scope rejects query from non owner thread without dispatching or closing early` فقط Context گرفته‌شده از callback را از thread دیگر صدا می‌زند؛ همان Context در `TransactionExecutionScope.rejectQuery()` رد می‌شود و Failure را در Scope ثبت می‌کند.
- تست `connection scope rejects root executor and executeOut bypasses` root executor را فقط در thread callback می‌سنجد.
- `TransactionGuard.activeScope()` از `ThreadLocal` می‌خواند. در `QueryBuilderExecutor.executeInScope()`، root executor بدون `transactionScope` اگر Scope محلی پیدا نکند، اجرای عادی را فرا می‌خواند. پس root executor از thread غیرمالک هنگام Scope فعال می‌تواند Connection مستقل بگیرد. این نتیجه از کد فعلی به‌دست می‌آید و هنوز به‌عنوان رفتار مطلوب قراردادی تثبیت نشده است.
- معیار پذیرش no-bypass، root executor را به‌طور کلی نام می‌برد؛ بند thread ownership فعلی، استفادهٔ Context scoped در thread دیگر را رد می‌کند. این دو بند به‌تنهایی تعیین نمی‌کنند root executor در thread دیگری که callback Scope در آن فعال نیست چه رفتاری داشته باشد.

### دو گزینهٔ رفتاری باز

1. **منع root execution در تمام threadها تا پایان Scope:** root executor متصل به همان executor والد باید هنگام فعال‌بودن Scope در هر thread رد شود و نباید Connection مستقل بگیرد. پیاده‌سازی باید registry/coordination فراتر از `ThreadLocal` داشته باشد. این گزینه no-bypass را سراسری می‌کند، اما اجرای هم‌زمان یک Query عادی از همان executor والد را نیز تا پایان Scope تحت تأثیر می‌گذارد و به تعریف thread-safety، هم‌زمانی و آزادسازی registry نیاز دارد.
2. **محدودکردن منع به thread callback:** root executor و `executeOut()` در thread callback Scope رد می‌شوند؛ Context scoped در thread غیرمالک نیز رد می‌شود؛ ولی root executor از thread دیگر همچنان رفتار عادی و مستقل خود را دارد. این گزینه سازگاری رفتار مستقل فعلی را حفظ می‌کند، اما منع no-bypass سراسری نیست و عملیات موازی روی Connection جداگانه را ممکن می‌گذارد.

**وضعیت تصمیم:** باز؛ هیچ‌یک از دو گزینه به‌عنوان قرارداد عمومی انتخاب نشده و API/production تغییر نکرده است. تست‌های `root executor from non owner thread uses an independent connection during active scope (characterization)` و `...during active transaction (characterization)` رفتار فعلی را مشخص می‌کنند؛ نتیجهٔ آن‌ها به‌تنهایی به‌معنای تأیید قرارداد مطلوب نیست.

### نتایج Phase 5

- **Thread ownership:** هر دو characterization تست ثابت کردند که worker thread از root executor استفاده می‌کند، Query موفق از مسیر مستقل اجرا می‌شود و Connection دوم پیش از بازگشت Scope بسته می‌شود. Context scoped در thread غیرمالک همچنان مطابق تست موجود رد می‌شود. قرارداد مطلوب root executor هنوز باز است؛ هیچ تغییر production یا API انجام نشد.
- **Cleanup:** تست `rollback and close failures retain the query failure and cleanup order` حفظ همان `queryFailure` را در `ExecuteResult.Failure` و ترتیب suppressedهای rollback سپس close را تأیید کرد. تست `rollback Error remains fatal and retains query and later close failures` همان rollback `Error` را به‌عنوان خطای نهایی و query/close failures را به‌ترتیب به‌صورت suppressed تأیید کرد. تست `callback Error remains primary when rollback Error and close failure occur` حفظ همان callback `Error` را با rollback/close suppressed تأیید کرد. در هر حالت rollback و close دقیقاً یک‌بار و به ترتیب اجرا شدند و commit فراخوانی نشد.
- **فرمان نهایی:** `:data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --no-build-cache --console=plain` با JDK 24 و Gradle User Home محلی پروژه؛ `BUILD SUCCESSFUL`، ۲۸ task actionable (۲ اجرا و ۲۶ up-to-date). XML: `data_base/query/executor/build/test-results/test/TEST-gog.my_project.data_base.query.executer.manager.TransactionExecutionTest.xml`، شامل ۴۸ تست، صفر failure، صفر error و صفر skipped.
- **هشدارها:** Kotlin daemon نتوانست marker موقت را در `%LOCALAPPDATA%\\kotlin\\daemon` بسازد؛ fallback بدون daemon موفق شد. هشدارهای deprecated test DSL و `sun.misc.Unsafe::objectFieldOffset` دیده شدند؛ شکست تست/کامپایل نهایی نبودند. هیچ DB یا Integration اجرا نشد.
- **حفاظت Git:** قبل و بعد ۴۷ مسیر status باقی ماندند: ۲۷ ` M`، همان ۸ `AM` و ۱۲ `??`. `git diff --cached --name-status` همچنان فقط همان هشت LockForUpdate را دارد. تغییرات این Phase فقط به محتوای فایل untracked موجود `TransactionExecutionTest.kt` و همین پلن untracked محدود بود؛ staging و commit انجام نشد.
- **وضعیت Phase 5:** پوشش cleanup موردنیاز این مرحله و characterization رفتاری تکمیل شد. تصمیم قراردادی root executor از thread غیرمالک هنوز لازم است؛ MySQL واقعی و قفل `FOR UPDATE` همچنان تأییدنشده‌اند. پذیرش کامل V2.0 اعلام نمی‌شود.

## Phase 6 — جلوگیری از bypass ریشه و هماهنگی هم‌زمانی Scope

**تاریخ بررسی:** ۲۹ اکتبر ۲۰۲۶  
**وضعیت:** پیاده‌سازی داخلی و تست‌های هدفمند تکمیل شده‌اند. No-bypass به همان root executor lineage محدود است؛ rootهای مستقل coordinator جدا دارند.

### پیاده‌سازی و شواهد

- `QueryBuilderExecutor.kt` یک `RootExecutionCoordinator` برای هر root executor می‌سازد و آن را با executorهای فرزند به اشتراک می‌گذارد. `TransactionGuard` همچنان nesting هم‌ریسمانی و Context فعال را مدیریت می‌کند؛ coordinator اجرای root را در همهٔ threadها برای همان lineage هماهنگ می‌کند.
- root query و `executeOut()` پیش از renderer/dispatch یا گرفتن Connection مجوز می‌گیرند. در `Starting` و `Active` رد می‌شوند؛ در `Active` Failure همان Scope را ثبت می‌کند. در `Finalizing` رد می‌شوند، بی‌آنکه نتیجهٔ seal‌شده را تغییر دهند.
- `tryStartScope()` شروع Scope را در حضور عملیات عادی root در حال اجرا fail-fast رد می‌کند. هنگام `Starting`، تلاش root پیش از اجرای callback یا connection factory رد می‌شود و Failure pending به Scope منتقل می‌شود.
- `seal(scope)` اتمیکاً Scope را پایان می‌دهد و coordinator را به `Finalizing` می‌برد، پیش از commit/rollback/close. `finishScope()` پس از اتمام cleanup state را آزاد می‌کند. permit عملیات root در `finally` آزاد می‌شود و release در برابر فراخوانی تکراری idempotent است.
- تست‌های سابق characterization اکنون قرارداد no-bypass را بررسی می‌کنند؛ تست‌های هماهنگ‌شده با latch پوشش می‌دهند که Query/Connection جدید ساخته نشود، نتیجه بعد از seal تغییر نکند، root مستقل مسدود نشود و پس از cleanup همان root دوباره قابل استفاده باشد.

### گزینه‌های بررسی‌شده و تصمیم نهایی

1. **اتکا به `ThreadLocal` فعلی:** هیچ state یا API تازه‌ای لازم ندارد، اما thread غیرمالک همچنان می‌تواند Scope را دور بزند؛ با تصمیم Phase 6 سازگار نیست.
2. **registry سراسری از executorها/Scopeهای فعال:** threadهای مختلف را می‌بیند، ولی به state مرکزی، مدیریت identity و حذف entryها، هماهنگی سراسری و خطر نگهداری referenceهای مانده نیاز دارد؛ Scopeهای نامرتبط نیز به‌سادگی با هم تداخل می‌کنند. این گزینه توصیه نمی‌شود.
3. **هماهنگ‌کنندهٔ داخلی و محدود به هر root executor (انتخاب و پیاده‌سازی‌شده):** state مشترک فقط بین یک root executor و Contextهای فرزند آن نگهداری می‌شود؛ هیچ API عمومی یا registry سراسریِ executorها اضافه نمی‌شود. نمونه‌های root مستقل state جدا دارند. هزینه، یک state داخلی محدود و قواعد پذیرش هم‌زمانی است؛ تغییر سازگاری محدود به استفادهٔ هم‌زمان از همان root executor هنگام Scope یا query دیگر است.

### طرح پیشنهادی

- یک `RootExecutionCoordinator` داخلی برای هر نمونهٔ root `QueryBuilderExecutor` ساخته شود و همان reference به executor فرزند Connection/Transaction داده شود. `TransactionGuardRegistry.shared` به‌عنوان guard مبتنی بر `ThreadLocal` برای رد nesting هم‌ریسمانی میان Scopeها حفظ می‌شود؛ coordinator جای آن را نمی‌گیرد.
- coordinator با monitor کوتاه و state محدود (`Idle`, `Starting`, `Active`, `Finalizing`) و شمارندهٔ عملیات عادیِ root کار کند؛ هیچ monitor هنگام JDBC، callback یا cleanup نگه داشته نشود و state با پایان مسیر به `Idle` برگردد. state به‌صورت map سراسری یا به‌ازای هر thread انباشته نمی‌شود.
- اجرای عادی root، از جمله SELECT/DML و `executeOut()`, پیش از dispatch یک permit می‌گیرد و آن را در `finally` آزاد می‌کند. اگر همان root در state غیر `Idle` باشد، Query به شکل `ExecuteResult.Failure` رد می‌شود و هیچ renderer/JDBC/Connection مستقل اجرا نمی‌شود؛ `executeOut()` نیز پیش از dispatch خطا می‌دهد و در مرحلهٔ callback فعال، Failure را latch می‌کند.
- شروع `connection {}` یا `transaction {}` ابتدا guard فعلی nesting را می‌گذراند و سپس به‌صورت fail-fast coordinator را از `Idle` به `Starting` می‌برد. اگر عملیات عادی همان root هنوز در جریان باشد، شروع Scope با `Failure` رد می‌شود، callback اجرا نمی‌شود و Connection گرفته نمی‌شود. این قاعده از race بین پذیرش Query عادی و آغاز Scope جلوگیری می‌کند، بدون انتظار نامحدود یا lock در طول عملیات.
- state `Starting` پیش از فراخوانی factory فعال است تا Query worker در زمان acquisition نتواند Connection دیگری باز کند. اگر چنین تلاش ردیف‌شده‌ای رخ دهد، با یک `IllegalStateException` رد و به‌عنوان Failure pending نگه داشته می‌شود؛ در صورت موفقیت acquisition، به `TransactionExecutionScope` منتقل و قبل از callback latch می‌شود. اگر خود acquisition شکست بخورد، همان خطای acquisition نتیجهٔ Scope است و callback اجرا نمی‌شود.
- پس از آماده‌شدن `TransactionExecutionScope`، coordinator به `Active` می‌رود. root Query یا شروع Scope دیگر روی همان executor از هر thread رد می‌شود؛ هنگام callback فعال، اولین Failure در state موجود Scope ثبت می‌شود. Transaction در پایان Rollback می‌کند و Connection Scope با همان policy خود فقط Connection را می‌بندد.
- با پایان callback، یک transition اتمیک `Active → Finalizing` هم‌زمان `scope.end()` را انجام می‌دهد و نتیجهٔ latchشده را برای تصمیم commit/rollback تثبیت می‌کند. در `Finalizing`، تا پایان تلاش cleanup/close تمام root calls همان نمونه رد می‌شوند و Connection تازه نمی‌گیرند؛ چون نتیجهٔ Scope قبلاً seal شده، این تلاش‌های دیرهنگام نتیجهٔ نهایی را عوض نمی‌کنند. پس از تلاش close و آزادسازی guard، state به `Idle` بازمی‌گردد و اجرای مستقل بعدی رفتار عادی قبلی را دارد.
- Context فرزند همچنان فقط با `TransactionExecutionScope.rejectQuery()` thread غیرمالک یا Context منقضی‌شده را رد می‌کند. child queryها از Connection همان Scope استفاده می‌کنند و برای هر query از مسیر root permit عبور نمی‌کنند.
- نمونهٔ root دیگری در thread دیگری coordinator مستقلی دارد و با Scope فعال این نمونه مسدود نمی‌شود. شروع nested/cross-nested Scope روی همان thread همچنان با `TransactionGuardRegistry.shared` رد می‌شود. این جداسازی، no-bypass را برای همان executor lineage تضمین می‌کند، نه برای همهٔ executorهای مستقل process.

### پیامدهای سازگاری و تصمیم‌های نیازمند تأیید

- API عمومی `IQueryBuilderExecutor` تغییر نمی‌کند؛ تغییر موردنیاز state داخلی در `QueryBuilderExecutor` و `TransactionExecutionScope` است.
- عملیات عادی root خارج از Scope همچنان هم‌زمان قابل dispatch است. اگر همان root در حال اجرای Query عادی باشد، شروع Scope جدید fail-fast می‌شود؛ Query عادی‌ای که در لحظهٔ شروع Scope هنوز permit دارد، اجازه نمی‌دهد Scope شروع شود. این رفتار با تست latch-based `scope start fails fast while a root query is in flight` بررسی شده است.
- رد root Query در callback فعال، Failure را latch می‌کند و می‌تواند نتیجهٔ Connection/Transaction را تغییر دهد؛ این رفتار با منع دورزدن و قاعدهٔ موجود latch هم‌راستاست.
- تلاش root در `Finalizing` رد می‌شود و هیچ Connection/Query ایجاد نمی‌کند، اما نتیجهٔ Scope را عوض نمی‌کند؛ transition به Finalizing نقطهٔ نهایی‌شدن پذیرش Failure است و از تغییر نتیجه پس از شروع commit یا cleanup جلوگیری می‌کند. تست‌های sealed connection/transaction این رفتار را همراه با امکان استفادهٔ مجدد پس از cleanup بررسی می‌کنند.
- `executeOut()` در callback فعال با `UnsupportedOperationException` رد و Failure آن در Scope ثبت می‌شود؛ در `Starting` و `Finalizing` نیز پیش از dispatch رد می‌شود. تست‌ها نبود Connection/Query جدید را بررسی می‌کنند.
- یک executor مستقل، حتی اگر از thread دیگری هم‌زمان کار کند، مسدود نمی‌شود. این تصمیم عمداً از منع process-wide فراتر نمی‌رود؛ اتصال executorها از طریق یک نمونهٔ root و Context فرزند آن تعریف می‌شود.

### تست‌های اجراشده و تطبیق معیارهای Phase 6

1. **Active و bypass:** تست‌های `root executor from non owner thread is rejected during active connection scope`, `root executor from non owner thread is rejected during active transaction`, `connection scope rejects root executor and executeOut bypasses` و `executeOut through original executor cannot bypass transaction` رد root/`executeOut` و رفتار Failure در Scope را بررسی می‌کنند؛ Connection Scope فقط close می‌کند و Transaction rollback می‌کند.
2. **Starting:** `root query and executeOut are rejected during starting before a connection is opened` بررسی می‌کند که root query، `executeOut` و شروع Scope تودرتو پیش از ساخت Connection یا اجرای callback رد شوند و Failure در صورت فعال‌شدن Scope منتقل شود.
3. **Query عادی هم‌زمان با شروع Scope:** `scope start fails fast while a root query is in flight` با latch، عدم اجرای callback/factory هنگام permit فعال و امکان شروع Scope پس از آزادشدن permit را بررسی می‌کند.
4. **Finalizing و seal:** `sealed connection scope rejects late root calls without changing its result` و `sealed transaction rejects late root calls and independent roots remain available` بررسی می‌کنند که root query/`executeOut` دیرهنگام Connection یا Query نسازند، نتیجهٔ Scope را تغییر ندهند، root مستقل مسدود نشود و پس از cleanup همان root دوباره قابل استفاده باشد.
5. **انتشار permit در خطا:** `root operation Error releases its admission permit` بررسی می‌کند که Error اجرای root مجوز را نشت نمی‌دهد و Scope بعدی می‌تواند آغاز شود.
6. **رفتارهای قبلی:** تست‌های nesting/cross-nesting، Context منقضی‌شده، thread غیرمالک در Context scoped، rollback/commit/close، `executeOut` و مسیر one-shot بدون حذف یا تضعیف در همان suite حفظ شده‌اند.
7. **نتیجه:** تمام ۵۳ تست `TransactionExecutionTest` و ۷ تست `QueryConsumerExecuteAsTest` در XML موفق‌اند؛ جزئیات شمارش و محدودیت console در بخش «نتایج اعتبارسنجی Phase 6» آمده است.

**پذیرش طرح:** هیچ Query یا Connection مستقل از همان root در stateهای `Starting`, `Active`, `Finalizing` آغاز نشود؛ root مستقل با coordinator جدا تحت تأثیر قرار نگیرد؛ state و permit در همهٔ مسیرهای عادی/خطا/`Error` در `finally` آزاد شوند؛ Connection و lifecycle Transaction/Connection Scope مطابق قرارداد فعلی باقی بمانند؛ و تمام raceها با latch/barrier و deadline محدود، نه sleep، آزموده شوند.

### نتایج اعتبارسنجی Phase 6

- فرمان اجراشده از ریشهٔ مخزن: `.\gradlew.bat :data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --tests "gog.my_project.data_base.query.executer.manager.QueryConsumerExecuteAsTest" '-Pkotlin.compiler.execution.strategy=in-process' --no-build-cache --console=plain`. برای همان نشست، JDK 24 و `GRADLE_USER_HOME` محلی مخزن تنظیم شده بود.
- XML: `TransactionExecutionTest` برابر ۵۳ تست، ۰ failure، ۰ error و ۰ skipped؛ `QueryConsumerExecuteAsTest` برابر ۷ تست، ۰ failure، ۰ error و ۰ skipped؛ جمع این دو suite برابر ۶۰ تست موفق است. فایل‌ها در `data_base/query/executor/build/test-results/test/` هستند.
- XML هر دو suite با timestamp اجرای نهایی در دسترس است. آخرین بلوک `.gradle/daemon/8.14/daemon-13980.out.log` نیز `BUILD SUCCESSFUL in 32s` ثبت می‌کند. نشست تعاملی دیگر قابل بازیابی نیست، پس numeric exit code در دسترس نیست؛ نتیجهٔ Gradle از daemon log و شمارش تست‌ها از XML تأیید می‌شود.
- اجرا فقط از fake/proxyهای JDBC استفاده کرد؛ MySQL، Integration واقعی و قفل `FOR UPDATE` اجرا یا تأیید نشدند. گزینهٔ Kotlin compiler به‌صورت موقت `in-process` تنظیم شد و در اجرای نهایی خطای دسترسی Kotlin daemon ثبت نشد. هشدار JVM target باقی است: Kotlin target درخواست‌شده برای JDK 24 را پشتیبانی نمی‌کند و به JVM 22 برمی‌گردد؛ تنظیمات build تغییر نکردند.
- Git پیش و پس از Phase 6: همان ۴۷ مسیر status شامل ۲۷ ` M`، ۸ `AM` و ۱۲ `??`. `git diff --cached --name-status` همچنان همان هشت فایل LockForUpdate را نشان می‌دهد؛ staging area تغییر نکرد و هیچ stage/commit انجام نشد. تغییرات این Phase در محتوای فایل‌های موجود `QueryBuilderExecutor.kt`، `TransactionExecutionScope.kt`، `TransactionExecutionTest.kt` و این پلن است؛ هیچ مسیر جدیدی به working tree افزوده نشد.

**نتیجهٔ Phase 6:** معیارهای هم‌زمانی و root lineage با تست‌های focused و XML تأیید شدند. پذیرش کلی Transaction V2.0 همچنان باز است تا Integration واقعی MySQL و رفتار lock بین دو Connection مستقل با محیط مجاز اجرا شود.

## ممیزی فقط‌خواندنی پیش‌نیازهای MySQL Integration — ۲۹ اکتبر ۲۰۲۶

این ممیزی فقط کد `TransactionMySqlIntegrationTest.kt`، تنظیمات test task و مستندات MySQL 8.0 را بررسی کرد. هیچ اتصال دیتابیسی، build، تست، task یا تغییر تنظیمات محیطی انجام نشد.

### SQL و مجوزهای لازم

SQLهای صریح تست شامل `SELECT DATABASE()` برای تأیید schema اتصال، `CREATE TABLE ... ENGINE=InnoDB` برای جدول یکتای `qb_tx_rows_<uuid>`، `DROP TABLE IF EXISTS` همان جدول در cleanup، و `SET SESSION innodb_lock_wait_timeout = 8` در اتصال دوم سناریوهای قفل هستند. Queryهای builder نیز فقط `INSERT` (از جمله درج تکراری برای تولید خطای واقعی)، `SELECT`، و `SELECT ... FOR UPDATE` اجرا می‌کنند. تست هیچ `UPDATE` یا `DELETE` ردیفی، `LOCK TABLES`، `CREATE DATABASE` یا `DROP DATABASE` اجرا نمی‌کند. Commit و rollback از طریق JDBC انجام می‌شوند.

برای MySQL 8.0، `CREATE TABLE` به `CREATE`، درج به `INSERT` و خواندن جدول به `SELECT` نیاز دارد؛ تعریف indexها در همان `CREATE TABLE` به مجوز جداگانهٔ `INDEX` نیاز ندارد. `DROP TABLE` به `DROP` نیاز دارد. `SELECT ... FOR UPDATE` علاوه بر `SELECT` به دست‌کم یکی از `UPDATE`، `DELETE` یا `LOCK TABLES` نیاز دارد. چون تست هیچ DML از نوع UPDATE/DELETE ندارد، پیشنهاد کم‌خطرتر از میان این گزینه‌ها `LOCK TABLES` است؛ این مجوز برای عبور از بررسی privilege در locking read است، نه به‌دلیل اجرای دستور `LOCK TABLES` توسط تست. نسخهٔ واقعی سرور هنوز مشخص نیست و شرط مجوز باید با نسخهٔ اعلام‌شدهٔ DBA دوباره تطبیق داده شود.

`SET SESSION innodb_lock_wait_timeout` در مستند MySQL 8.0 برای هر client مجاز است و privilege مدیریتی global لازم ندارد. تست مقدار را فقط روی session اتصال دوم تغییر می‌دهد، نه در سطح global. مجوزهای `UPDATE` و `DELETE` برای SQLهای فعلی لازم نیستند.

تست فقط جدول یکتای ساخته‌شده توسط خودش را در `finally` حذف می‌کند؛ اما محدودیت privilegeهای MySQL اجازه نمی‌دهد به حسابی حق ساخت/حذف جدول در schema بدهیم و هم‌زمان حق ساخت/حذف خود database را صریحاً از آن بگیریم. بنابراین `CREATE, DROP ON schema.*` برای جدول‌های تصادفی فراتر از جدول‌ها اختیار می‌دهد و می‌تواند شامل ساخت/حذف همان schema نیز باشد. کد تست هیچ `CREATE DATABASE` یا `DROP DATABASE` اجرا نمی‌کند، اما توانایی حساب گسترده‌تر از SQLهای تست است. اجرای تست با این grant تنها پس از تأیید DBA مبنی بر اینکه schema اختصاصی، آزمایشی و کاملاً قابل‌حذف است قابل قبول است. اگر این توانایی برای حساب تست پذیرفتنی نیست، طرح فعلیِ ساخت و حذف جدول تصادفی با یک حساب واحد کم‌اختیار قابل تأمین نیست؛ باید setup/cleanup با هویت جداگانه یا سازوکار جدول ازپیش‌ساخته بازطراحی شود و این تصمیم خارج از این ممیزی است.

### SQL پیشنهادی برای بازبینی DBA — اجرا نشده

مقادیر داخل `<...>` باید پیش از اجرا با مقادیر تأییدشده جایگزین شوند. `CREATE DATABASE` و `CREATE USER` را فقط DBA اجرا می‌کند؛ حساب تست هیچ privilege سراسری یا `GRANT OPTION` نمی‌گیرد. بااین‌حال grantهای `CREATE` و `DROP` در سطح schema طبق محدودیت MySQL قابل تفکیک از توانایی create/drop همان database نیستند. برای حساب MySQL از host مبدأ literal و محدود استفاده شود، نه `%`.

```sql
CREATE DATABASE `qb_tx_test_kotlin_db` CHARACTER SET utf8mb4;

CREATE USER 'qb_tx_test_user'@'<literal-client-host-or-ip>'
  IDENTIFIED BY '<secret-provided-through-approved-secure-channel>';

GRANT CREATE, DROP, SELECT, INSERT, LOCK TABLES
  ON `qb_tx_test_kotlin_db`.*
  TO 'qb_tx_test_user'@'<literal-client-host-or-ip>';

SHOW GRANTS FOR 'qb_tx_test_user'@'<literal-client-host-or-ip>';
```

این SQL فقط برای schema تازه و اختصاصی و با پذیرش صریح توانایی create/drop خود schema پیشنهاد شده است؛ اگر DBA این اثر را نمی‌پذیرد، هیچ‌یک از grantهای `CREATE`/`DROP` را اجرا نکند و ابتدا راه setup/cleanup با هویت جداگانه یا جدول ازپیش‌ساخته تصویب شود. secret نباید در repository، Gradle tracked، log یا command history ثبت شود.

### نام schema و تنظیمات لازم

`qb_tx_test_kotlin_db` با الگوی تست `^qb_tx_test_[A-Za-z0-9_]+$` سازگار است و ۲۰ نویسه دارد؛ محدودیت نام database در MySQL 8.0 برابر ۶۴ نویسه است. این فقط اعتبار نحوی نام را تأیید می‌کند و وجود، اختصاصی‌بودن، خالی‌بودن یا آماده‌بودن schema را اثبات نمی‌کند. schema موجود `kotlin_db_test` با validator تست سازگار نیست و در این مسیر نباید استفاده شود.

تست فقط با propertyهای صریح زیر opt-in می‌شود؛ `enabled` باید دقیقاً `true` باشد و در صورت فعال‌سازی هر پنج property اتصال باید حاضر باشند:

- `execute.test.db.transaction.enabled`
- `execute.test.db.host`
- `execute.test.db.port` — عدد صریح در بازهٔ ۱ تا ۶۵۵۳۵، بدون fallback.
- `execute.test.db.name` — schema با پیشوند و نویسه‌های مجاز validator.
- `execute.test.db.username` — مقدار غیرخالی.
- `execute.test.db.password` — property باید موجود باشد؛ validator فعلی خالی‌بودن آن را رد نمی‌کند، اما برای محیط واقعی credential غیرخالی و اختصاصی لازم است.

Gradle فقط propertyهای پروژهٔ موجود را به test worker منتقل می‌کند و برای host یا port مقدار پیش‌فرض اضافه نمی‌کند. Connector/J موجود در build برابر `mysql:mysql-connector-java:8.0.33` است؛ این نسخه، نسخهٔ سرور MySQL را تعیین یا pin نمی‌کند.

### موارد تأییدنشده و مانع اجرای Integration

- host و port واقعی server، نسخهٔ دقیق MySQL، InnoDB بودن و آماده‌بودن schema، محدودیت دسترسی شبکه، host مبدأیی که باید در account نام‌گذاری شود، و credential اختصاصی هنوز از کاربر/DBA تأیید نشده‌اند.
- وجود schema و account، خالی یا disposable بودن schema، و grantهای نهایی با `SHOW GRANTS` فقط پس از provisioning قابل بررسی‌اند؛ در این مرحله هیچ‌کدام بررسی یا اجرا نشدند.
- تا وقتی DBA ریسک create/drop database ناشی از grantهای `CREATE`/`DROP ON schema.*` را صریحاً نپذیرفته یا راه cleanup محدودتر تصویب نشده، SQL پیشنهادی اجرا نشود.
- در `withTestTable`, پرچم `tableCreated` پس از پایان `use` اتصال ساخت جدول تنظیم می‌شود؛ اگر `CREATE TABLE` موفق شود ولی بستن همان Connection خطا بدهد، cleanup ممکن است از روی پرچم جدول را حذف نکند. این ریسک کدی در این مرحله اصلاح نشد.

منابع مستند برای privilege و محدودیت‌های نسخه: MySQL 8.0 Reference Manual، بخش‌های 17.7.2.4 «Locking Reads»، 8.2.2 «Privileges Provided by MySQL»، 15.1.20 «CREATE TABLE»، 15.1.32 «DROP TABLE»، 17.14 «InnoDB Startup Options and System Variables»، و «Identifier Length Limits». این ثبت، اجرای واقعی یا آمادگی محیط را تأیید نمی‌کند.

## Phase 9 — امکان استفاده از `kotlin_db_test` (تحلیل فقط‌خواندنی)

**نتیجه:** `kotlin_db_test` با validator فعلی `TransactionMySqlIntegrationTest` قابل استفاده نیست. نام در `readSettings` با `^qb_tx_test_[A-Za-z0-9_]+$` رد می‌شود؛ این اعتبارسنجی پیش از `openVerifiedConnection` انجام می‌شود. پیشوند فقط guard ایمنی/ایزوله‌سازی است: URL اتصال از `settings.schema` ساخته می‌شود، `SELECT DATABASE()` همان مقدار را تأیید می‌کند و SQL هیچ وابستگی فنی به رشتهٔ پیشوند ندارد. بنابراین پذیرش `kotlin_db_test` نیازمند تغییر validator است، ولی تغییر validator به‌تنهایی محیط را ایمن نمی‌کند.

### ارجاع‌های دیگر پروژه به `kotlin_db_test`

- رشتهٔ ثابت در source تست‌های Transaction یافت نشد. در `data_base/migration/help.json:220,473` و `data_base/migration/executor/help.json:44,106` این نام به‌عنوان default رابط اجرای Integration نمایش داده می‌شود؛ همان helpها host محلی/port و نام کاربری root را نیز به‌صورت default نمایشی تعریف می‌کنند. این‌ها fallback خود تست Transaction نیستند. Gradle ماژول migration propertyهای مشخص را فقط در صورت ارائه به test worker منتقل می‌کند؛ تست Transaction نام schema را از property صریح `execute.test.db.name` می‌گیرد.
- `MigrationSystemIntegrationTest` و `MigrationSchemaIntegrationTest` از `migration.test.db.name` استفاده می‌کنند. `migration.test.db.refresh` در نبود مقدار false است؛ اگر true شود، `refreshAllTables` همهٔ foreign keyها و تمام جدول‌های schema انتخاب‌شده را حذف می‌کند. وقتی refresh خاموش است، collisionهای نام‌گذاری‌شده را پیش از migration رد می‌کنند، اما پس از اجرای موفق fixtureها و جدول `system_migration` را برای بررسی حفظ می‌کنند و پاک‌سازی عمومی انجام نمی‌دهند. پس استفادهٔ مشترک از schema با این suiteها می‌تواند باقیمانده و برخورد ایجاد کند؛ refresh روشن می‌تواند داده و جدول‌های نامرتبط همان schema را حذف کند.
- `data_base/migration/executor/help.json` default همان database را برای integrationهای executor به propertyهای محیطی وصل می‌کند. `SystemMigrationStateStoreIntegrationTest` پیش از اجرا وجود `system_migration` را بررسی و در صورت collision متوقف می‌شود؛ در اجرای خود جدول را می‌سازد و سپس حذف می‌کند. `MigrationMigratorIntegrationTest` نیز collision جدول‌های `system_migration` و `v120_runner_integration_table` را رد می‌کند و در cleanup همین دو نام را حذف می‌کند. این تست‌ها schema را به‌صورت پیش‌فرض و خودکار انتخاب نمی‌کنند؛ اجرای آن‌ها به property/environmentهای صریح یا استفاده از مقدارهای help وابسته است.

### مقایسهٔ گزینه‌ها

| گزینه | فایده | ریسک و محدودیت |
|---|---|---|
| تغییر validator برای پذیرش `kotlin_db_test` | schema جدید لازم نیست و تست Transaction فقط جدول UUIDدار خودش را می‌سازد/حذف می‌کند. | ایزوله‌سازی را تضمین نمی‌کند؛ migration integrationها همان schema را پیشنهاد می‌کنند، ممکن است جدول و تاریخچه باقی بگذارند و refresh روشن همهٔ جدول‌ها را حذف کند. grantهای لازم نیز از نظر scope از جدول اختصاصی فراتر می‌روند. |
| schema جدا با پیشوند `qb_tx_test_` | validator و هدف ایزوله‌سازی با هم سازگارند و برخورد با migration fixtureهای `kotlin_db_test` کم می‌شود. | DBA باید schema و user را جداگانه آماده کند؛ خطر grantهای schema-level `CREATE`/`DROP` که در بخش Phase 8 ثبت شد باقی است. این تست schema را نمی‌سازد یا حذف نمی‌کند و جدول‌های UUIDدار را پاک می‌کند. |

**پیشنهاد:** validator را برای استفاده از `kotlin_db_test` تغییر ندهید. schema اختصاصی `qb_tx_test_...` گزینهٔ کم‌خطرتر است، مشروط به تأیید جداسازی، grants و پذیرش اختیار create/drop database؛ در غیر این صورت Integration مسدود بماند تا fixture با setup/cleanup دارای هویت جداگانه طراحی شود. هیچ evidenceای وجود ندارد که نشان دهد schema اضافه لازم است برای همیشه نگه داشته شود؛ DBA می‌تواند آن را پس از پایان و تأیید عدم استفادهٔ هم‌زمان حذف کند، اما حذف خودکار در تست نیست و نبود مصرف‌کنندهٔ خارجی را نمی‌توان از مخزن اثبات کرد.

این بررسی فقط source، help و Gradle wiring را خواند. هیچ کد، test، Gradle، فایل V1.0 یا تنظیمی تغییر نکرد؛ هیچ build، تست، task، اتصال یا عملیات دیتابیسی انجام نشد.

## Finalization Phase — ۲۹ اکتبر ۲۰۲۶

**وضعیت نهایی Transaction V2.0: `Complete`.** این ممیزی از نتایج قبلی و XMLهای موجود استفاده کرد؛ هیچ تست، build یا اتصال MySQL جدیدی اجرا نشد. وضعیت‌های «پذیرش باز» که در گزارش‌های تاریخی Phaseهای قبل ثبت شده‌اند، با شواهد نهایی این بخش به‌روزرسانی می‌شوند.

### LockForUpdate و Integration واقعی

- XML موجود `data_base/query/renderer/build/test-results/test/TEST-gog.my_project.data_base.query.renderer.lock.LockForUpdateRenderingTest.xml`: یک تست، یک موفق، صفر failure/error/skipped. تست SQL عادی را بدون `FOR UPDATE` و همان SELECT را با `FOR UPDATE` مقایسه می‌کند.
- XML موجود `data_base/query/executor/build/test-results/test/TEST-gog.my_project.data_base.query.executer.manager.TransactionMySqlIntegrationTest.xml`: ۹ تست، ۹ موفق، صفر failure/error/skipped. این نتیجه همان اجرای قبلی Integration است، نه اجرای Finalization.
- دو سناریوی واقعی قفل در همان suite، انتظار Connection دوم تا پایان Transaction اول را جداگانه برای `commit` و `rollback` بررسی می‌کنند. هماهنگی با `CountDownLatch`، deadline محدود و timeout مشاهدهٔ انتظار انجام می‌شود و تست تفاوت Connectionها را assert می‌کند.
- مسیر عمومی V2.0 برای هر دو SELECT قفل‌دار مستقیماً پوشش دارد: `tx.queryBuilder(lockingSelectQuery(...)).execute()` در `TransactionMySqlIntegrationTest.kt`، در سناریوهای commit و rollback. تست رندر به‌تنهایی شاهد اجرای Connection نیست؛ شاهد runtime از Integration Test می‌آید.
- از ۹ تست Integration، دو تست opt-in/configuration هستند و هفت سناریو با جدول InnoDB واقعی اجرا می‌شوند؛ صفر skipped در XML موجود نشان می‌دهد سناریوهای دیتابیسی این اجرا به‌دلیل خاموش‌بودن opt-in رد نشده‌اند.

### وضعیت نهایی معیارهای پذیرش

| معیار | وضعیت | شاهد ثبت‌شده |
|---|---|---|
| 1. API عمومی `connection {}` و default fail-closed | تأییدشده با تست و compile | تست fake implementation بدون scoped support؛ consumer مستقل با dependency مستقیم executor کامپایل شده است. |
| 2. state/guard مشترک و lifecycle مستقل | تأییدشده با بررسی کد و تست | `TransactionExecutionScope`/guard مشترک؛ lifecycle و cleanup جدا؛ تست‌های Connection و Transaction. |
| 3. `ctx.queryBuilder(api)` روی همان Connection و ترتیب binding | تأییدشده با تست proxy و Integration | Connection Scope و Integration چند Query روی یک Transaction Connection را بررسی می‌کنند. |
| 4. `BuiltQuery`/`BoundQuery` مستقیم و TABLE در خارج از دامنه | تأییدشده با بررسی کد | مسیر عمومی scoped مورد توافق، `queryBuilder(api)` است؛ مسیر مستقیم در v2.0 اضافه نشده است. |
| 5. مالکیت و close-once | تأییدشده با تست proxy و Integration | شمارش acquisition/close در `TransactionExecutionTest` و `multiple_queries_use_one_transaction_connection_and_close_it_once`. |
| 6. تفاوت `connection {}` و `transaction {}` | تأییدشده با تست | Connection Scope بدون تغییر `autoCommit` و commit/rollback؛ Transaction با commit/rollback خودش. |
| 7. latch شدن Failureهای execution، validation و mapping | تأییدشده با تست proxy | تست‌های `TransactionExecutionTest` و `QueryConsumerExecuteAsTest`؛ Query بعدی رد می‌شود و Success callback Failure ثبت‌شده را پاک نمی‌کند. |
| 8. رد `executeOut()` و جلوگیری از bypass | تأییدشده با تست | تست‌های Phase 6 برای root executor و `executeOut()` در Starting، Active و Finalizing. |
| 9. Exception/Error و خطاهای cleanup/commit/rollback/close | تأییدشده با تست proxy و Integration | تست‌های cleanup در `TransactionExecutionTest`؛ Integration خطای SQL واقعی، rollback و حفظ خطای driver را بررسی می‌کند. |
| 10. Context منقضی‌شده و thread غیرمالک | تأییدشده با تست | تست‌های Context scoped و root lineage در `TransactionExecutionTest`. |
| 11. nesting و cross-nesting | تأییدشده با تست | تست‌های fail-closed در `TransactionExecutionTest`. |
| 12. suite و Integration | تأییدشده با اجرای suiteها | اجرای کامل قبلی executor: ۱۲۸ case، ۱۲۱ موفق، ۷ skipped، صفر failure/error؛ اجرای جداگانهٔ Integration: ۹/۹ موفق و صفر skipped. |
| 13. انتشار API برای consumer مستقل و وضعیت Git | تأییدشده با compile و بررسی Git | consumer مستقل با فقط dependency مستقیم executor موفق کامپایل شده؛ فایل‌های LockForUpdate با وضعیت `AM` حفظ شده‌اند و هیچ stage/commit انجام نشده است. |

### وضعیت Phaseها و محدودیت‌ها

- Phaseهای پیاده‌سازی 1 تا 6 تکمیل شده‌اند؛ Phase 6 قرارداد no-bypass را برای همان root lineage و هماهنگی Starting/Active/Finalizing با تست‌های latch-based تأیید کرده است.
- بررسی‌های آماده‌سازی MySQL در Phaseهای 7 تا 9 تحلیلی بودند؛ نتیجهٔ Integration واقعی فقط از اجرای موفق قبلی و XML یادشده ثبت می‌شود. تست MySQL در این Finalization تکرار نشد.
- اعتبارسنجی unit/proxy و consumer compile، رفتار را در محدودهٔ تست‌های ثبت‌شده تأیید می‌کند؛ پشتیبانی dialectهای دیگر یا تضمین‌های فراتر از MySQL آزمایش‌شده ادعا نمی‌شود.
- این مرحله فقط همین پلن را به‌روزرسانی کرد. پلن V1.0 و source/test/build دست‌نخورده ماندند. هشت فایل LockForUpdate همچنان `AM` هستند و staging area تغییر نکرد.

