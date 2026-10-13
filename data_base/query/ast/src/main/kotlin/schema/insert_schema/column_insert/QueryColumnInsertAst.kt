package gog.my_project.data_base.query.ast.schema.insert_schema.column

import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.core.query.reader.SqlParameter

class QueryColumnInsertAst() : IQueryColumnInsertAst {

    override var columnName:  String? = null;
    override var columnTag:   String? = null;
    override var parameter:   SqlParameter<*>? = null;

}
