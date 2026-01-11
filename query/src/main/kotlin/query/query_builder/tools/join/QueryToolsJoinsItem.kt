package gog.my_project.query.query_builder.tools.join

import gog.my_project.datas.SqlParameter
import gog.my_project.enums.SqlTypeJoin
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.conditions.QueryToolsConditionsGroups
import gog.my_project.query.query_builder.tools.table.QueryToolsTable


class QueryToolsJoinsItem(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsJoinsItem
{

    private var joinType:       SqlTypeJoin = SqlTypeJoin.InnerJoin;
    private var joinTable:      IQueryToolsTable = QueryToolsTable();
    private var joinConditions: IQueryToolsConditionsGroups = QueryToolsConditionsGroups()

    /* ==============================================================
    template
    ============================================================== */
    override fun getJoinType(): SqlTypeJoin {
        return joinType;
    }

    override fun getJoinTable(): IQueryToolsTable {
        return joinTable;
    }

    override fun getJoinConditions(): IQueryToolsConditionsGroups {
        return joinConditions;
    }







    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getJoinItemSql(this);
    }







    /* ==============================================================
    structure
    ============================================================== */

    override fun innerJoin(): IQueryToolsJoinsItem {
        this.joinType = SqlTypeJoin.InnerJoin;
        return this;
    }

    override fun leftJoin(): IQueryToolsJoinsItem {
        this.joinType = SqlTypeJoin.LeftJoin;
        return this;
    }

    override fun rightJoin(): IQueryToolsJoinsItem {
        this.joinType = SqlTypeJoin.RightJoin;
        return this;
    }






    override fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryToolsJoinsItem {
        val builder = QueryToolsTable(params);
        joinTable = builder.blockTable();
        return this;
    }






    override fun condition(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsJoinsItem {
        val builder = QueryToolsConditionsGroups(params);
        joinConditions = builder.blockCondition();
        return this;
    }






}