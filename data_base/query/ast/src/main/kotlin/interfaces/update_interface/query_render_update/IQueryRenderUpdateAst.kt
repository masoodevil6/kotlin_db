package gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst


interface IQueryRenderUpdateAst : IQueryAst {

    var table :       IQueryTableAst?;
    var columns:      MutableList<IQueryColumnUpdateAst>;
    var where :       IQueryWhereAst?;

}