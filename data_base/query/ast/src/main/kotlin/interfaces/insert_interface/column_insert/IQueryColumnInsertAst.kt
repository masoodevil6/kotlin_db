package gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryColumnInsertAst : IQueryAst {

    var columnName: String? ;

}