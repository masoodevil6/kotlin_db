package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.api.interfaces.MigrationIdentity
import gog.my_project.data_base.migration.api.interfaces.MigrationRegistration
import gog.my_project.data_base.migration.api.interfaces.RegisteredMigration
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import kotlin.jvm.java
import kotlin.reflect.KClass

class MigrationConfigurationBuilder {
    private val registrations = mutableListOf<MigrationRegistration>()

    inline fun <reified T : Migration> migration() {
        registerMigration(T::class)
    }

    fun migrationGroup(name: String, block: MigrationGroupBuilder.() -> Unit) {
        require(name.isNotBlank()) { "Migration group name must not be blank" }

        val groupBuilder = MigrationGroupBuilder(::resolveMigration).apply(block)
        registrations += MigrationGroup(name, groupBuilder.build())
    }

    @PublishedApi
    internal fun registerMigration(type: KClass<out Migration>) {
        registrations += SingleMigration(resolveMigration(type))
    }

    internal fun build(): MigrationConfiguration = MigrationConfiguration(registrations)

    private fun resolveMigration(type: KClass<out Migration>): RegisteredMigration {
        val javaClass = type.java
        val annotation = javaClass.getAnnotation(MigrationId::class.java)
            ?: throw IllegalArgumentException(
                "Migration class '${javaClass.name}' must declare @MigrationId",
            )

        val identity = try {
            MigrationIdentity.fromAnnotationValue(annotation.value)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid MigrationId '${annotation.value}' on '${javaClass.name}': ${exception.message}",
                exception,
            )
        }

        return RegisteredMigration(identity, type)
    }
}

class MigrationGroupBuilder internal constructor(
    private val resolveMigration: (KClass<out Migration>) -> RegisteredMigration,
) {
    private val migrations = mutableListOf<RegisteredMigration>()

    inline fun <reified T : Migration> migration() {
        registerMigration(T::class)
    }

    @PublishedApi
    internal fun registerMigration(type: KClass<out Migration>) {
        migrations += resolveMigration(type)
    }

    internal fun build(): List<RegisteredMigration> = migrations.toList()
}
