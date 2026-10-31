# فهرست پروژه و ساختار ماژولار

این پروژه یک پروژه چندماژوله Kotlin/Gradle است که هسته آن برای ساخت، رندر و اجرای کوئری‌های SQL و همچنین تعریف و اجرای migration طراحی شده است. ماژول‌ها در `settings.gradle.kts` ثبت شده‌اند و عمدتاً از Kotlin/JVM با toolchain نسخه 24 استفاده می‌کنند.

## نمای کلی درخت پروژه

```text
my_project/
├── app/                              # برنامه نمونه و نقطه ورود اصلی
├── utils/                            # ابزارهای عمومی قابل استفاده مجدد
├── tools/                            # ابزارهای داخلی و اسکریپت‌های کمکی
├── data_base/                        # بدنه اصلی کتابخانه دیتابیس
│   ├── core/                         # قراردادها و مدل‌های پایه
│   ├── manager/                      # مدیریت اتصال و اجرای عملیات دیتابیس
│   │   ├── connection/               # ایجاد و مدیریت اتصال
│   │   └── execute/                  # اجرای کوئری و دریافت نتیجه
│   ├── models/                       # لایه مدل‌ها
│   │   └── eloquent/                 # مدل‌سازی به سبک Eloquent
│   ├── query/                        # زیرسیستم ساخت و اجرای کوئری
│   │   ├── api/                      # APIهای عمومی و interfaceها
│   │   ├── ast/                      # ساختار درخت نحوی کوئری
│   │   ├── builder/                   # builderهای fluent
│   │   ├── dialect/                   # مدل داده و قرارداد رندر
│   │   ├── renderer/                 # رندر SQL، فعلاً برای MySQL
│   │   ├── executor/                 # اتصال builder/renderer به اجرا
│   │   └── example/                   # مثال‌های query builder
│   └── migration/                     # زیرسیستم migration و ساخت schema
│       ├── params/                    # انواع داده ستون‌ها
│       ├── ast/                       # AST مربوط به migration
│       ├── api/                       # قراردادهای عمومی migration
│       ├── builder/                   # builderهای جدول و ستون
│       ├── dialect/                   # قرارداد و مدل رندر migration
│       ├── renderer/                  # رندر migration، فعلاً برای MySQL
│       ├── executor/                  # اجرای migration
│       └── example/                   # مثال‌های migration
├── documents/                       # فهرست، ساختار و راهنماهای پروژه
│   ├── README.md                    # فهرست مستندات
│   ├── structure.md                 # ساختار تفصیلی ماژول‌ها
│   └── guides/                      # راهنماهای استفاده از ماژول‌ها
│       ├── connection.md
│       ├── collection.md
│       ├── migration/
│       └── query-builder/
├── buildSrc/                        # منطق مشترک build و convention pluginها
├── gradle/                            # wrapper و version catalog
├── gradlew / gradlew.bat              # Gradle Wrapper
├── settings.gradle.kts                # تعریف پروژه‌ها و subprojectها
└── gradle.properties                  # تنظیمات عمومی Gradle
```

## فهرست پروژه و مستندات

- [فهرست کامل مستندات](documents/README.md)
- [درخت کلی پروژه](#نمای-کلی-درخت-پروژه)

### راهنماهای ماژول‌ها

- [Connection و تنظیم اتصال](documents/guides/connection.md)
- [Collection](documents/guides/collection.md)
- [Migration و Schema](documents/guides/migration/README.md)
  - [ثبت و گروه‌بندی migrationها](documents/guides/migration/registration.md)
  - [AST و مثال‌های Migration](documents/guides/migration/ast-examples.md)
  - [انجام عملیات Migration](documents/guides/migration/execution.md)
- [Query Builder](documents/guides/query-builder/README.md)
  - [روش ارتباط‌گیری، استخراج و نتیجه](documents/guides/query-builder/README.md#connection)
  - [فهرست مثال‌های نسخه‌ها](documents/guides/query-builder/examples/README.md)
    - [مثال‌های 1.0](documents/guides/query-builder/examples/v1.0.md)
    - [مثال‌های 2.0](documents/guides/query-builder/examples/v2.0.md)
    - [مثال‌های 3.0](documents/guides/query-builder/examples/v3.0.md)
    - [مثال‌های 3.1](documents/guides/query-builder/examples/v3.1.md)
  - [قواعد Model، Query و Metadata](documents/guides/query-builder/rules.md)
