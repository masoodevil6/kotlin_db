package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.builder.ast.create_full_text_index.MigrationCreateFullTextIndexBuilder

fun createFullTextIndex(
    block: IMigrationCreateFullTextIndexApi.() -> Unit,
): IMigrationCreateFullTextIndexApi {
    val builder = MigrationCreateFullTextIndexBuilder()
    builder.block()
    return builder.build()
}
