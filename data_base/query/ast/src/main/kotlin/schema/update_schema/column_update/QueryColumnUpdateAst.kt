package gog.my_project.data_base.query.ast.schema.update_schema.column_update

import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst

class QueryColumnUpdateAst : IQueryColumnUpdateAst {

    override var columnAlias: String? = null
    override var columnName:  String? = null
    override var columnTag:   String? = null
}