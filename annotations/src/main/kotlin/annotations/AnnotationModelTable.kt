package data.base.schemas

@Target(AnnotationTarget.CLASS)
annotation class QBTable(
    val name: String = "",
)