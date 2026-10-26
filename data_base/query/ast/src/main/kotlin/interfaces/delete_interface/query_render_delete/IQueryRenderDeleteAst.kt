package gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete
import gog.my_project.data_base.query.ast.metadata.delete.query_render_delete.QueryRenderDeleteAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst

interface IQueryRenderDeleteAst : IQueryAst<QueryRenderDeleteAstMetadata>{

    var table :       IQueryTableAst?;

    var where :       IQueryWhereAst?;

}