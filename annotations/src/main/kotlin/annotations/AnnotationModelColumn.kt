package data.base.schemas

@Target(AnnotationTarget.PROPERTY)
annotation class QBColumn(
    val name: String = "" ,
    val alias: String = "",
    val primaryKey: Boolean = false
)