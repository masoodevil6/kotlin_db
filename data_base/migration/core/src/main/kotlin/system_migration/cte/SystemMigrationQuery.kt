package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationModel
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base_query.builder.ast.select_builder.cte.buildCte

@QBQuery(name = "system_migration_query", alias = "smq")
class SystemMigrationQuery : ICteDeclaration<SystemMigrationQuery.RelationFilters> {
    data class RelationFilters(
        val id: Int? = null,
        val tagId: Int? = null,
        val versionId: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    var id: Int = 0

    @QBQuerySelect(name = "tag_id", alias = "tagId")
    var tagId: Int = 0

    @QBQuerySelect(name = "version_id", alias = "versionId")
    var versionId: Int = 0

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column {
                    tableSelector(SystemMigrationModel::class)
                    columnSelector(SystemMigrationModel::id)
                }
                alias(SystemMigrationQuery::id)
            }
            addColumn {
                column {
                    tableSelector(SystemMigrationModel::class)
                    columnSelector(SystemMigrationModel::tagId)
                }
                alias(SystemMigrationQuery::tagId)
            }
            addColumn {
                column {
                    tableSelector(SystemMigrationModel::class)
                    columnSelector(SystemMigrationModel::versionId)
                }
                alias(SystemMigrationQuery::versionId)
            }
        }
        from { model(SystemMigrationModel::class) }

        if (params.id != null || params.tagId != null || params.versionId != null) {
            where {
                conditions {
                    params.id?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationModel::class)
                                columnSelector(SystemMigrationModel::id)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_id")
                                paramValue(value)
                            }
                        }
                    }
                    params.tagId?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationModel::class)
                                columnSelector(SystemMigrationModel::tagId)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_tag_id")
                                paramValue(value)
                            }
                        }
                    }
                    params.versionId?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationModel::class)
                                columnSelector(SystemMigrationModel::versionId)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_version_id")
                                paramValue(value)
                            }
                        }
                    }
                }
            }
        }
    }
}
