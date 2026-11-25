# کلید پایگاه داده و Object دسترسی

`DatabaseKey` نام و هویت یک Registration در `DatabaseManager` است. کلید، تنظیمات اتصال نیست؛ در زمان ثبت، هر کلید به یک `DatabaseConfig` وصل می‌شود و بعداً با همان کلید Handle آن Registration را می‌گیرید.

```kotlin
import gog.my_project.data_base_manager.facade.DatabaseKey

object MainDatabase : DatabaseKey
object AnalyticsDatabase : DatabaseKey
```

برای دو پایگاه دادهٔ ثابت، `object` یک نمونهٔ یکتا و قابل استفاده در کل برنامه فراهم می‌کند:

```kotlin
val main = manager[MainDatabase]
val analytics = manager[AnalyticsDatabase]
```

اگر یک خانواده از Registrationها را با شناسهٔ پارامتری تعریف می‌کنید، می‌توانید از `data class` استفاده کنید:

```kotlin
data class TenantDatabase(val tenantId: String) : DatabaseKey

val tenantA = manager[TenantDatabase("tenant-a")]
val tenantB = manager[TenantDatabase("tenant-b")]
```

کلیدهای `data class` با مقادیر propertyهایشان مقایسه می‌شوند؛ بنابراین ساختن نمونهٔ تازه با مقادیر برابر، همان Registration را پیدا می‌کند. در مثال بالا، `TenantDatabase("tenant-a")` در هر بار ساخت با نمونهٔ هم‌مقدار برابر است. propertyهای کلید را در مدتی که Manager از آن استفاده می‌کند تغییرپذیر نکنید؛ تغییر مقادیری که در `equals` یا `hashCode` دخیل‌اند می‌تواند lookup را نامعتبر کند.

`object` و `data class` فقط دو شیوهٔ ساخت کلید هستند. اگر کلاس معمولی بسازید و `equals` و `hashCode` را بازنویسی نکنید، هویت آن معمولاً بر اساس همان نمونه است؛ در این حالت برای lookup باید همان reference را نگه دارید.

ثبت دو کلید برابر در یک Manager رد می‌شود. دو کلید متفاوت می‌توانند حتی به تنظیمات یکسان اشاره کنند، اما در catalog دو Registration جدا هستند. خود کلید مشخص نمی‌کند اتصال به کدام سرور یا schema می‌رود؛ آن را `DatabaseConfig` تعیین می‌کند.
