package gog.my_project.data_base.core.annotations.migrations

/** Declares the stable, persisted identity of a migration class. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MigrationId(val value: String)
