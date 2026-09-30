package gog.my_project.data_base.core.annotations.ctes

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class QBCte(
    val name: String = "",
    val alias: String = "",
)
