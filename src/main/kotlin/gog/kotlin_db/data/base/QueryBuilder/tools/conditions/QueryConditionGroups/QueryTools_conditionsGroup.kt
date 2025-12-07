package gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.IQueryTools_conditions
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditions.QueryTools_conditions
import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

class QueryTools_conditionsGroup(
    val conditionLogical: String,
):
    IQueryTools_conditionsGroupMethods ,
    IQueryTools_conditions,
    IQueryTools
{

    public var isAddLogical: Boolean = false;

    protected var conditions: MutableList<IQueryTools_conditions> = mutableListOf()










    override fun group(
        conditionLogical: String,
        block: (QueryTools_conditionsGroup) -> IQueryTools_conditions
    ): QueryTools_conditionsGroup {
        val conditionSchema = block(QueryTools_conditionsGroup(conditionLogical));
        conditions.add(conditionSchema);
        return this;
    }










    override fun whereAnd(
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, conditionOperation, sideRight);
    }

    override fun whereAnd(
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, conditionOperation, block);
    }

    override fun whereOn(
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_ON, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOn(
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_ON, sideLeft, conditionOperation, block);
    }

    override fun whereOr(
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_OR, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOr(
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_OR, sideLeft, conditionOperation, block);
    }


    override fun whereIn(
        sideLeft: String,
        listValues: List<String>
    ): QueryTools_conditionsGroup {
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
    ): QueryTools_conditionsGroup {
        return whereCondition(QueryBuilder._LOGICAL_AND, sideLeft, QueryBuilder._OPERATION_IN, block);
    }



    override fun whereLike(
        sideLeft: String,
        search: String ,
        conditionLogical: String
    ): QueryTools_conditionsGroup {
        group(conditionLogical){
            schema->
            schema
                .whereCondition(QueryBuilder._LOGICAL_AND , sideLeft , QueryBuilder._OPERATION_LIKE , "'$search'")
                .whereCondition(QueryBuilder._LOGICAL_AND , sideLeft , QueryBuilder._OPERATION_LIKE , "'%$search'")
                .whereCondition(QueryBuilder._LOGICAL_AND , sideLeft , QueryBuilder._OPERATION_LIKE , "'$search%'")
                .whereCondition(QueryBuilder._LOGICAL_AND , sideLeft , QueryBuilder._OPERATION_LIKE , "'%$search%'")
        }
        return this;
    }




    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String,
        conditionOperation: String,
        sideRight: String
    ): QueryTools_conditionsGroup {
        val condition = QueryTools_conditions(conditionLogical, sideLeft, conditionOperation, sideRight);
        conditions.add(condition);
        return this;
    }

    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_conditionsGroup {
        val sideRight = block(QueryBuilder());
        val condition = QueryTools_conditions(conditionLogical, sideLeft, conditionOperation, sideRight.toSql().toString());
        conditions.add(condition);
        return this;
    }








    override fun toWhereSql(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }





    companion object {

        /*----------------
          Templates
        ----------------*/
        const val _TAG_TEMP_CONDITION=             "{{_TAG_TEMP_CONDITION}}"
        const val _TAG_TEMP_CONDITION_LOGICAL=     "{{_TAG_TEMP_CONDITION_LOGICAL}}"
        const val _TAG_TEMP_CONDITION_GROUP=        "{{_TAG_TEMP_CONDITION_LEFT}}"

    }



    override fun getBaseTempSql(): String? {
        return " $_TAG_TEMP_CONDITION_LOGICAL ($_TAG_TEMP_CONDITION_GROUP)"
    }

    override fun toSql(): String? {
        if (conditions != null && conditions.size>0) {

            var queryTemp = getBaseTempSql();
            queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_LOGICAL, if(isAddLogical){conditionLogical} else{""});

            var conditionStr = "";
            for ((index, condition) in conditions.withIndex()){
                conditionStr += condition.toWhereSql(index > 0).toString()
            }
            queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_GROUP, conditionStr);

            return queryTemp;
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }




}