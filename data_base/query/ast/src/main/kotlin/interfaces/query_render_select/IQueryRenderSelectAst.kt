package gog.my_project.data_base.query.ast.interfaces.query_render_select

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst

interface IQueryRenderSelectAst : IQueryAst  {

    var withs :           IQueryWithsAst?;
    var select :          IQuerySelectAst?;
    var table :           IQueryTableAst?;
    var joins :           IQueryJoinsAst?;
    var where :           IQueryWhereAst?;
    var optionLimit :     IQueryOptionLimitAst?;
    var optionOffset :    IQueryOptionOffsetAst?;
    var optionGroup :     IQueryOptionGroupAst?;
    var optionOrder :     IQueryOptionOrderAst?;

}