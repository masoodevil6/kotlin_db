package gog.my_project.data_base.query.renderer.nodes.joins_item

import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.dialect.data_class.conditions_group.QueryConditionsGroupsData
import gog.my_project.data_base.query.dialect.data_class.joins_item.QueryJoinsItemData
import gog.my_project.data_base.query.dialect.data_class.table.QueryTableData
import gog.my_project.data_base.query.dialect.nodes.joins_item.IQueryJoinsItemCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryJoinsItemCapability : IQueryJoinsItemCapability {

    override fun render(
        ast: IQueryJoinsItemAst,
        ctx: IRenderContext ,
        dataClass: QueryJoinsItemData?
    ): String? {

        var joinType: String? = ast.joinType;
        var joinTable: IQueryTableAst = ast.joinTable;
        var joinCondition: IQueryConditionsGroupsAst = ast.joinConditions;

        var joinTableStr =      ctx.registry.render(joinTable     , ctx.dialect  , QueryTableData( _withPrefix = false));
        var joinConditionsStr = ctx.registry.render(joinCondition , ctx.dialect  , QueryConditionsGroupsData( _hasLogical = false));

        return " $joinType $joinTableStr $joinConditionsStr ";

    }

}