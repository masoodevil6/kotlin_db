package gog.kotlin_db.data.base.QueryBuilder.Conditions.QueryConditionGroups

import gog.kotlin_db.data.base.QueryBuilder.Conditions.IQueryBuilder_conditions
import gog.kotlin_db.data.base.QueryBuilder.Conditions.QueryConditions.QueryBuilder_conditions
import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

class QueryBuilder_conditionsGroup(
    val conditionLogical: String,
):
    IQueryBuilder_conditionsGroupMethods ,
    IQueryBuilder_conditions,
    IQueryBuilder
{

    public var isAddLogical: Boolean = false;

    protected var conditions: MutableList<IQueryBuilder_conditions> = mutableListOf()










    override fun group(
        conditionLogical: String,
        block: (QueryBuilder_conditionsGroup) -> IQueryBuilder_conditions
    ): QueryBuilder_conditionsGroup {
        val conditionSchema = block(QueryBuilder_conditionsGroup(conditionLogical));
        conditions.add(conditionSchema);
        return this;
    }










    override fun whereAnd(
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, conditionOperation, sideRight);
    }

    override fun whereAnd(
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, conditionOperation, block);
    }

    override fun whereOn(
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_ON, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOn(
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_ON, sideLeft, conditionOperation, block);
    }

    override fun whereOr(
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_OR, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOr(
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_OR, sideLeft, conditionOperation, block);
    }


    override fun whereIn(
        sideLeft: String,
        listValues: List<String>
    ): QueryBuilder_conditionsGroup {
        var sideRight = " (";
        for ((index, value) in listValues.withIndex()){
            sideRight +=  " $sideRight";
            if (index < listValues.size - 1){
                sideRight += ","
            }
        }
        sideRight += ") "
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, QueryBuilder._OPERATION_IN, sideRight);
    }

    override fun whereIn(
        sideLeft: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, QueryBuilder._OPERATION_IN, block);
    }

    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryBuilder_conditionsGroup {
        val condition = QueryBuilder_conditions(conditionLogical, sideLeft, conditionOperation, sideRight);
        conditions.add(condition);
        return this;
    }

    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder_conditionsGroup {
        val sideRight = block(QueryBuilder());
        val condition = QueryBuilder_conditions(conditionLogical, sideLeft, conditionOperation, sideRight.toSql().toString());
        conditions.add(condition);
        return this;
    }








    override fun toWhereSql(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }








    private val _conditionLogicalTag =     "{{CONDITION_LOGICAL_TAG}}"
    private val _conditionTag =            "{{CONDITION_TAG}}"

    override fun getBaseTempSql(): String? {
        return " $_conditionLogicalTag ($_conditionTag)"
    }

    override fun toSql(): String? {
        if (conditions != null && conditions.size>0) {

            var queryTemp = getBaseTempSql();
            queryTemp =  queryTemp?.replace(_conditionLogicalTag, if(isAddLogical){conditionLogical} else{""});

            var conditionStr = "";
            for ((index, condition) in conditions.withIndex()){
                conditionStr += condition.toWhereSql(index > 0).toString()
            }
            queryTemp =  queryTemp?.replace(_conditionTag, conditionStr);

            return queryTemp;
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }




}