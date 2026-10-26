package gog.my_project.data_base.query.ast.interfaces.select_interface.option_order
import gog.my_project.data_base.query.ast.metadata.select.option_order.QueryOptionOrderAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

interface IQueryOptionOrderAst : IQueryAst<QueryOptionOrderAstMetadata> {

    var orderByList: MutableList<IQueryColumnsBaseAst>;

}