package gog.my_project.data_base.query.definition.interfaces.with

import gog.my_project.data_base.core.managers.ctes.ICte

interface IQueryDefinitionWithsItem {

    fun with(with : ICte): IQueryDefinitionWithsItem;


}