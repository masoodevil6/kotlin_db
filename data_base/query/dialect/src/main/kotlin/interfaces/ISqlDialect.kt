package gog.my_project.data_base.query.dialect.interfaces

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry

interface ISqlDialect {

    val _prefixWiths:   String;
    val _prefixSelect:  String;
    val _prefixTable:   String;
    val _prefixWhere:   String;
    val _prefixGroup:   String;
    val _prefixLimit:   String;
    val _prefixOrder:   String;
    val _prefixOffset:  String;
    val _prefixInsert:  String;
    val _prefixUpdate:  String;
    val _prefixDelete:  String;

    val _registry: IRendererRegistry;

    fun registerRenders();

    fun <Ast: IQueryAst> render(ast: Ast) : String?;

}