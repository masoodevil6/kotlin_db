package gog.my_project.data_base.core.annotations.models

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class QBColumn(
    val name: String = "" ,
    val alias: String = "",
    val primaryKey: Boolean = false
)