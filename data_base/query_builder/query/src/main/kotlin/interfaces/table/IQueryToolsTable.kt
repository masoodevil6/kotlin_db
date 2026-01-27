package gog.my_project.data_base.query_builder.query.interfaces.table

import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.models.main.BaseModel
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import kotlin.reflect.KClass

interface IQueryToolsTable : IQueryTools {

//    fun getTableName(): String?;
//    fun getTableAlias(): String?;
//    fun getTableQuery(): IQueryBuilder?;
//
//    fun table(tableName: String?): IQueryToolsTable;
//    fun tableQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable;
//
//    fun alias(aliasName: String?): IQueryToolsTable;


    fun getTable(): QBTable?;

    fun <T: BaseModel> table(table: KClass<T>): IQueryToolsTable;

}