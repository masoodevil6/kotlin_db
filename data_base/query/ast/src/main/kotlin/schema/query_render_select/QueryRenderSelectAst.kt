package gog.my_project.data_base.query.ast.schema.query_render_select

import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst

class QueryRenderSelectAst : IQueryRenderSelectAst {

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