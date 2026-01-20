package gog.my_project.data_base.query_builder.dialect.dialects

import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumns
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditions
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsItem
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query_builder.query.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query_builder.query.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query_builder.query.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithItem
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithsCollection


interface ISqlDialect {


    fun getBasicSql(baseQuery: IQueryBuilder?): String?




    fun getWithSql(with: IQueryToolsWithsCollection?, withPrefix: Boolean = true) : String;

    fun getWithItemSql(with: IQueryToolsWithItem?) : String?;





    fun getSelectSql(select: IQueryToolsSelect?, withPrefix: Boolean=true) : String;

    fun getColumnSql(column: IQueryToolsColumns?) : String?;

    fun getColumnBaseSql(column: IQueryToolsColumnsBase?) : String?;





    fun getTableSql(table: IQueryToolsTable?, withPrefix: Boolean=true) : String;




    fun getJoinSql(join:  IQueryToolsJoinsConnect?) : String

    fun getJoinItemSql(join: IQueryToolsJoinsItem?) : String?





    fun getWhereSql(condition: IQueryToolsWhere?) : String





    fun getOptionGroupSql(group: IQueryToolsOptionGroup?) : String

    fun getOptionOderSql(order: IQueryToolsOptionOrder?) : String

    fun getOptionLimitSql(limit: IQueryToolsOptionLimit?) : String

    fun getOptionOffsetSql(offset: IQueryToolsOptionOffset?) : String




    fun getConditionGroupSql(group: IQueryToolsConditionsGroups?, hasLogical: Boolean = false, forceHasLogical: Boolean = false) : String?

    fun getConditionSql(condition: IQueryToolsConditions?, hasLogical: Boolean = false) : String?

}