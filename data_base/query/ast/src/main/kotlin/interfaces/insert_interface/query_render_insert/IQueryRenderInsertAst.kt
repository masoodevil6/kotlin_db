package gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst


interface IQueryRenderInsertAst : IQueryAst{

    var table:   IQueryTableAst? ;
    var columns: MutableList<IQueryColumnInsertAst>;

}