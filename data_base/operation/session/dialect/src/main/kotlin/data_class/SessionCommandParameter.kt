package gog.my_project.data_base_session.dialect.data_class

data class SessionCommandParameter(
    val name: String,
    val value: Any?,
    val sqlType: Int,
)
