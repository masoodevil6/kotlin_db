package gog.my_project.data_base.query.ast.interfaces.select_interface.option_order

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst

interface IQueryOptionOrderAst : IQueryAst {

    var orderByList: MutableList<IQueryColumnsBaseAst>;
    var orderType :  String?                          ;

}