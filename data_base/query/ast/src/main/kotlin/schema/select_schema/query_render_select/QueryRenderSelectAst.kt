package gog.my_project.data_base.query.ast.schema.select_schema.query_render_select

import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst


class QueryRenderSelectAst :
    IQueryRenderSelectAst {

    override var withs :           IQueryWithsAst?        = null;
    override var select :          IQuerySelectAst?       = null;
    override var table :           IQueryTableAst?        = null;
    override var joins :           IQueryJoinsAst?        = null;
    override var where :           IQueryWhereAst?        = null;
    override var optionLimit :     IQueryOptionLimitAst?  = null;
    override var optionOffset :    IQueryOptionOffsetAst? = null;
    override var optionGroup :     IQueryOptionGroupAst?  = null;
    override var optionOrder :     IQueryOptionOrderAst?  = null;

}