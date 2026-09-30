package gog.my_project.data_base.query.renderer.nodes.select_nodes.joins_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_group.QueryConditionsGroupsData
import gog.my_project.data_base.query.dialect.data_class.select_data.joins_item.QueryJoinsItemData
import gog.my_project.data_base.query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.joins_item.IQueryJoinsItemCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryJoinsItemCapability :
    IQueryJoinsItemCapability {

    override fun render(
        ast:         IQueryJoinsItemAst,
        ctx:         IRenderContext,
        dataClass:   QueryJoinsItemData?
    ): String? {

        var joinType: String? = ast.joinType;
        var joinTable: IQueryTableAst = ast.joinTable;
        var joinCondition: IQueryConditionsGroupsAst = ast.joinConditions;

        var joinTableStr =      ctx.registry.render(joinTable     , ctx.dialect  , QueryTableData(_withPrefix = false) );
        var joinConditionsStr = ctx.registry.render(joinCondition , ctx.dialect  , QueryConditionsGroupsData(_hasLogical = false) );

        return " $joinType $joinTableStr $joinConditionsStr ";

    }

}