# Registration Attributes

`DatabaseRegistrationAttribute<T>` کلیدی نوع‌دار برای الصاق یک مقدار برنامه‌ای به یک Registration است. از Attribute برای metadata متعلق به یک Registration استفاده کنید؛ برای انتخاب Registration همچنان `DatabaseKey` و برای تنظیم اتصال `DatabaseConfig` را به‌کار ببرید.

## تعریف کلید و ثبت مقدار

کلید را یک‌بار در property پایدار بسازید و همان نمونه را هم هنگام ثبت و هم هنگام خواندن استفاده کنید:

~~~kotlin
import gog.my_project.data_base_manager.facade.DatabaseRegistrationAttribute
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager

object MainDatabase : DatabaseKey
object AnalyticsDatabase : DatabaseKey

data class ServiceMetadata(
    val displayName: String,
    val readOnly: Boolean,
)

object RegistrationAttributes {
    val displayName = DatabaseRegistrationAttribute.create<String>()
    val serviceMetadata = DatabaseRegistrationAttribute.create<ServiceMetadata>()
}

val manager = DatabaseManager {
    database(MainDatabase) {
        config = mainConfig
        attribute(RegistrationAttributes.displayName, "main")
        attribute(
            RegistrationAttributes.serviceMetadata,
            ServiceMetadata(displayName = "Main DB", readOnly = false),
        )
    }

    database(AnalyticsDatabase) {
        config = analyticsConfig
        attribute(RegistrationAttributes.displayName, "analytics")
        attribute(
            RegistrationAttributes.serviceMetadata,
            ServiceMetadata(displayName = "Analytics DB", readOnly = true),
        )
    }
}
~~~

`attribute(key, value)` در بلوک همان `database(key) { ... }` مقدار را به Registration آن کلید وصل می‌کند. یک کلید مشترک را می‌توان برای Registrationهای مختلف با مقدارهای متفاوت به‌کار برد.

## خواندن از Handle

`DatabaseHandleApi` مستقیماً متد Attribute ندارد. Handle مدیریت‌شدهٔ Manager قابلیت اختیاری `DatabaseHandleAttributes` را ارائه می‌کند؛ آن را بررسی کنید و سپس با همان کلید مقدار را بخوانید:

~~~kotlin
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseHandleAttributes

val handle: DatabaseHandleApi = manager[MainDatabase]
val attributes = handle as? DatabaseHandleAttributes
    ?: error("This Handle does not expose Registration attributes")

val name: String? = attributes.attribute(RegistrationAttributes.displayName)
val metadata: ServiceMetadata? = attributes.attribute(RegistrationAttributes.serviceMetadata)

println(name)
println(metadata?.readOnly)
~~~

اگر کلید برای آن Registration ثبت نشده باشد، `attribute(key)` مقدار `null` برمی‌گرداند. نوع مورد انتظار از نوع کلید تعیین می‌شود و مقدارهای `null` هنگام ثبت پذیرفته نمی‌شوند؛ `T` باید non-null باشد.

پیاده‌سازی سفارشی `DatabaseHandleApi` مجبور نیست `DatabaseHandleAttributes` را پیاده‌سازی کند. بنابراین قابلیت را با cast بررسی کنید و فرض نکنید هر Handleای Attribute دارد.

## هویت کلیدها

کلیدها بر اساس هویت همان شیء شناخته می‌شوند، نه نوع مقدار یا نام متغیر. دو فراخوانی جداگانهٔ `DatabaseRegistrationAttribute.create<String>()` دو کلید مستقل می‌سازند؛ حتی اگر هر دو `String` نگه دارند. اگر هنگام ثبت و خواندن دو نمونهٔ جدا بسازید، کلید ثبت‌شده پیدا نمی‌شود و نتیجه `null` خواهد بود.

~~~kotlin
val primaryLabel = DatabaseRegistrationAttribute.create<String>()
val anotherLabel = DatabaseRegistrationAttribute.create<String>()

// این دو کلید مستقل‌اند، هرچند نوع مقدار هر دو String است.
~~~

کلیدها را در `object`, `companion object` یا propertyای با عمر مناسب برنامه نگه دارید. آن‌ها را در هر بار ثبت یا lookup دوباره نسازید.

## Snapshot و تغییرپذیری مقدارها

هنگام ساخته‌شدن Registration، نگاشت کلید/مقدار از Builder کپی می‌شود و Map داخلی قابل‌تغییر نیست. در نتیجه تغییر بعدی در Builder، مقدار ثبت‌شده را عوض نمی‌کند. این کپی فقط خود Map را پوشش می‌دهد؛ مقدارها با reference نگهداری می‌شوند و deep-copy نمی‌شوند.

~~~kotlin
data class RuntimeOptions(val labels: Set<String>)

val optionsAttribute = DatabaseRegistrationAttribute.create<RuntimeOptions>()
~~~

مقدارهای immutable مانند `String`، `data class` با propertyهای `val` و collectionهای read-only مناسب‌ترند. اگر شیء mutable ثبت کنید و بعداً همان شیء را تغییر دهید، Handle همان reference را می‌خواند و تغییر را می‌بیند. برای تنظیمات مشترک بین threadها، از مقدار immutable یا snapshot مستقل استفاده کنید.

اگر یک کلید در همان بلوک Registration چند بار ثبت شود، مقدار جدید در همان کلید جای مقدار قبلی را می‌گیرد. بهتر است برای هر Attribute یک مقدار روشن و نهایی تعیین کنید.

## ارتباط با Registration و Handle

Attribute به Registration متصل است، نه به اتصال JDBC یا یک اجرای خاص. Handleهای یک Registration به همان مقدارهای ثبت‌شده دسترسی دارند؛ دو Registration می‌توانند کلید یکسان را با مقدارهای جداگانه استفاده کنند. گرفتن Handle یا خواندن Attribute به‌تنهایی Connection باز نمی‌کند.

- برای ساخت Registration و تنظیم چند پایگاه داده، [راهنمای تعریف پایگاه داده](../database_define/README.md) را ببینید.
- برای انتخاب Registration با `DatabaseKey`، [راهنمای کلیدهای پایگاه داده](../database_keys/README.md) را بخوانید.
- برای APIهای Handle و اجرای کار، [راهنمای Executor](../executor/README.md) را ببینید.
