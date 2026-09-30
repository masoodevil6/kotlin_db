package gog.my_project.data_base.query.ast.schema.update_schema.query_render_update

import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst

class QueryRenderUpdateAst : IQueryRenderUpdateAst {

    override var table:      IQueryTableAst? = null;
    override var columns:    MutableList<IQueryColumnUpdateAst> = mutableListOf();
    override var where:      IQueryWhereAst? = null

}