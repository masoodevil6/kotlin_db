# راهنمای Pipeline در Operation

Pipeline چند مرحله‌ی همگام را روی یک اجرای Operation به هم متصل می‌کند. هر Stage می‌تواند از APIهای امن Operation برای اجرای Query استفاده کند و نتیجه‌ی خود را به مرحله‌ی بعد بدهد. Stageها به‌تنهایی Connection یا Transaction جداگانه‌ای نمی‌سازند؛ lifecycle همچنان متعلق به Operation است.

در کد فعلی چند API با قراردادهای متفاوت وجود دارد. آن‌ها را با هم اشتباه نگیرید:

| API | چه زمانی مناسب است؟ | عبور به Stage بعد |
|---|---|---|
| **OperationPipeline<T>** | حفظ کدهای قدیمی و زنجیره‌های هم‌نوع | با **next(value)**؛ اگر next صدا زده نشود Pipeline همان‌جا تمام می‌شود. |
| **TypedOperationPipeline<I, O>** | زنجیره‌ی ساده با نوع خروجی متفاوت برای Stageها، بدون انتخاب معمولی برای توقف زودتر | خروجی هر Stage خودکار ورودی Stage بعد می‌شود. |
| **connectionPipeline** و **transactionPipeline** | Pipeline typed با امکان پایان زودتر و API متناسب با lifecycle | با **next(value)**؛ در Connection باید تصمیم **REUSE** هم داده شود. |
| **OperationPipelineDefinition<State, Result>** | Recipe قابل استفاده‌ی مجدد، State مشترک در یک اجرا و توقف Stageهای بعدی پس از مشاهده‌ی Failure | با ترکیب typed در Builder؛ Runner بین Stageها Failure را بررسی می‌کند. |

برای APIهای سطح بالاتر DatabaseHandle معمولاً از connectionPipeline، transactionPipeline یا Definition استفاده کنید. برای Operationهای قدیمی OperationPipeline را تغییر ندهید مگر آنکه واقعاً به قرارداد typed دیگری نیاز داشته باشید.

## تطبیق با الگوی load / validate / execute / finalize

الگوی نمونه‌ی شما مفید است: داده از Stageها عبور کند، هر Stage بخش خودش را انجام دهد، و در صورت نیاز Pipeline پیش از Stage بعدی پایان یابد. اما شکل کد نمونه عین API فعلی نیست. مرزهای واقعی Source این‌ها هستند:

| چیزی که در نمونه آمده | وضعیت API فعلی |
|---|---|
| **db.connection<MigrationState> { start { ... } }** | چنین Pipeline DSLای روی DatabaseHandleApi وجود ندارد. **connection { ... }** فعلی callback مربوط به QueryBuilderExecutor است. برای Stageهای typed از **connectionPipeline { ... }** استفاده کنید. |
| **ctx.state** در Connection/Transaction continuation Pipeline | در Contextهای این دو builder وجود ندارد. خروجی typed هر Stage را با **next(value)** به بعدی بدهید. اگر State مشترک لازم است، از Definition استفاده کنید. |
| **connection(ConnectionDecision.NEW)** | در enum وجود دارد، اما Pipeline فعلی آن را رد می‌کند؛ فقط REUSE قابل اجراست و Connection دومی باز نمی‌شود. |
| **OperationPipelineDefinition و ctx.stop()** | Definition از **create(stateFactory) { start { ... }.then { ... } }** ساخته می‌شود. Context آن متد stop() ندارد. توقف dispatch در Definition زمانی رخ می‌دهد که Failure در مرز Stage مشاهده شود؛ توقف معمولی بر اساس نتیجه‌ی دامنه API فعلی ندارد. |
| نتیجه‌ی **ExecuteResult<MigrationState>** برای هر توقف | continuation Pipeline نتیجه را با **PipelineCompletion** نوع‌دار می‌کند تا مشخص باشد کدام Stage پایان داده است؛ آن را خودکار به State قابل‌تغییر تبدیل نمی‌کند. |

بنابراین در کد فعلی این دو نیاز را جدا انتخاب کنید:

- اگر مهم است که با رد شدن Validation، Stageهای بعدی اصلاً dispatch نشوند، از Connection یا Transaction continuation Pipeline استفاده کنید و داده‌ی هر مرحله را داخل نوع‌های خروجی و ورودی منتقل کنید. برای این APIها State property نداریم.
- اگر State مشترک و typed در تمام Stageها مهم است، از OperationPipelineDefinition استفاده کنید. در آن، همه‌ی Stageها به‌ترتیب dispatch می‌شوند مگر Failure در مرز ثبت شده باشد. برای Validation ناموفق، Stageهای بعدی باید اثر جانبی را با توجه به State انجام ندهند؛ این با «عدم dispatch شدن Stage» یکی نیست.

اگر لازم است هم State مشترک، هم توقف دامنه‌ای صریح، هم نتیجه‌ی نهایی مستقیم از نوع MigrationState و هم سیاست انتخاب Connection در یک DSL واحد وجود داشته باشد، ترکیب این قابلیت‌ها در API فعلی حاضر نیست. نمونه‌ی مستندشده نباید آن ترکیب را به‌عنوان کد قابل کامپایل معرفی کند.

## قواعد مشترک

- اجرا synchronous است؛ Stageها به ترتیب تعریف‌شده و روی همان thread اجرا می‌شوند.
- در یک Pipeline، تمام Stageها از همان Operation Scope و Connection استفاده می‌کنند. Transaction Pipeline نیز یک Transaction متعلق به Operation دارد.
- Context هر Stage مختص همان Stage است، پس از بازگشت یا پرتاب Exception منقضی می‌شود و فقط از thread سازنده‌ی آن قابل استفاده است. Context را ذخیره نکنید و از callback ناهمگام یا thread دیگری استفاده نکنید.
- Context متدهای Operation مانند executeRead، executeSelect، executeInsert، executeUpdate، executeDelete و executeDdl را در اختیار می‌گذارد؛ خود Scope و JDBC Connection خام را افشا نمی‌کند.
- نتیجه‌ی خطای دیتابیس را بررسی کنید. ExecuteResult.Failure یا context.failed(...) Failure را در Operation ثبت می‌کند؛ یک شیء خطای دامنه که صرفاً به‌عنوان مقدار برگردانده شود، به‌تنهایی Failure مربوط به Operation نیست.
- Exception عادی که از Stage خارج شود از مسیر Failure و cleanup متعلق به Operation عبور می‌کند. Error با هویت اصلی خود پس از cleanup دوباره پرتاب می‌شود.

برای جزئیات عمر Connection و Transaction، [راهنمای Connection و Transaction](../connection_transaction/README.md) را ببینید. برای مشاهده‌ی lifecycle بدون تغییر نتیجه‌ی اجرا، [راهنمای رویدادهای Operation](../events/README.md) را بخوانید.

## 1. OperationPipeline<T> قدیمی

این API برای سازگاری باقی مانده است. نوع ورودی و خروجی تمام Stageها یکسان است. Stage اول ورودی ندارد؛ Stageهای بعدی مقدار Stage قبلی را می‌گیرند. Stage باید next(value) را صدا بزند تا مقدار به Stage بعد منتقل شود:

~~~kotlin
import gog.my_project.data_base_operation.then.OperationPipeline
import gog.my_project.data_base_operation.then.executePipeline

val pipeline = OperationPipeline.start<String> {
    next("customer-42")
}.then { customerId ->
    next("validated:" + customerId)
}.then { validatedId ->
    "ready:" + validatedId
}

val result = operationExecutor.executePipeline(pipeline)
~~~

در مثال بالا Stage آخر next ندارد، بنابراین رشته‌ی ready:validated:customer-42 نتیجه‌ی نهایی است. اگر هر Stage پیش از next تمام شود، Stageهای بعدی اجرا نمی‌شوند و مقدار بازگشتی همان Stage نتیجه‌ی Pipeline خواهد بود:

~~~kotlin
val lookup = OperationPipeline.start<String> {
    "not-found" // مرحله‌ی بعد اجرا نمی‌شود
}.then { value ->
    "unreachable:" + value
}
~~~

next() کنترل جریان Pipeline است، نه علامت موفقیت یا شکست Operation. در این API اگر Failure ثبت شود اما Stage هنوز next() را درخواست کند، مراحل بعدی ممکن است dispatch شوند؛ عملیات محافظت‌شده‌ی دیتابیس Failure ثبت‌شده را رعایت می‌کنند و نتیجه‌ی نهایی Operation همچنان Failure می‌ماند. برای توقف dispatch مرحله‌ی بعد بلافاصله پس از مشاهده‌ی Failure، از Definition استفاده کنید.

Pipeline را می‌توان به Executor داد تا Operation Connection Scope جدیدی اجرا کند، یا در Scope موجود اجرا کرد:

~~~kotlin
val result = operationExecutor.executePipeline(pipeline)
val resultInExistingScope = operationScope.executePipeline(pipeline)
~~~

مسیر Scope موجود lifecycle را تغییر نمی‌دهد؛ برای مثال، Pipeline می‌تواند داخل Scope و Lock فعلی اجرا شود. Scope را از callback بیرون نبرید.

## 2. TypedOperationPipeline<I, O>

این نسخه ورودی و خروجی را مستقل و نوع خروجی هر Stage را در زمان کامپایل دنبال می‌کند. Stageهای زنجیره به‌صورت پیوسته اجرا می‌شوند؛ این API متد next() ندارد:

~~~kotlin
import gog.my_project.data_base_operation.then.TypedOperationPipeline
import gog.my_project.data_base_operation.then.executePipeline

val pipeline = TypedOperationPipeline.start<String, Int> { input, context ->
    input.length
}.then<String> { length, context ->
    "length=" + length
}

val result = operationExecutor.executePipeline(pipeline, "hello")
~~~

خروجی این نمونه ExecuteResult<String> است. برای اجرای آن در Scope از قبل فعال، overload اکستنشن operationScope.executePipeline(pipeline, input) را می‌توان به‌کار برد. اگر اجرای مرحله‌ی بعد باید انتخابی باشد یا یک Stage بتواند زودتر پایان دهد، APIهای continuation بخش بعد مناسب‌ترند.

## 3. Pipelineهای typed با continuation

### Connection Pipeline

روی DatabaseHandleApi با connectionPipeline شروع کنید. execute() زنجیره را در یک Manager lease و یک Operation Connection Scope اجرا می‌کند:

~~~kotlin
import gog.my_project.data_base_manager.facade.connectionPipeline
import gog.my_project.data_base_operation.then.ConnectionDecision

val pipeline = database.connectionPipeline<String> { context ->
    context.next("customer-42").connection(ConnectionDecision.REUSE)
}.then<Int> { customerId, context ->
    context.next(customerId.length).connection(ConnectionDecision.REUSE)
}.then<String> { length, _ ->
    "loaded:" + length
}

val result = pipeline.execute()
~~~

ConnectionPipelineStageContext.next(value) یک continuation درخواست می‌کند و باید با connection(REUSE) تکمیل شود. هر Stage حداکثر یک بار می‌تواند continuation درخواست کند. ConnectionDecision.NEW در پیاده‌سازی فعلی پشتیبانی نمی‌شود و به‌جای باز کردن Connection دوم، خطای پروتکل می‌دهد.

اگر یک Stage next() نخواهد، همان‌جا Pipeline را کامل می‌کند و مرحله‌های بعد اجرا نمی‌شوند. چون ممکن است هر Stage نقطه‌ی پایان باشد، نوع خروجی زنجیره از PipelineCompletionهای تو در تو استفاده می‌کند؛ Current(value) نتیجه‌ی مرحله‌ی نهایی اجراشده و Earlier(value) نتیجه‌ی توقف در یکی از مراحل قبلی را نشان می‌دهد. در نمونه‌ی سه‌مرحله‌ای بالا، نوع نتیجه‌ی موفق ExecuteResult<PipelineCompletion<PipelineCompletion<String, Int>, String>> است و مقدار مرحله‌ی نهایی در PipelineCompletion.Current قرار می‌گیرد.

اگر زنجیره همیشه باید تا آخر اجرا شود و خروجی واحد و ساده می‌خواهید، TypedOperationPipeline یا Definition را انتخاب کنید.

### Transaction Pipeline

برای یک زنجیره‌ی Transaction از transactionPipeline استفاده کنید. ادامه با context.next(value) درخواست می‌شود؛ این Context عمداً API انتخاب Connection ندارد:

~~~kotlin
import gog.my_project.data_base_manager.facade.transactionPipeline

val pipeline = database.transactionPipeline<String> { context ->
    context.next("order-42")
}.then<Int> { orderId, context ->
    context.next(orderId.length)
}.then<String> { length, _ ->
    "stored:" + length
}

val result = pipeline.execute()
~~~

تمام Stageها در همان Transaction هستند. اگر Operation با Failure تمام شود، commit انجام نمی‌شود و تصمیم rollback و cleanup با Operation است. Connection Pipeline به‌خودی‌خود Transaction ایجاد نمی‌کند. این دو lifecycle را با هم اشتباه نگیرید.

این Manager-bound builderها برای DatabaseHandle مدیریت‌شده طراحی شده‌اند؛ پیاده‌سازی سفارشی DatabaseHandleApi که lifecycle مدیریت‌شده ارائه نکند هنگام اجرا Failure می‌گیرد.

## 4. Definition و State مشترک در هر اجرا

OperationPipelineDefinition<State, Result> یک Recipe قابل استفاده‌ی مجدد است. زمان ساخت Definition نه Recipe اجرا می‌شود و نه State Factory. هنگام هر اجرا، بعد از admission مربوط به Operation و پیش از گرفتن resource، Recipe materialize و State Factory دقیقاً یک بار فراخوانی می‌شود. همه‌ی Stageهای همان اجرا به State داخل همان Frame دسترسی دارند. برای مستقل‌بودن اجراها، State Factory باید برای هر فراخوانی یک نمونه‌ی تازه بسازد؛ خود API نمی‌تواند جلوی برگرداندن عمدی یک Singleton را بگیرد.

~~~kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.executeConnectionDefinition
import gog.my_project.data_base_operation.then.OperationPipelineDefinition

data class ImportState(var rowsRead: Int = 0)

val importDefinition = OperationPipelineDefinition.create<ImportState, String>(
    stateFactory = { ImportState() },
) {
    start<Int> { context ->
        context.state.rowsRead += 1
        40
    }.then<Int, String> { count, context ->
        context.state.rowsRead += 1
        "processed=" + (count + context.state.rowsRead)
    }
}

val result: ExecuteResult<String> = database.executeConnectionDefinition(importDefinition)
~~~

برای Transaction، از extension executeTransactionDefinition(definition) استفاده کنید. اگر با OperationExecutionExecutor کار می‌کنید، متدهای executeConnectionDefinition(definition) و executeTransactionDefinition(definition) در دسترس‌اند. در هر دو حالت مالکیت lifecycle با Operation می‌ماند.

### قرارداد Stage و Failure در Definition

- هر then<In, Out> ورودی Stage قبلی را با نوع In می‌گیرد و Out را به Stage بعد می‌دهد. نتیجه‌ی نهایی Definition باید با Result سازگار باشد.
- هر Stage یک OperationPipelineStageExecutionContext<State> تازه می‌گیرد. context.state همان State مشترک اجرای جاری است؛ خود Context پس از Stage معتبر نمی‌ماند.
- اگر Failure عادی پیش از شروع Runner ثبت شده باشد، Stage اول dispatch نمی‌شود. پس از هر Stage نیز Runner در مرز بعدی Failure Owner را بررسی می‌کند؛ اگر Failure ثبت شده باشد، Stage بعدی dispatch نمی‌شود.
- اگر Stage Failure را ثبت کند و عادی برگردد، خروجی آن به‌عنوان نتیجه‌ی نهایی جعل نمی‌شود. Runner با Stopped متوقف می‌شود و Executor Failure معتبر را از Failure Owner انتخاب می‌کند.
- Error ثبت‌شده یا پرتاب‌شده با همان هویت propagate می‌شود. Exception عادی پرتاب‌شده از Stage از مسیر مدیریت Failure و cleanup Operation عبور می‌کند.
- این بررسی مرزی اجرای Stage در حال اجرا را لغو نمی‌کند. Failure ممکن است بعد از بررسی مرز و شروع Stage بعدی ثبت شود؛ در این رقابت Stage بعدی می‌تواند شروع شده باشد، اما عملیات guarded همچنان Failure latch را رعایت می‌کنند.

Definition گزینه‌ی مناسبی است وقتی Recipe باید بارها استفاده شود، State اجرایی لازم دارید و Stageهای بعدی نباید پس از مشاهده‌ی Failure اجرا شوند. اگر داده‌ی دامنه‌ای شبیه Failure دارید که نباید Operation را fail کند، آن را به‌عنوان مقدار معمولی در State یا خروجی Stage نگه دارید و context.failed(...) را فقط برای Failure واقعی Operation به‌کار ببرید.

## خطاها، نتیجه‌ها و انتخاب الگو

APIهای اجرایی نتیجه‌ی ExecuteResult<T> دارند؛ Exception عادیِ پرتاب‌شده طبق lifecycle مالک اجرا ممکن است به ExecuteResult.Failure تبدیل شود، درحالی‌که Error با همان هویت propagate می‌شود. در overloadهایی که روی Scope موجود اجرا می‌شوند، Exception ممکن است به caller پرتاب شود تا lifecycle بیرونی آن را مدیریت کند. موفقیت را از ExecuteResult.Success.result و Failure را از ExecuteResult.Failure.exception بخوانید. نوع Pipeline به‌تنهایی نوع نتیجه‌ی موفق را مشخص نمی‌کند؛ برای typed continuation ممکن است PipelineCompletion نیز بررسی شود.

| نیاز | انتخاب پیشنهادی |
|---|---|
| حفظ رفتار قدیمی با Stageهای هم‌نوع | OperationPipeline<T> |
| تبدیل ساده‌ی ورودی به خروجی با نوع‌های متفاوت و اجرای همه‌ی مراحل | TypedOperationPipeline<I, O> |
| ادامه‌ی اختیاری، با انتخاب lifecycle صریح | connectionPipeline یا transactionPipeline |
| Recipe قابل استفاده‌ی مجدد، State اشتراکی در هر اجرا و توقف dispatch پس از Failure | OperationPipelineDefinition<State, Result> |

Pipeline قابلیت branching عمومی، اجرای async، Retry، Recovery یا بازکردن Connection تازه برای Stage بعدی ارائه نمی‌کند. برای عملیات چندمرحله‌ای همواره lifecycle و نتیجه‌ی Operation را در نظر بگیرید؛ صرف استفاده از Pipeline Atomicity دیتابیس را تضمین نمی‌کند.

## پیاده‌سازی و تست‌های مرتبط

APIهای اصلی در این فایل‌ها قرار دارند:

- data_base/operation/executor/src/main/kotlin/then/OperationPipeline.kt
- data_base/operation/executor/src/main/kotlin/then/TypedOperationPipeline.kt
- data_base/operation/executor/src/main/kotlin/then/TypedContinuationOperationPipeline.kt
- data_base/operation/executor/src/main/kotlin/then/TypedContinuationPipelineContext.kt
- data_base/operation/executor/src/main/kotlin/then/OperationPipelineDefinition.kt
- data_base/operation/executor/src/main/kotlin/then/OperationPipelinePlan.kt
- data_base/operation/executor/src/main/kotlin/then/OperationPipelineStageExecutionContext.kt
- data_base/manager/src/main/kotlin/facade/DatabaseHandlePipeline.kt

نمونه‌های آزمون در OperationPipelineTest، TypedContinuationPipelineTest و OperationPipelineDefinitionExecutorTest قراردادهای اجرا، انتقال مقدار، انقضای Context، Failure و cleanup را نشان می‌دهند.
