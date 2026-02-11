package gog.my_project.data_base.query.ast.interfaces.option_order

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst

interface IQueryOptionOrderAst : IQueryAst {

    var orderByList: MutableList<IQueryColumnsBaseAst>;
    var orderType :  String?                          ;

}