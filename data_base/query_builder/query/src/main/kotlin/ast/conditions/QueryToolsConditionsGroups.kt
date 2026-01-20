package gog.my_project.data_base.query_builder.query.ast.conditions

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditions
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsIsConditions
import gog.my_project.data_base.query_builder.query.tools.enums.SqlLogical


open class QueryToolsConditionsGroups(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsConditionsGroups
{

    private var isAddLogical:     Boolean = false;
    private var conditionLogical: SqlLogical? = SqlLogical.And;
    private var conditions:       MutableList<IQueryToolsIsConditions> = mutableListOf()





    /* ==============================================================
    template
    ============================================================== */
    override fun getGroupLogical(): SqlLogical? {
        return conditionLogical;
    }

    override fun getGroupConditions(): MutableList<IQueryToolsIsConditions> {
        return conditions;
    }

    override fun isAddLogical(): Boolean {
        return this.isAddLogical;
    }






    /* ==============================================================
    Builder
    ============================================================== */
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getConditionGroupSql(this);
    }*/






    /* ==============================================================
    structure
    ============================================================== */
    override fun setIsAddLogical(isAddLogical: Boolean){
        this.isAddLogical = isAddLogical;
    }




    override fun logical(logical: SqlLogical): IQueryToolsConditionsGroups {
        this.conditionLogical = logical;
        return this;
    }

    override fun logicalAnd(): IQueryToolsConditionsGroups {
        return this.logical(SqlLogical.And)
    }

    override fun logicalOr(): IQueryToolsConditionsGroups {
        return this.logical(SqlLogical.Or)
    }

    override fun logicalOn(): IQueryToolsConditionsGroups {
        return this.logical(SqlLogical.On)
    }




    override fun addGroup(
        blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups
    ): IQueryToolsConditionsGroups {
        val builder =
            QueryToolsConditionsGroups(params);
        val conditionSchema = builder.blockGroup();
        conditions.add(conditionSchema);
        return this;
    }



    override fun addCondition(
        blockCondition: IQueryToolsConditions.() -> IQueryToolsConditions
    ): IQueryToolsConditionsGroups {
        val builder =
            QueryToolsConditions(params);
        val conditionSchema = builder.blockCondition();
        conditions.add(conditionSchema);
        return this;
    }



}