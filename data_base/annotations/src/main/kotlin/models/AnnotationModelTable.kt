package gog.my_project.data_base.annotations.models

@Target(AnnotationTarget.CLASS)
annotation class QBTable(
    val name: String = "",
)