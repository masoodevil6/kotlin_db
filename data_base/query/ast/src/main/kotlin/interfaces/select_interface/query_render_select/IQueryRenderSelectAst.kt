package gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst

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