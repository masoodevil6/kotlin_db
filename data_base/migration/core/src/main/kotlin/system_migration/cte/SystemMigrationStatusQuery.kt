package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.cte.declaration.CteDeclaration

/** Combines persisted System Migration Tags, Versions, and applied Migration records. */
@QBQuery(name = "system_migration_status_query", alias = "smstq")
class SystemMigrationStatusQuery :
    CteDeclaration<SystemMigrationStatusQuery.RelationFilters, SystemMigrationStatusQuery>() {
    data class RelationFilters(
        val tagId: Int? = null,
        val tagName: String? = null,
        val tagType: Int? = null,
    )

    @QBQuerySelect(name = "tag_id", alias = "tagId")
    var tagId: Int = 0

    @QBQuerySelect(name = "tag_name", alias = "tagName")
    var tagName: String = ""

    @QBQuerySelect(name = "tag_type", alias = "tagType")
    var tagType: Int = 0

    /** Version row attached directly to this tag; null for non-Version or orphan Tags. */
    @QBQuerySelect(name = "tag_version_id", alias = "tagVersionId")
    var tagVersionId: Int? = null

    /** Applied Migration row attached directly to this tag; null when it has not been applied. */
    @QBQuerySelect(name = "migration_history_id", alias = "migrationHistoryId")
    var migrationHistoryId: Int? = null

    /** Raw Version reference from the applied Migration row, retained to expose broken references. */
    @QBQuerySelect(name = "applied_version_reference_id", alias = "appliedVersionReferenceId")
    var appliedVersionReferenceId: Int? = null

    /** Resolved Version row referenced by the applied Migration; null if the reference is broken. */
    @QBQuerySelect(name = "applied_version_id", alias = "appliedVersionId")
    var appliedVersionId: Int? = null

    @QBQuerySelect(name = "applied_version_tag_id", alias = "appliedVersionTagId")
    var appliedVersionTagId: Int? = null

    @QBQuerySelect(name = "applied_version_tag_name", alias = "appliedVersionTagName")
    var appliedVersionTagName: String? = null

    @QBQuerySelect(name = "applied_version_tag_type", alias = "appliedVersionTagType")
    var appliedVersionTagType: Int? = null

    override fun createCte(params: RelationFilters): Cte {
        val tagRelation = SystemMigrationTagQuery().createCte(SystemMigrationTagQuery.RelationFilters())
        val versionRelation = SystemMigrationVersionQuery().createCte(SystemMigrationVersionQuery.RelationFilters())
        val migrationRelation = SystemMigrationQuery().createCte(SystemMigrationQuery.RelationFilters())

        return buildCte {
            withs {
                addWith {
                    with(tagRelation.name, tagRelation)
                }
                addWith {
                    with(versionRelation.name, versionRelation)
                }
                addWith {
                    with(migrationRelation.name, migrationRelation)
                }
            }

            from {
                cte(tagRelation.name)
                alias(TAG_ALIAS)
            }

            select {
                addColumn {
                    column {
                        tableSelector(SystemMigrationTagQuery::class)
                        columnSelector(SystemMigrationTagQuery::id)
                    }
                    alias(SystemMigrationStatusQuery::tagId)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(SystemMigrationTagQuery::class)
                        columnSelector(SystemMigrationTagQuery::tagName)
                    }
                    alias(SystemMigrationStatusQuery::tagName)
                    execute(DataType.STRING)
                }
                addColumn {
                    column {
                        tableSelector(SystemMigrationTagQuery::class)
                        columnSelector(SystemMigrationTagQuery::tagType)
                    }
                    alias(SystemMigrationStatusQuery::tagType)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(TAG_VERSION_ALIAS);
                        columnSelector("id") }
                    alias(SystemMigrationStatusQuery::tagVersionId)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(MIGRATION_ALIAS);
                        columnSelector("id")
                    }
                    alias(SystemMigrationStatusQuery::migrationHistoryId)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(MIGRATION_ALIAS);
                        columnSelector("versionId")
                    }
                    alias(SystemMigrationStatusQuery::appliedVersionReferenceId)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(APPLIED_VERSION_ALIAS);
                        columnSelector("id")
                    }
                    alias(SystemMigrationStatusQuery::appliedVersionId)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(APPLIED_VERSION_TAG_ALIAS);
                        columnSelector("id")
                    }
                    alias(SystemMigrationStatusQuery::appliedVersionTagId)
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(APPLIED_VERSION_TAG_ALIAS);
                        columnSelector("tagName")
                    }
                    alias(SystemMigrationStatusQuery::appliedVersionTagName)
                    execute(DataType.STRING)
                }
                addColumn {
                    column {
                        tableSelector(APPLIED_VERSION_TAG_ALIAS);
                        columnSelector("tagType")
                    }
                    alias(SystemMigrationStatusQuery::appliedVersionTagType)
                    execute(DataType.INT)
                }
            }

            joins {
                addJoin {
                    leftJoin()
                    table {
                        cte(versionRelation.name)
                        alias(TAG_VERSION_ALIAS)
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector {
                                tableSelector(SystemMigrationTagQuery::class)
                                columnSelector(SystemMigrationTagQuery::id)
                            }
                            operationEqual()
                            sideValueColumn { tableSelector(TAG_VERSION_ALIAS); columnSelector("tagId") }
                        }
                    }
                }
                addJoin {
                    leftJoin()
                    table {
                        cte(migrationRelation.name)
                        alias(MIGRATION_ALIAS)
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector {
                                tableSelector(SystemMigrationTagQuery::class)
                                columnSelector(SystemMigrationTagQuery::id)
                            }
                            operationEqual()
                            sideValueColumn { tableSelector(MIGRATION_ALIAS); columnSelector("tagId") }
                        }
                    }
                }
                addJoin {
                    leftJoin()
                    table {
                        cte(versionRelation.name)
                        alias(APPLIED_VERSION_ALIAS)
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector { tableSelector(MIGRATION_ALIAS); columnSelector("versionId") }
                            operationEqual()
                            sideValueColumn { tableSelector(APPLIED_VERSION_ALIAS); columnSelector("id") }
                        }
                    }
                }
                addJoin {
                    leftJoin()
                    table {
                        cte(tagRelation.name)
                        alias(APPLIED_VERSION_TAG_ALIAS)
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector { tableSelector(APPLIED_VERSION_ALIAS); columnSelector("tagId") }
                            operationEqual()
                            sideValueColumn { tableSelector(APPLIED_VERSION_TAG_ALIAS); columnSelector("id") }
                        }
                    }
                }
            }

            if (params.tagId != null || params.tagName != null || params.tagType != null) {
                where {
                    conditions {
                        params.tagId?.let { value ->
                            addCondition {
                                logicalAnd()
                                sideSelector {
                                    tableSelector(SystemMigrationTagQuery::class)
                                    columnSelector(SystemMigrationTagQuery::id)
                                }
                                operationEqual()
                                sideValueParam {
                                    paramName("system_migration_status_tag_id")
                                    paramValue(value)
                                }
                            }
                        }
                        params.tagName?.let { value ->
                            addCondition {
                                logicalAnd()
                                sideSelector {
                                    tableSelector(SystemMigrationTagQuery::class)
                                    columnSelector(SystemMigrationTagQuery::tagName)
                                }
                                operationEqual()
                                sideValueParam {
                                    paramName("system_migration_status_tag_name")
                                    paramValue(value)
                                }
                            }
                        }
                        params.tagType?.let { value ->
                            addCondition {
                                logicalAnd()
                                sideSelector {
                                    tableSelector(SystemMigrationTagQuery::class)
                                    columnSelector(SystemMigrationTagQuery::tagType)
                                }
                                operationEqual()
                                sideValueParam {
                                    paramName("system_migration_status_tag_type")
                                    paramValue(value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG_ALIAS = "status_tag"
        const val TAG_VERSION_ALIAS = "tag_version"
        const val MIGRATION_ALIAS = "applied_migration"
        const val APPLIED_VERSION_ALIAS = "applied_version"
        const val APPLIED_VERSION_TAG_ALIAS = "applied_version_tag"
    }
}
