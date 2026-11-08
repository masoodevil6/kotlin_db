package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationTagModel
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base_query.builder.ast.select_builder.cte.buildCte

@QBQuery(name = "system_migration_tag_query", alias = "smtq")
class SystemMigrationTagQuery : ICteDeclaration<SystemMigrationTagQuery.RelationFilters> {
    data class RelationFilters(
        val id: Int? = null,
        val tagName: String? = null,
        val tagType: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    var id: Int = 0

    @QBQuerySelect(name = "tagName", alias = "tagName")
    var tagName: String = ""

    @QBQuerySelect(name = "tagType", alias = "tagType")
    var tagType: Int = 0

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column {
                    tableSelector(SystemMigrationTagModel::class)
                    columnSelector(SystemMigrationTagModel::id)
                }
                alias(SystemMigrationTagQuery::id)
            }
            addColumn {
                column {
                    tableSelector(SystemMigrationTagModel::class)
                    columnSelector(SystemMigrationTagModel::tagName)
                }
                alias(SystemMigrationTagQuery::tagName)
            }
            addColumn {
                column {
                    tableSelector(SystemMigrationTagModel::class)
                    columnSelector(SystemMigrationTagModel::tagType)
                }
                alias(SystemMigrationTagQuery::tagType)
            }
        }
        from { model(SystemMigrationTagModel::class) }

        if (params.id != null || params.tagName != null || params.tagType != null) {
            where {
                conditions {
                    params.id?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationTagModel::class)
                                columnSelector(SystemMigrationTagModel::id)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_tag_id")
                                paramValue(value)
                            }
                        }
                    }
                    params.tagName?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationTagModel::class)
                                columnSelector(SystemMigrationTagModel::tagName)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_tag_name")
                                paramValue(value)
                            }
                        }
                    }
                    params.tagType?.let { value ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(SystemMigrationTagModel::class)
                                columnSelector(SystemMigrationTagModel::tagType)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("system_migration_tag_type")
                                paramValue(value)
                            }
                        }
                    }
                }
            }
        }
    }
}
