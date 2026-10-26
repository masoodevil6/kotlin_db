package gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete
import gog.my_project.data_base.query.ast.metadata.delete.query_render_delete.QueryRenderDeleteAstMetadata

import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst

class QueryRenderDeleteAst(
    override var metadata: QueryRenderDeleteAstMetadata = QueryRenderDeleteAstMetadata()
) : IQueryRenderDeleteAst {

    override var table: IQueryTableAst? = null

    override var where: IQueryWhereAst? = null

}
