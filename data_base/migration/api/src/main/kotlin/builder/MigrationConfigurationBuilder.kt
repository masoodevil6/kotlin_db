package gog.my_project.data_base_migration.api.builder

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.group.MigrationNode
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import kotlin.jvm.java
import kotlin.reflect.KClass

class MigrationConfigurationBuilder {
    private val versions = mutableListOf<MigrationVersion>()

    fun migrationVersion(tag: MigrationVersionTag, block: MigrationVersionBuilder.() -> Unit) {
        addMigrationVersion(tag, block)
    }

    internal fun build(): MigrationConfiguration = MigrationConfiguration(versions)

    private fun addMigrationVersion(
        tag: MigrationVersionTag,
        block: MigrationVersionBuilder.() -> Unit,
    ) {
        tag.validatedValue
        val versionBuilder = MigrationVersionBuilder(::resolveMigration).apply(block)
        val registrations = versionBuilder.build()
        versions += MigrationVersion.fromTag(tag, registrations)
    }

    private fun resolveMigration(
        type: KClass<out Migration>,
    ): RegisteredMigration {
        val migration = try {
            type.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        } catch (exception: ReflectiveOperationException) {
            throw IllegalArgumentException(
                "Migration class '${type.qualifiedName}' must have a no-argument constructor " +
                    "so its identity can be read",
                exception,
            )
        }
        return RegisteredMigration(migration.identity, type)
    }
}

class MigrationVersionBuilder internal constructor(
    private val resolveMigration: (KClass<out Migration>) -> RegisteredMigration,
) {
    private val registrations = mutableListOf<MigrationNode>()

    inline fun <reified T : Migration> migration() {
        registerMigration(T::class)
    }

    fun migrationGroup(tag: MigrationGroupTag, block: MigrationGroupBuilder.() -> Unit) {
        addMigrationGroup(tag, block)
    }

    private fun addMigrationGroup(
        tag: MigrationGroupTag,
        block: MigrationGroupBuilder.() -> Unit,
    ) {
        tag.validatedValue
        val groupBuilder = MigrationGroupBuilder(resolveMigration).apply(block)
        val children = groupBuilder.build()
        registrations += MigrationGroup.fromTag(tag, children)
    }

    @PublishedApi
    internal fun registerMigration(type: KClass<out Migration>) {
        registrations += SingleMigration(resolveMigration(type))
    }

    internal fun build(): List<MigrationNode> = registrations.toList()
}

class MigrationGroupBuilder internal constructor(
    private val resolveMigration: (KClass<out Migration>) -> RegisteredMigration,
) {
    private val registrations = mutableListOf<MigrationNode>()

    inline fun <reified T : Migration> migration() {
        registerMigration(T::class)
    }

    fun migrationGroup(tag: MigrationGroupTag, block: MigrationGroupBuilder.() -> Unit) {
        addMigrationGroup(tag, block)
    }

    private fun addMigrationGroup(
        tag: MigrationGroupTag,
        block: MigrationGroupBuilder.() -> Unit,
    ) {
        tag.validatedValue
        val groupBuilder = MigrationGroupBuilder(resolveMigration).apply(block)
        val children = groupBuilder.build()
        registrations += MigrationGroup.fromTag(tag, children)
    }

    @PublishedApi
    internal fun registerMigration(type: KClass<out Migration>) {
        registrations += SingleMigration(resolveMigration(type))
    }

    internal fun build(): List<MigrationNode> = registrations.toList()
}
