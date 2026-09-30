package gog.my_project.data_base.migration.api.interfaces.create_multi_column_index

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst

interface IMigrationCreateMultiColumnIndexApi : IMigrationApi<IMigrationCreateMultiColumnIndexAst> {
    fun tableName(table: String): IMigrationCreateMultiColumnIndexApi
    fun name(name: String): IMigrationCreateMultiColumnIndexApi
    fun columns(vararg columns: String): IMigrationCreateMultiColumnIndexApi
}
