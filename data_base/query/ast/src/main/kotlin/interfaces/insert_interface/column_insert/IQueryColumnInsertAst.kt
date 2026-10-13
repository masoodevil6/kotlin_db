package gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.core.query.reader.SqlParameter

interface IQueryColumnInsertAst : IQueryAst {

    var columnName:   String? ;
    var columnTag:    String? ;
    var parameter:    SqlParameter<*>?;

}
