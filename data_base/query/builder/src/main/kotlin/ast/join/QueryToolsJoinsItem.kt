package gog.my_project.data_base.query.builder.ast.join

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.conditions.QueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.ast.table.QueryToolsTable
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsItem
import gog.my_project.data_base.query.builder.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditionsGroups
import gog.my_project.data_base.query.definition.interfaces.join.IQueryDefinitionJoinsItem
import gog.my_project.data_base.query.definition.interfaces.table.IQueryDefinitionTable
import gog.my_project.data_base.query.definition.tools.enums.SqlTypeJoin

class QueryToolsJoinsItem(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsJoinsItem , IQueryDefinitionJoinsItem
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
    structure
    ============================================================== */

    override fun innerJoin(): IQueryDefinitionJoinsItem {
        this.joinType = SqlTypeJoin.InnerJoin;
        return this;
    }

    override fun leftJoin(): IQueryDefinitionJoinsItem {
        this.joinType = SqlTypeJoin.LeftJoin;
        return this;
    }

    override fun rightJoin(): IQueryDefinitionJoinsItem {
        this.joinType = SqlTypeJoin.RightJoin;
        return this;
    }






    override fun table(blockTable: IQueryDefinitionTable.() -> IQueryDefinitionTable): IQueryDefinitionJoinsItem {
        val builder = QueryToolsTable(params);
        joinTable = builder.blockTable() as IQueryToolsTable;
        return this;
    }






    override fun condition(blockCondition: IQueryDefinitionConditionsGroups.() -> IQueryDefinitionConditionsGroups): IQueryDefinitionJoinsItem {
        val builder = QueryToolsConditionsGroups(params);
        joinConditions = builder.blockCondition() as IQueryToolsConditionsGroups;
        return this;
    }






}