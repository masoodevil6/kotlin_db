package gog.my_project.data_base.core.annotations.models

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class QBColumn(
    val name: String = QB_METADATA_UNSET,
    val alias: String = QB_METADATA_UNSET,
    val primaryKey: Boolean = false
)
