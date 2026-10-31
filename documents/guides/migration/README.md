# Migration

<a id="migration-schemas"></a>
## Schemas

در این ماژول، «Schema» به تعریف و تغییر ساختار جدول‌ها در پایگاه دادهٔ انتخاب‌شده اشاره دارد. AST فعلی عملیاتی برای ساخت یا حذف خود database/schema ندارد؛ مقصد اتصال از `DefaultDatabaseConfig.config` می‌آید و در بخش [ارتباط با پایگاه داده](../connection.md#ارتباط-با-پایگاه-داده) توضیح داده شده است. قابلیت‌های Migration روی table، column، constraint و index کار می‌کنند.

DSL عملیات را به ASTهای مستقل تبدیل می‌کند. در یک migration چند عملیات به ترتیب نوشته می‌شوند؛ dialect از AST دستور DDL می‌سازد و `IMigrationExecutor` آن را اجرا می‌کند. مثال‌های موجود در `data_base:migration:example` زیر V1 قرار دارند. بیشتر مثال‌ها دو مسیر جدا دارند: `render()` برای دیدن SQL و `execute(...)` برای اجرای صریح. قبل از اجرای DDL، مقصد اتصال و SQL تولیدشده را بررسی کنید؛ مثال‌های این بخش به‌خودی‌خود schema آزمایشی نمی‌سازند.

### ساخت جدول و تعریف ستون

نمونهٔ زیر از `A1ExampleCreateTableV1` است. نام جدول و ستون‌ها، نوع داده، nullability، مقدار پیش‌فرض و ویژگی‌های کلید در بلوک DSL تعریف می‌شوند:

~~~kotlin
createTable {
    table { tableName("users") }
    ifNotExists()

    addColumn {
        name("id")
        dataType(IntType())
        notNull()
        autoIncrement()
        primaryKey()
    }
    addColumn {
        name("name")
        dataType(VarcharType(120))
        notNull()
    }
    addColumn {
        name("display_name")
        dataType(VarcharType(120))
        default("New user")
    }
}
~~~

`ifNotExists()` گزینهٔ `IF NOT EXISTS` را در AST جدول ثبت می‌کند. ویژگی‌های هر ستون در `IMigrationColumnAst` نگه‌داری می‌شوند: نام، `MigrationColumnDataType`، default، nullable بودن، auto increment و primary بودن. نوع‌های تعریف‌شده در ماژول params عبارت‌اند از `BooleanType`، `IntType`، `VarcharType(length)`، `TextType`، `DecimalType(precision, scale)`، `JsonType`، `DateType`، `TimeType`، `DateTimeType` و `TimestampType`. طول VARCHAR باید مثبت باشد؛ precisionهای fractional برای TIME، DATETIME و TIMESTAMP در بازهٔ 0 تا 6 اعتبارسنجی می‌شوند و برای DECIMAL نیز scale نمی‌تواند از precision بیشتر باشد.

### کلیدها و constraintهای جدول

در مثال `A1ExampleCreateTableConstraintsV1` چند نوع تعریف جدولی کنار هم آمده‌اند. primary key می‌تواند چندستونی باشد؛ unique، index، full-text و foreign key نیز از طریق builderهای جداگانه به AST تعریف جدول اضافه می‌شوند:

~~~kotlin
createTable {
    table { tableName("orders") }
    addColumn { name("tenant_id"); dataType(IntType()); notNull() }
    addColumn { name("order_id"); dataType(IntType()); notNull() }
    addColumn { name("user_id"); dataType(IntType()); notNull() }
    addColumn { name("email"); dataType(VarcharType(255)); notNull() }
    addColumn { name("description"); dataType(TextType()); notNull() }

    primaryKey { columns("tenant_id", "order_id") }
    unique {
        name("uq_orders_email")
        columns("email")
    }
    index {
        name("idx_orders_tenant_user")
        columns("tenant_id", "user_id")
        using(IndexMethod.BTREE)
    }
    fullTextIndex {
        name("ft_orders_description")
        columns("description")
    }
    foreignKey {
        name("fk_orders_user")
        columns("user_id")
        referencesTable("users")
        referencesColumns("id")
        onDelete(ForeignKeyAction.CASCADE)
        onUpdate(ForeignKeyAction.RESTRICT)
    }
}
~~~

AST constraintها در `IMigrationCreateTableDefinitionAst` و تعریف‌های primary key، unique، index، full-text و foreign key نگه‌داری می‌شوند. `IndexMethod` در AST دو مقدار `BTREE` و `HASH` دارد. `ForeignKeyAction` نیز `CASCADE`، `RESTRICT`، `NO_ACTION` و `SET_NULL` را تعریف می‌کند. اعتبار نهایی این گزینه‌ها به dialect و موتور پایگاه داده وابسته است.

### ستون‌های زمانی کمکی و نوع‌های زمانی

`timestamps()` دو ستون nullable به نام‌های `created_at` و `updated_at` با نوع `DateTimeType` می‌افزاید. `softDeletes()` یک ستون nullable به نام `deleted_at` می‌افزاید؛ این shorthand خودش رفتار حذف نرم را اجرا نمی‌کند و فقط ستون را در CREATE TABLE تعریف می‌کند.

~~~kotlin
createTable {
    table { tableName("posts") }
    addColumn {
        name("id")
        dataType(IntType())
        notNull()
        autoIncrement()
        primaryKey()
    }
    timestamps()
    softDeletes()
}
~~~

مثال `A1ExampleTemporalDataTypesV1` انواع زمانی را صریح تعریف می‌کند:

~~~kotlin
createTable {
    table { tableName("temporal_events") }
    addColumn { name("event_date"); dataType(DateType()); notNull() }
    addColumn { name("elapsed_time"); dataType(TimeType(precision = 3)) }
    addColumn { name("local_event_time"); dataType(DateTimeType()); notNull() }
    addColumn { name("recorded_at"); dataType(TimestampType(precision = 6)); notNull() }
}
~~~

### تغییر ساختار جدول موجود

این DSL عملیات جداگانه‌ای برای افزودن، تغییر، تغییرنام و حذف ستون دارد. مثال‌های زیر شکل استفادهٔ واقعی آن‌ها را نشان می‌دهند:

~~~kotlin
// A1ExampleAddColumnV1
addColumn {
    tableName("users")
    name("email")
    dataType(VarcharType(255))
    notNull()
}

// A1ExampleModifyColumnV1
modifyColumn {
    tableName("users")
    name("name")
    dataType(VarcharType(255))
    notNull()
    default("Unknown")
    autoIncrement(false)
}

// A1ExampleRenameColumnV1
renameColumn {
    tableName("users")
    name("display_name")
    to("full_name")
}

// A1ExampleDropColumnV1
dropColumn {
    tableName("users")
    name("display_name")
    ifExists()
}
~~~

در AST مربوط به `modifyColumn`، nullability و default به‌صورت حالت‌های مشخص ثبت می‌شوند: `NULL` یا `NOT_NULL` و یکی از بدون DEFAULT، `DEFAULT NULL` یا مقدار پیش‌فرض. این تفکیک اجازه می‌دهد نبودن clause با default صریح null اشتباه نشود. `ifExists()` در حذف ستون اختیاری است. چون renderer MySQL برای این حالت `ALTER TABLE ... DROP COLUMN` معمولی می‌سازد، `MigrationExecutor` پیش از DDL با `INFORMATION_SCHEMA.COLUMNS` وجود ستون را در database انتخاب‌شده بررسی می‌کند؛ اگر ستون موجود نباشد عملیات را موفق تمام می‌کند و اگر موجود باشد DDL را اجرا می‌کند.

تغییرنام جدول با `renameTable { fromTableName("users"); toTableName("customers") }` ساخته می‌شود. حذف جدول با `dropTable { tableName("users"); ifExists() }` انجام می‌شود. همانند حذف ستون، `ifExists()` برای حذف جدول اختیاری است. هر دو عملیات فقط نام‌های جدول را در AST ثبت می‌کنند و وجود وابستگی‌های خارجی یا اثر DDL روی داده را خودکار حل نمی‌کنند.

### Indexها و Foreign Keyهای مستقل

علاوه بر constraintهای inline در `createTable`، AST عملیات مستقل برای ساخت و حذف index و foreign key هم دارد:

~~~kotlin
createIndex {
    tableName("users")
    name("idx_users_email")
    column("email")
}

createUniqueIndex {
    tableName("users")
    name("uq_users_email")
    column("email")
}

createMultiColumnIndex {
    tableName("users")
    name("idx_users_name_email")
    columns("last_name", "first_name", "email")
}

createFullTextIndex {
    tableName("posts")
    name("ft_posts_title_body")
    columns("title", "body")
}

createSpatialIndex {
    tableName("places")
    name("spx_places_geom")
    column("geom")
}
~~~

`createSpatialIndex` فقط index فضایی را تعریف می‌کند؛ ستون نمونهٔ `places.geom` باید از قبل وجود داشته و برای index فضایی در دیتابیس هدف مناسب باشد. API و مثال، نوع فضایی مستقلی برای ساخت آن ستون در فهرست typeهای migration تعریف نمی‌کنند. برای حذف index از `dropIndex { tableName("users"); name("idx_users_email") }` استفاده می‌شود.

Foreign key مستقل، نام constraint و جفت ستون‌های جدول و مرجع را در AST ثبت می‌کند:

~~~kotlin
createForeignKey {
    tableName("orders")
    name("fk_orders_tenant_user")
    columns("tenant_id", "user_id")
    referencesTable("users")
    referencesColumns("tenant_id", "id")
    onDelete(ForeignKeyAction.CASCADE)
    onUpdate(ForeignKeyAction.RESTRICT)
}

dropForeignKey {
    tableName("orders")
    name("fk_orders_tenant_user")
}
~~~

در عملیات مستقل، تعداد و ترتیب ستون‌های محلی و مرجع باید با هم متناظر باشد. مثال‌های `A1ExampleCreateForeignKeyV1` و `A1ExampleDropForeignKeyV1` این دو جهت را جدا نشان می‌دهند. ایجاد و حذف foreign key در مثال‌ها opt-in است و با فراخوانی صریح `execute(...)` انجام می‌شود.

### Migration چندعملیاتی و ثبت نسخه

برای گروه‌بندی تغییرات، کلاس `Migration` متدهای `up()` و `down()` را برمی‌گرداند. مثال `CreateCustomersMigrationV1` در `up` جدول و index را می‌سازد و در `down` ابتدا index و سپس جدول را حذف می‌کند:

~~~kotlin
class CreateCustomersMigrationV1 : Migration {
    override fun up() = migration {
        createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("email")
                dataType(VarcharType(255))
                notNull()
            }
        }
        createIndex {
            tableName("customers")
            name("idx_customers_email")
            column("email")
        }
    }

    override fun down() = migration {
        dropIndex { tableName("customers"); name("idx_customers_email") }
        dropTable { tableName("customers") }
    }
}
~~~

ثبت و گروه‌بندی migrationها با `@MigrationId` و `migrationConfig` انجام می‌شود؛ نمونهٔ کامل و روش مشاهدهٔ فهرست در بخش [Register، گروه‌بندی و فهرست migrationها](registration.md) آمده است. Builder هنگام ثبت، وجود `@MigrationId` و معتبر بودن مقدار آن را بررسی می‌کند. Migration گروهی ترتیب ثبت‌شده را نگه می‌دارد؛ عملیات `down` را با توجه به وابستگی‌ها در ترتیب معکوس طراحی کنید.
## انجام عملیات Migration

پس از تعریف و ثبت migrationهای اجرایی، تنظیم اتصال و اجرای MigrationMigrator را از [راهنمای انجام عملیات Migration](execution.md) دنبال کنید.
