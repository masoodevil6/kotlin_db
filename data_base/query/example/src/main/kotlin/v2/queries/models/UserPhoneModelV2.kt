package gog.my_project.data_base.query.example.v2.queries.models

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase

@QBTable(name = "user_phones", alias = "up")
class UserPhoneModelV2 : IModelBase {
    @QBColumn(name = "id", alias = "user_phone_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "phone", alias = "user_phone")
    val phone: String? = null

    @QBColumn(name = "user_id", alias = "phone_user_id")
    val userId: Int = 0
}
