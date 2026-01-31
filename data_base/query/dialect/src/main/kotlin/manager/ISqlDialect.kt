package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.query.builder.interfaces.IQueryBuilder
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumns
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditions
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsItem
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query.builder.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query.builder.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query.builder.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithItem
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithsCollection
import gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in.IQueryToolsConditionsCollection

interface ISqlDialect {


    fun getBasicSql(baseQuery: IQueryBuilder?): String?




    fun getWithSql(with: IQueryToolsWithsCollection?, withPrefix: Boolean = true) : String;

    fun getWithItemSql(with: IQueryToolsWithItem?) : String?;





    fun getSelectSql(select: IQueryToolsSelect?, withPrefix: Boolean=true) : String;

    fun getColumnSql(column: IQueryToolsColumns?) : String?;

    fun getColumnBaseSql(columnData: IQueryToolsColumnsBase?) : String?;





    fun getTableSql(tableData: IQueryToolsTable?, withPrefix: Boolean=true) : String;




    fun getJoinSql(join:  IQueryToolsJoinsConnect?) : String

    fun getJoinItemSql(join: IQueryToolsJoinsItem?) : String?





    fun getWhereSql(condition: IQueryToolsWhere?) : String





    fun getOptionGroupSql(group: IQueryToolsOptionGroup?) : String

    fun getOptionOderSql(order: IQueryToolsOptionOrder?) : String

    fun getOptionLimitSql(limit: IQueryToolsOptionLimit?) : String

    fun getOptionOffsetSql(offset: IQueryToolsOptionOffset?) : String




    fun getConditionGroupSql(group: IQueryToolsConditionsGroups?, hasLogical: Boolean = false, forceHasLogical: Boolean = false) : String?

    fun getConditionSql(condition: IQueryToolsConditions?, hasLogical: Boolean = false) : String?
    fun getConditionCollectionSql(params: IQueryToolsConditionsCollection?) : String?

}