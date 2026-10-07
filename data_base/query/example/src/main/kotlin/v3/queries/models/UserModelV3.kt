package gog.my_project.data_base.query.example.v3.queries.models

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase

@QBTable(name = "user_users", alias = "uu")
class UserModelV3 : IModelBase {
    @QBColumn(name = "id", alias = "user_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "name", alias = "user_name")
    val name: String? = null

    @QBColumn(name = "family", alias = "user_family")
    val family: String? = null

    @QBColumn(name = "age", alias = "user_age")
    val age: Int? = null
}
