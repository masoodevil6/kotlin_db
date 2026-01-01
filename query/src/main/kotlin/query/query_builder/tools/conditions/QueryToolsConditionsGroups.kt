package gog.my_project.query.query_builder.tools.conditions

import gog.my_project.enums.SqlLogical
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsIsConditions
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder


class QueryToolsConditionsGroups(
    private val sqlDialect: ISqlDialect
):
    IQueryToolsConditionsGroups
{

    private var isAddLogical: Boolean = false;
    private var conditionLogical: String? = null;
    private var conditions: MutableList<IQueryToolsIsConditions> = mutableListOf()



    override fun getGroupLogical(): String? {
        return conditionLogical;
    }

    override fun getGroupConditions(): MutableList<IQueryToolsIsConditions> {
        return conditions;
    }






    override fun setIsAddLogical(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }

    override fun isAddLogical(): Boolean {
        return this.isAddLogical;
    }




    override fun logical(logical: SqlLogical): IQueryToolsConditionsGroups {
        this.conditionLogical = logical.value;
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
        val builder = QueryToolsConditionsGroups(sqlDialect);
        val conditionSchema = builder.blockGroup();
        conditions.add(conditionSchema);
        return this;
    }



    override fun addCondition(
        blockCondition: IQueryToolsConditions.() -> IQueryToolsConditions
    ): IQueryToolsConditionsGroups {
        val builder = QueryToolsConditions(sqlDialect);
        val conditionSchema = builder.blockCondition();
        conditions.add(conditionSchema);
        return this;
    }






    override fun toSql(): String? {
        return sqlDialect.getConditionGroupSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }







    /*  override fun whereAnd(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, sideRight);
      }

      override fun whereAnd(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, block);
      }

      override fun whereOn(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, sideRight);
      }

      override fun  whereOn(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, block);
      }

      override fun whereOr(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, sideRight);
      }

      override fun whereOr(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, block);
      }


      override fun whereIn(sideLeft: String? , listValues: List<String>): IQueryToolsConditionsGroups {
          if (listValues.size > 0){
              var sideRight = " (";
              for ((index, value) in listValues.withIndex()){
                  sideRight +=  " $sideRight";
                  if (index < listValues.size - 1){
                      sideRight += ","
                  }
              }
              sideRight += ") "
              return whereCondition(_LOGICAL_AND, sideLeft, _OPERATION_IN, sideRight);
          }
          return this;
      }

      override fun whereIn(sideLeft: String? , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
          return whereCondition(_LOGICAL_AND, sideLeft, _OPERATION_IN, block);
      }



      override fun whereLike(sideLeft: String? , search: String? , conditionLogical: String) : IQueryToolsConditionsGroups {
          if (search != null && search.isNotEmpty()) {
              group(conditionLogical){
                      schema->
                  schema
                      .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'$search'")
                      .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'%$search'")
                      .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'$search%'")
                      .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'%$search%'")
              }
          }
          return this;
      }



      override fun whereNull(conditionLogical: String ,  sideLeft: String): IQueryToolsConditionsGroups {
          return whereCondition(conditionLogical, sideLeft, "", " is null ");
      }

      override fun whereNull(conditionLogical: String, block: (IQueryBuilder) -> IQueryBuilder, conditionOperation: String): IQueryToolsConditionsGroups {
          val sideLeft = block(QueryBuilder(sqlDialect));
          return whereCondition(conditionLogical, "(${sideLeft.toSql().toString()})" , "", " is null ");
      }




      override fun whereCondition(conditionLogical: String , sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
          val condition = QueryToolsConditions(sqlDialect , conditionLogical, sideLeft, conditionOperation, sideRight);
          conditions.add(condition);
          return this;
      }

      override fun whereCondition(conditionLogical: String ,sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
          val sideRight = block(QueryBuilder(sqlDialect));
          val condition =
              QueryToolsConditions(sqlDialect , conditionLogical, sideLeft, conditionOperation, "(${sideRight?.toSql().toString()})");
          conditions.add(condition);
          return this;
      }*/






}