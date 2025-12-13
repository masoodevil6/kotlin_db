package gog.kotlin_db.data.base.schemas

@Target(AnnotationTarget.CLASS)
annotation class QBTable(
    val name: String = "",
)