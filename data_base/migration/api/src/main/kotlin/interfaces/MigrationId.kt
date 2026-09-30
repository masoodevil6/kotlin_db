package gog.my_project.data_base.migration.api.interfaces

/** Declares the stable, persisted identity of a migration class. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MigrationId(val value: String)
