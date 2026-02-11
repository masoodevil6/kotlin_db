package gog.my_project.data_base.query.builder.interfaces.joins_item

import gog.my_project.data_base.query.api.tools.enums.SqlTypeJoin
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import gog.my_project.data_base.query.builder.interfaces.conditions_group.IQueryConditionsGroupsBuilder
import gog.my_project.data_base.query.builder.interfaces.table.IQueryTableBuilder

interface IQueryJoinsItemBuilder : IQuery {

    var ast: IQueryJoinsItemAst;

    fun typeJoin(type: SqlTypeJoin): IQueryJoinsItemBuilder;
    fun innerJoin(): IQueryJoinsItemBuilder;
    fun leftJoin(): IQueryJoinsItemBuilder;
    fun rightJoin(): IQueryJoinsItemBuilder;

    fun table(blockTable: IQueryTableBuilder.() -> Unit): IQueryJoinsItemBuilder;

    fun condition(blockCondition: IQueryConditionsGroupsBuilder.() -> Unit): IQueryJoinsItemBuilder;

}