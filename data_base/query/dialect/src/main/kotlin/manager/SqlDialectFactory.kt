package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.core.query.dialect.DialectQuery
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
import gog.my_project.data_base.query.dialect.dialects.MySqlDialect
import gog.my_project.data_base.query.dialect.manager.ISqlDialect
import gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in.IQueryToolsConditionsCollection

class SqlDialectFactory(
    val _dialectSelected: DialectQuery
) {



    fun create(): ISqlDialect =
        when (_dialectSelected) {
            DialectQuery.MY_SQL -> MySqlDialect()
            // DialectQuery.SQL_LITE ->  SqlLiteDialect()
        }



    fun <T> translate(input: T): String?{
        var query : String? = null;
        val sqlDialect = this.create();
        when (input) {
            is IQueryBuilder ->                            query = sqlDialect.getBasicSql(input)
            is IQueryToolsWithsCollection ->               query = sqlDialect.getWithSql(input)
            is IQueryToolsWithItem ->                      query = sqlDialect.getWithItemSql(input)
            is IQueryToolsSelect ->                        query = sqlDialect.getSelectSql(input)
            is IQueryToolsColumns ->                       query = sqlDialect.getColumnSql(input)
            is IQueryToolsColumnsBase ->                   query = sqlDialect.getColumnBaseSql(input)
            is IQueryToolsTable ->                         query = sqlDialect.getTableSql(input)
            is IQueryToolsJoinsConnect ->                  query = sqlDialect.getJoinSql(input)
            is IQueryToolsJoinsItem ->                     query = sqlDialect.getJoinItemSql(input)
            is IQueryToolsWhere ->                         query = sqlDialect.getWhereSql(input)
            is IQueryToolsOptionGroup ->                   query = sqlDialect.getOptionGroupSql(input)
            is IQueryToolsOptionOrder ->                   query = sqlDialect.getOptionOderSql(input)
            is IQueryToolsOptionLimit ->                   query = sqlDialect.getOptionLimitSql(input)
            is IQueryToolsOptionOffset ->                  query = sqlDialect.getOptionOffsetSql(input)
            is IQueryToolsConditionsGroups ->              query = sqlDialect.getConditionGroupSql(input)
            is IQueryToolsConditions ->                    query = sqlDialect.getConditionSql(input)
            is IQueryToolsConditionsCollection->           query = sqlDialect.getConditionCollectionSql(input)
        }

        return query;
    }


}