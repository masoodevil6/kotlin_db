package gog.my_project.data_base.migration.api.interfaces.create_full_text_index

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst

interface IMigrationCreateFullTextIndexApi : IMigrationApi<IMigrationCreateFullTextIndexAst> {
    fun tableName(table: String): IMigrationCreateFullTextIndexApi
    fun name(name: String): IMigrationCreateFullTextIndexApi
    fun columns(vararg columns: String): IMigrationCreateFullTextIndexApi
}
