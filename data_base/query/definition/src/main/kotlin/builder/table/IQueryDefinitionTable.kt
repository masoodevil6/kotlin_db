package gog.my_project.data_base.query.definition.interfaces.table

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import kotlin.reflect.KClass

interface IQueryDefinitionTable {

    fun <T: IModel> table(table: KClass<T>): IQueryDefinitionTable;
    fun <T: ICte> cte(cte: KClass<T>): IQueryDefinitionTable;

}