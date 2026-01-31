package gog.my_project.data_base.core.annotations.models

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class QBTable(
    val name: String = "",
    val alias: String = "",
)