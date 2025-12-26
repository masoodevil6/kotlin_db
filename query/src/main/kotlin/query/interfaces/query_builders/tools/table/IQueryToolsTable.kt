package gog.my_project.query.interfaces.query_builders.tools.table

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsTable : IQueryTools {


    fun tableSetup(blockTable: (IQueryToolsTable) -> IQueryToolsTable): IQueryToolsTable;

    fun table(tableName: String?): IQueryToolsTable;
    fun table(tableName: String? , aliasName: String?): IQueryToolsTable;
    fun table(aliasName: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsTable;


}