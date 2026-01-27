package gog.my_project.data_base.query_builder.query.interfaces.columns

import gog.my_project.data_base.annotations.models.QBColumn
import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.models.main.BaseModel
import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


interface IQueryToolsColumnsBase : IQueryTools {

//    fun getColumnPrefix(): String?;
//    fun getColumnName(): String?;
//    fun getColumnQuery(): IQueryBuilder?;
//
//    fun columnPrefix(columnPrefix: String): IQueryToolsColumnsBase;
//    fun columnName(columnName: String): IQueryToolsColumnsBase;
//    fun columnQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsColumnsBase;



    fun getTable(): QBTable?;
    fun getColumn(): QBColumn?;

    fun <T: BaseModel , R> column(table: KClass<T>, column: KProperty1<T, R>): IQueryToolsColumnsBase;

}

