package gog.my_project.data_base.query.definition.interfaces.column

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryDefinitionColumnsBase {

    fun <T: IModel, R> column(table: KClass<T>, column: KProperty1<T, R>): IQueryDefinitionColumnsBase;

    fun <T: ICte, R> selector(cte: KClass<T>, select: KProperty1<T, R>): IQueryDefinitionColumnsBase;


}