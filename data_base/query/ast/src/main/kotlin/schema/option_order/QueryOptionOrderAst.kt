package gog.my_project.data_base.query.ast.schema.option_order

import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst

class QueryOptionOrderAst : IQueryOptionOrderAst {

    override var orderByList: MutableList<IQueryColumnsBaseAst> = mutableListOf();
    override var orderType :  String?                           = null;

}