package gog.my_project.data_base.query.builder.interfaces.join

import gog.my_project.data_base.query.builder.interfaces.IQueryTools
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query.definition.tools.enums.SqlTypeJoin

interface IQueryToolsJoinsItem : IQueryTools {

    fun getJoinType(): SqlTypeJoin;
    fun getJoinTable(): IQueryToolsTable;
    fun getJoinConditions(): IQueryToolsConditionsGroups;

}