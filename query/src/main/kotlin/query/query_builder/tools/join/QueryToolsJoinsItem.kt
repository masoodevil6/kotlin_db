package gog.my_project.query.query_builder.tools.join

import gog.my_project.enums.SqlTypeJoin
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.conditions.QueryToolsConditionsGroups
import gog.my_project.query.query_builder.tools.table.QueryToolsTable


class QueryToolsJoinsItem(
    private val sqlDialect: ISqlDialect ,
) :
    IQueryToolsJoinsItem
{

    private var joinType: String = SqlTypeJoin.InnerJoin.value;
    private var joinTable: IQueryToolsTable = QueryToolsTable(sqlDialect);
    private var joinConditions: IQueryToolsConditionsGroups = QueryToolsConditionsGroups(sqlDialect)


    override fun getJoinType(): String {
        return joinType;
    }

    override fun getJoinTable(): IQueryToolsTable {
        return joinTable;
    }

    override fun getJoinConditions(): IQueryToolsConditionsGroups {
        return joinConditions;
    }







    override fun typeJoin(joinType: SqlTypeJoin): IQueryToolsJoinsItem {
        this.joinType = joinType.value;
        return this;
    }

    override fun innerJoin(): IQueryToolsJoinsItem {
        this.joinType = SqlTypeJoin.InnerJoin.value;
        return this;
    }

    override fun leftJoin(): IQueryToolsJoinsItem {
        this.joinType = SqlTypeJoin.LeftJoin.value;
        return this;
    }

    override fun rightJoin(): IQueryToolsJoinsItem {
        this.joinType = SqlTypeJoin.RightJoin.value;
        return this;
    }





    override fun tableJoin(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryToolsJoinsItem {
        val builder = QueryToolsTable(sqlDialect);
        joinTable = builder.blockTable();
        return this;
    }



    override fun conditionJoin(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsJoinsItem {
        val builder = QueryToolsConditionsGroups(sqlDialect);
        joinConditions = builder.blockCondition();
        return this;
    }







    override fun toSql(): String? {
        return sqlDialect.getJoinItemSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }



}