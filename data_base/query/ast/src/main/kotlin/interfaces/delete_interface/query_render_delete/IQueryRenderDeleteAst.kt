package gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst

interface IQueryRenderDeleteAst : IQueryAst{

    var targets:      MutableList<String>;
    var table :       IQueryTableAst?;
    var where :       IQueryWhereAst?;

}