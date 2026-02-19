package gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst


interface IQueryRenderInsertAst : IQueryAst{

    var table: String?;
    var columns: MutableList<IQueryColumnInsertAst>;

}