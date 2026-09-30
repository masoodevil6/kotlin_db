package gog.my_project.data_base.core.annotations.ctes

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class QBCteSelect(
    val name: String = "",
    val alias: String = "",
)
