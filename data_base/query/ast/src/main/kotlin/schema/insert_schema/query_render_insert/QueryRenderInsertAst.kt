package gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert

import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst


class QueryRenderInsertAst : IQueryRenderInsertAst {

    override var table:   IQueryTableAst? = null;

    override var columns: MutableList<IQueryColumnInsertAst> = mutableListOf();

}