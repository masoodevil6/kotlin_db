package gog.my_project.datas

data class BuiltQuery(
    val query: String? ,
    val params: MutableList<SqlParameter<*>>
){




}
