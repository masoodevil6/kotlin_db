# Operation Pipeline Vision
## چشم‌انداز طراحی Pipeline در ماژول Operation کتابخانه kotlin_db

**وضعیت:** Design Vision  
**هدف:** تعریف مدل مفهومی، قراردادهای رفتاری و API موردنظر برای Pipeline در Operation

---

## 1. هدف اصلی

هدف، توسعه یک Pipeline برای اجرای عملیات متوالی در چارچوب Operation است؛ به‌گونه‌ای که چند مرحله بتوانند با ترتیب مشخص اجرا شوند و در عین حال:

- نوع ورودی و خروجی هر Stage مشخص و Type-safe باشد.
- انتقال داده بین Stageها به‌صورت صریح انجام شود.
- امکان نگهداری اطلاعات مشترک در طول اجرای Pipeline وجود داشته باشد.
- مدیریت Connection و Lifecycle همچنان تحت مالکیت Operation باقی بماند.
- Failure Handling و محدودیت‌های Scope حفظ شوند.
- API برای Kotlin طبیعی و برای Java قابل استفاده باشد.

Pipeline نباید صرفاً مجموعه‌ای از callbackها باشد. باید یک مدل مشخص برای جریان داده، وضعیت اجرا و مدیریت منابع ارائه کند.

## 2. مدل مفهومی Pipeline

Pipeline از سه مفهوم مستقل تشکیل می‌شود:

1. **Stage Input/Output:** داده‌ای که یک Stage دریافت می‌کند و داده‌ای که به Stage بعدی منتقل می‌کند.
2. **Pipeline State:** اطلاعات مشترکی که در طول اجرای Pipeline نگهداری می‌شوند.
3. **Operation Context:** دسترسی Stage به امکانات Operation، از جمله اجرای Query و مدیریت Connection.

این سه مفهوم نباید با یکدیگر ادغام شوند.

### 2.1. جریان داده با next()

هر Stage می‌تواند با استفاده از `next()` داده‌ای را به Stage بعدی منتقل کند.

برای مثال:

```kotlin
start<Migration> { ctx ->
    ctx.next(loadMigration())
        .connection(ConnectionDecision.REUSE)
}
    .then<Migration, ValidationResult> { migration, ctx ->
        ctx.next(validate(migration))
            .connection(ConnectionDecision.REUSE)
    }
    .then<ValidationResult, ExecutionResult> { validation, ctx ->
        // ادامه پردازش
    }
```

قرارداد Genericها:

- `start<Out>` نوع داده‌ای را مشخص می‌کند که Stage آغازین با `next()` تولید می‌کند.
- `then<In, Out>` نوع ورودی Stage و نوع داده‌ای را مشخص می‌کند که آن Stage می‌تواند با `next()` به Stage بعدی منتقل کند.
- ورودی هر Stage باید با خروجی Stage قبلی سازگار باشد.
- نوع خروجی یک Stage نباید با نوع ورودی آن اشتباه گرفته شود.

هر Stage باید قرارداد نوعی مستقل و روشن داشته باشد.

### 2.2. قرارداد توقف Pipeline

Stage می‌تواند بدون فراخوانی `next()` به کار خود پایان دهد.

قواعد مورد توافق:

- فراخوانی `next()` باعث ادامه Pipeline به Stage بعدی می‌شود.
- اگر Stage بدون فراخوانی `next()` پایان یابد، Pipeline متوقف می‌شود.
- فراخوانی چندباره `next()` در یک Stage خطای پروتکل است.
- اگر Stage پس از فراخوانی `next()` خطا پرتاب کند، Stage بعدی نباید اجرا شود.
- مقدار بازگشتی یک Stage باید صرفاً یکی از این دو حالت باشد:
    - `Unit` برای توقف بدون ادامه
    - نتیجه `next()` برای ادامه Pipeline

بازگرداندن یک مقدار معمولی مانند `ValidationResult` یا `ExecutionResult` به‌عنوان خروجی مستقیم Stage مجاز نیست، مگر آنکه قرارداد API صریحاً تغییر کند.

**نکته:** در طراحی جدید، خروجی نهایی Pipeline از طریق State تعیین می‌شود، نه از آخرین `next()`.

## 3. Pipeline State

یکی از تصمیم‌های اصلی این طراحی، جداسازی جریان داده از اطلاعات مشترک اجرای Pipeline است.

`next()` مسئول انتقال داده بین Stageها است؛ اما ممکن است یک نتیجه برای چند Stage یا بخش دیگر فرایند لازم باشد.

برای این نیاز، Pipeline دارای State خواهد بود.

### 3.1. تعریف State

نمونه:

```kotlin
data class MigrationState(
    var migration: Migration? = null,
    var validation: ValidationResult? = null,
    var execution: ExecutionResult? = null
)
```

State می‌تواند اطلاعاتی را نگهداری کند که در مراحل مختلف فرایند موردنیاز هستند.

### 3.2. تعریف State در connection

API موردنظر:

```kotlin
val result = db.connection<MigrationState> {
    // Pipeline definition
}
```

معنای Generic در این سطح:

`MigrationState` نوع State متعلق به اجرای Pipeline است.

نتیجه اجرای Connection از نوع زیر خواهد بود:

```kotlin
ExecuteResult<MigrationState>
```

بنابراین خروجی Pipeline مستقل از نوع داده‌های منتقل‌شده با `next()` خواهد بود.

### 3.3. دسترسی به State

تمام Stageها باید از طریق Context به یک نمونه مشترک State دسترسی داشته باشند:

```kotlin
.then<Migration, ValidationResult> { migration, ctx ->
    ctx.state.migration = migration

    val validation = validate(migration)
    ctx.state.validation = validation

    ctx.next(validation)
        .connection(ConnectionDecision.REUSE)
}
```

قواعد مالکیت State:

- State متعلق به هر اجرای Pipeline است.
- تمام Stageهای یک اجرا به همان نمونه دسترسی دارند.
- اجرای مجدد یک Builder باید State مستقل داشته باشد.
- State نباید متعلق به Builder باشد.
- State نباید به‌صورت Global یا در یک Map مشترک نگهداری شود.
- State مستقل از Connection است و نباید با تعویض Connection از بین برود.

### 3.4. نتیجه Pipeline

در پایان اجرای موفق Pipeline، State نتیجه اجرای Connection خواهد بود.

اگر Pipeline زودتر متوقف شود، State موجود همچنان باید قابل بازگشت باشد.

اگر Operation با Failure مواجه شود، نتیجه باید Failure را منعکس کند؛ نباید موفقیت را صرفاً به دلیل وجود یک State ناقص گزارش کرد.

در صورت نیاز به نگهداری اطلاعات تکمیلی پس از Failure، این موضوع باید با قرارداد Failure Result سازگار باشد و نباید باعث دور زدن Failure Latch شود.

## 4. مدل API موردنظر

دو ورودی اصلی برای تعریف عملیات در نظر گرفته می‌شود:

```kotlin
connection<State> {
    // Connection Pipeline
}
```

```kotlin
transaction<State> {
    // Transaction Pipeline
}
```

قرار نیست APIهای موازی مانند موارد زیر به‌عنوان ورودی اصلی معرفی شوند:

```kotlin
connectionPipeline()
transactionPipeline()
```

همچنین قرار نیست کاربر برای اجرای Pipeline به فراخوانی جداگانه `.execute()` نیاز داشته باشد.

بلوک بیرونی `connection` یا `transaction` مرز تعریف Pipeline است. پس از تکمیل تعریف، Operation مسئول اجرای آن خواهد بود.

### 4.1. یکپارچگی با عملیات ساده

هدف این است که عملیات ساده و Pipeline از طریق همان APIهای اصلی قابل تعریف باشند.

نمونه مفهومی:

```kotlin
val result = db.connection<MyState> {
    // Simple operation
}
```

و:

```kotlin
val result = db.connection<MyState> {
    start<Migration> { ctx ->
        ctx.next(loadMigration())
            .connection(ConnectionDecision.REUSE)
    }
    .then<Migration, ValidationResult> { migration, ctx ->
        val validation = validate(migration)

        ctx.state.migration = migration
        ctx.state.validation = validation

        ctx.next(validation)
            .connection(ConnectionDecision.REUSE)
    }
}
```

نحوه تشخیص و مدل‌سازی عملیات ساده از Pipeline هنوز باید در طراحی API و امضای Kotlin/JVM حل شود. نباید برای این کار به `Any`، تشخیص Runtime نوع بازگشتی یا overloadهای غیرقابل‌تمایز مبتنی بر `Function1` متوسل شد.

اگر قرارداد قبلی `connection` و `transaction` تغییر کند، این تغییر باید آگاهانه و هماهنگ در لایه‌های وابسته انجام شود.

## 5. مدیریت Connection

در Connection Pipeline، هر Stage می‌تواند درباره نحوه ادامه کار Connection تصمیم بگیرد.

```kotlin
enum class ConnectionDecision {
    REUSE,
    NEW
}
```

### REUSE

- Stage بعدی از همان Connection استفاده می‌کند.
- Scope همان Scope قبلی است.
- Failure Latch و وضعیت Operation مشترک باقی می‌مانند.
- Connection جدیدی ایجاد نمی‌شود.

### NEW

رفتار موردنظر:

- Connection قبلی بسته می‌شود.
- Scope قبلی پایان می‌یابد.
- یک Connection مستقل ایجاد می‌شود.
- Stage بعدی با Scope جدید اجرا می‌شود.
- مقدار `next()` به Stage بعدی منتقل می‌شود.
- State متعلق به اجرای Pipeline حفظ می‌شود.

`NEW` بخشی از قابلیت عمومی Pipeline است و نباید مختص Migration طراحی شود.

**وضعیت:** پیاده‌سازی و هماهنگی Lifecycle برای `NEW` هنوز نیازمند طراحی و بررسی است. صرف اضافه‌کردن enum کافی نیست.

### محدودیت Transaction Pipeline

Transaction Pipeline امکان تغییر Connection با `NEW` را ندارد.

Context مربوط به Transaction نباید متد `.connection()` ارائه کند.

Transaction باید مالکیت و محدودیت‌های Transaction را حفظ کند و نباید با Connection Pipeline یک Context یکسان داشته باشد.

## 6. Operation Context و Lifecycle

Operation همچنان مالک اصلی منابع و Lifecycle است.

مسئولیت‌های Operation:

- ایجاد و مدیریت Connection
- ایجاد و مدیریت Scope
- کنترل فعال بودن Scope
- مدیریت Thread و Stage Lifetime
- کنترل Failure Latch
- بستن منابع
- Commit و Rollback در مسیر Transaction
- مدیریت خطاهای اصلی و ثانویه

Pipeline نباید این مسئولیت‌ها را دوباره پیاده‌سازی کند.

### 6.1. Context هر Stage

هر Stage باید Context معتبر و مختص همان Stage دریافت کند.

قواعد:

- Context فقط در طول اجرای همان Stage معتبر است.
- استفاده از Context پس از پایان Stage مجاز نیست.
- Context باید Thread-bound باشد.
- دسترسی به Operation باید از طریق Guardهای معتبر انجام شود.
- Context نباید امکان دور زدن Scope یا Failure Latch را فراهم کند.
- تمام Stageها باید State مشترک اجرای Pipeline را ببینند.

### 6.2. اجرای Pipeline در Scope موجود

در کنار اجرای مستقل Pipeline، در آینده باید امکان اجرای Pipeline داخل Scope موجود نیز فراهم شود.

این قابلیت نباید Lifecycle جدید و تو‌در‌تو ایجاد کند.

اجرای داخل Scope موجود باید از همان Scope و Operation استفاده کند.

رفتار `NEW` در این حالت نیازمند طراحی جداگانه است و نباید بدون هماهنگی با مالک Scope پیاده‌سازی شود.

## 7. Failure Handling

Pipeline باید با مدل Failure موجود در Operation هماهنگ باشد.

قواعد:

- Failure واقعی Query یا Operation باید Failure Latch را فعال کند.
- Failure عادی دامنه‌ای که صرفاً به‌عنوان یک مقدار بازگردانده شده، لزوماً Operation Failure نیست.
- پس از فعال شدن Failure Latch، عملیات عادی بعدی باید رد شوند.
- `next()` یک دستور کنترلی است، نه Query؛ بنابراین نباید صرفاً به دلیل Failure Latch غیرقابل‌فراخوانی شود.
- خطای اصلی باید حفظ شود.
- خطاهای بعدی باید به‌عنوان خطاهای ثانویه نگهداری شوند.
- خطای Fatal باید طبق قواعد Operation مدیریت و در صورت لزوم مجدداً پرتاب شود.
- Pipeline نباید Failure را با مقدار State یا نتیجه دامنه‌ای پنهان کند.

در صورت پیاده‌سازی `NEW`، هر Scope جدید Failure Latch مستقل خود را خواهد داشت؛ اما نتیجه کل Pipeline باید Failure اصلی و خطاهای Recovery را به‌درستی حفظ کند.

## 8. SystemLock و محدودیت NEW

SystemLock فعلی از Session Advisory Lock استفاده می‌کند و به Connection مشخص وابسته است.

بنابراین بستن Connection مالک Lock، Lock را آزاد می‌کند.

در نتیجه:

- اجرای `NEW` در حین داشتن SystemLock نباید باعث از دست رفتن بی‌صدای Lock شود.
- تا زمانی که راهکار معتبر برای حفظ یا انتقال مالکیت Lock وجود ندارد، این وضعیت باید Fail Closed شود.
- Pipeline نباید تصور کند که با بازکردن Connection جدید، Lock قبلی همچنان برقرار است.

یکی از گزینه‌های قابل بررسی در آینده، جداسازی Connection مالک Lock از Connection اجرای کار است؛ اما این صرفاً یک گزینه پژوهشی است و هنوز تصمیم معماری محسوب نمی‌شود.

## 9. Kotlin و Java Compatibility

طراحی باید Genericهای واقعی و قابل استفاده در هر دو زبان داشته باشد.

مدل پایه:

```kotlin
Stage<In, Out>
```

در Kotlin:

```kotlin
start<Migration> { ctx -> ... }
    .then<Migration, ValidationResult> { migration, ctx -> ... }
```

در Java نیز باید امکان ساخت و ترکیب Stageهای نوع‌دار وجود داشته باشد.

نکات ضروری:

- Genericها باید در امضای عمومی API قابل مشاهده باشند.
- Type Safety نباید صرفاً به بررسی Runtime متکی باشد.
- محدودیت‌های Kotlin Receiver Lambda در Java باید بررسی شوند.
- در صورت نیاز، interfaceهای nominal یا SAM می‌توانند API جاوا را ساده‌تر کنند.
- Nullability و Java interoperability باید در امضاها بررسی شوند.
- نباید سازگاری Java صرفاً با مشاهده کد Kotlin تأیید شود؛ باید امضاهای واقعی JVM بررسی شوند.

## 10. مرزبندی ماژول‌ها

Pipeline بخشی از معماری Operation است.

مسئولیت‌های مربوط به اجرای Pipeline، Stage Context و Lifecycle باید در لایه Operation باقی بمانند.

Manager می‌تواند API مناسبی برای مصرف Pipeline ارائه کند، اما Operation نباید به Manager وابسته شود؛ این کار می‌تواند چرخه وابستگی ایجاد کند.

Query Builder نیز نباید مسئول مدیریت Lifecycle یا اجرای Pipeline باشد.

Adapterهای لازم برای Query Builder باید در لایه مناسب و بدون معکوس‌کردن وابستگی معماری قرار بگیرند.

## 11. مواردی که نباید انجام شوند

- استفاده از Global Registry یا Global Mutable State
- استفاده از WeakMap یا Map مخفی برای نگهداری Context
- قرار دادن State اجرای Pipeline داخل Builder
- دریافت مستقیم JDBC Connection توسط Stage
- دور زدن Operation Context
- ایجاد Lifecycle تو‌در‌تو هنگام اجرای Pipeline در Scope موجود
- استفاده از `Any` برای پنهان‌کردن ناسازگاری Genericها
- تشخیص نوع Pipeline با بررسی Runtime خروجی Lambda
- اضافه‌کردن APIهای موازی غیرضروری برای Connection و Transaction
- اضافه‌کردن `.execute()` به API مصرف‌کننده
- تغییر Migration در جریان توسعه Pipeline، مگر با درخواست مستقل و صریح
- بازگرداندن Transaction مستقل برای هر Migration Version

## 12. تصمیمات قطعی و موارد باز

### تصمیمات مورد توافق

1. Pipeline باید ورودی و خروجی هر Stage را به‌صورت Generic مشخص کند.
2. `start<Out>` نوع خروجی Stage آغازین را مشخص می‌کند.
3. `then<In, Out>` نوع ورودی و خروجی Stage را مشخص می‌کند.
4. `next()` مسئول انتقال داده بین Stageها است.
5. State مسئول نگهداری اطلاعات مشترک اجرای Pipeline است.
6. خروجی Connection Pipeline از نوع State خواهد بود.
7. State در هر اجرای Pipeline مستقل ساخته می‌شود.
8. تمام Stageهای یک اجرا به همان State دسترسی دارند.
9. APIهای اصلی `connection` و `transaction` باقی می‌مانند و ورودی Pipeline جداگانه‌ای لازم نیست.
10. کاربر نباید برای اجرای Pipeline متد `.execute()` فراخوانی کند.
11. Context مربوط به Connection و Transaction باید از نظر قابلیت‌ها تفکیک شوند.
12. Operation مالک Lifecycle و منابع باقی می‌ماند.
13. Failure Handling باید با Operation فعلی یکپارچه باشد.
14. `NEW` قابلیت عمومی Pipeline است، نه قابلیت اختصاصی Migration.

### موارد نیازمند طراحی و بررسی بیشتر

1. امضای دقیق API که هم عملیات ساده و هم Pipeline را با `connection` و `transaction` پشتیبانی کند.
2. پیاده‌سازی دقیق `start` و `then` با قرارداد `Unit` یا `PipelineNext`.
3. نحوه ساخت State برای هر اجرا و نحوه بازگرداندن آن در Failure.
4. رفتار دقیق `NEW` و هماهنگی آن با Lifecycle چند Scope.
5. محدودیت `NEW` هنگام فعال بودن SystemLock.
6. اجرای Pipeline داخل Scope موجود بدون ایجاد Lifecycle تو‌در‌تو.
7. API مناسب Java و بررسی امضاهای JVM.
8. سازگاری با پیاده‌سازی‌های سفارشی `DatabaseHandleApi`.
9. تست‌های مثبت و منفی Type Safety، تا حدی که زیرساخت تست اجازه دهد.

---

## 13. معیارهای پذیرش طراحی

طراحی زمانی قابل قبول است که:

- ورودی و خروجی Stageها در زمان Compile بررسی شوند.
- یک مقدار معمولی نتواند به‌جای `next()` به‌عنوان نتیجه Stage بازگردانده شود.
- Stage بتواند بدون `next()` Pipeline را متوقف کند.
- Stage بعدی فقط در صورت ادامه معتبر اجرا شود.
- State بین Stageهای یک اجرا مشترک و بین اجراهای مختلف مستقل باشد.
- نتیجه موفق Pipeline همان State اجرای مربوطه باشد.
- Failure واقعی توسط Operation مدیریت شود.
- REUSE همان Connection و Scope را حفظ کند.
- Transaction Context امکان تغییر Connection نداشته باشد.
- Pipeline به‌صورت مستقل و در آینده داخل Scope موجود قابل اجرا باشد.
- وابستگی ماژول‌ها بدون Cycle باقی بماند.
- API از Kotlin و Java قابل استفاده باشد.

## نتیجه

هدف نهایی ساخت یک Pipeline نوع‌دار و قابل ترکیب است که سه مسئولیت را از هم جدا نگه می‌دارد:

- **Data Flow:** انتقال داده بین Stageها با `next()`
- **Execution State:** نگهداری اطلاعات مشترک با `ctx.state`
- **Operation Lifecycle:** مدیریت Connection، Scope، Transaction و Failure توسط Operation

این تفکیک، اساس طراحی جدید Pipeline خواهد بود. پیاده‌سازی باید از این مدل پیروی کند و نباید برای ساده‌ترشدن چند امضای API، مرزهای معماری آن را از بین ببرد.