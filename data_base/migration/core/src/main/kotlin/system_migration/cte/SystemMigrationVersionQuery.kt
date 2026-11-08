package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base_query.builder.ast.select_builder.cte.buildCte

@QBQuery(name = "system_migration_version_query", alias = "smvq")
class SystemMigrationVersionQuery : ICteDeclaration<SystemMigrationVersionQuery.RelationFilters> {
    data class RelationFilters(
        val id: Int? = null,
        val tagId: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    var id: Int = 0

    @QBQuerySelect(name = "tag_id", alias = "tagId")
    var tagId: Int = 0

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column {
                    tableSelector(SystemMigrationVersionModel::class)
                    columnSelector(SystemMigrationVersionModel::id)
                }
                alias(SystemMigrationVersionQuery::id)
            }
            addColumn {
                column {
                    tableSelector(SystemMigrationVersionModel::class)
                    columnSelector(SystemMigrationVersionModel::tagId)
                }
                alias(SystemMigrationVersionQuery::tagId)
            }
        }
        from { model(SystemMigrationVersionModel::class) }

        if (params.id != null || params.tagId != null) {
            where {
                conditions {
                    params.id?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationVersionModel::class)
                                columnSelector(SystemMigrationVersionModel::id)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_version_id")
                                paramValue(value)
                            }
                        }
                    }
                    params.tagId?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationVersionModel::class)
                                columnSelector(SystemMigrationVersionModel::tagId)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_version_tag_id")
                                paramValue(value)
                            }
                        }
                    }
                }
            }
        }
    }
}
