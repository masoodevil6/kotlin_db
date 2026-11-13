package gog.my_project.data_base.migration.example.v1.migrations

import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

object DataBaseMigrationTags {

    enum class Versions(override val value: String) : MigrationVersionTag {
        VERSION_1("customers_v1"),
    }

    enum class Groups(override val value: String) : MigrationGroupTag {
       GROUP_1("customers");
    }

    enum class Items(override val value: String) : MigrationItemTag {
        CREATE_INDEX("create_customers_email_index_v1"),
        MIGRATION_1("create_customers_migration_v1"),
        MIGRATION_2("create_customers") ,
        MIGRATION_3("add_customer_email") ,
    }

}
