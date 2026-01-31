package gog.my_project.data_base.query.builder.interfaces.options

import gog.my_project.data_base.query.builder.interfaces.IQueryTools
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.definition.tools.enums.SqlOrderType

interface IQueryToolsOptionOrder: IQueryTools {

    fun getOrderType(): SqlOrderType;
    fun getListColumns(): MutableList<IQueryToolsColumnsBase>;

}