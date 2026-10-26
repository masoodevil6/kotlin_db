package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@QBTable
private class DefaultUserModel : IModelBase {
    @QBColumn
    val userId: Int = 0

    @QBColumn(alias = "")
    val displayName: String = ""
}

@QBTable(name = "empty_table_name", alias = "")
private class EmptyAliasModel : IModelBase {
    @QBColumn(name = "")
    val invalidColumn: Int = 0
}

@QBTable(name = "explicit_dml_table", alias = "ed")
private class ExplicitDmlModel : IModelBase {
    @QBColumn(name = "insert_id", alias = "insert_id_output")
    val insertId: Int get() = error("Metadata resolution must not invoke the getter")

    @QBColumn(name = "updated_amount", alias = "updated_amount_output")
    val amount: Int get() = error("Metadata resolution must not invoke the getter")
}

@QBQuery(name = "logical_people", alias = "people_source")
private class MetadataQuery : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "person_id", alias = "published_id")
    val id: Int get() = error("Query output getter must not be read")

    @QBQuerySelect(name = "person_label", alias = "annotation_label")
    val label: String get() = error("Query output getter must not be read")

    override fun createCte(params: Unit): Cte = error("Factory is invoked directly in this test")
}

@QBQuery(name = "annotation_named_default")
private class DefaultNamedQuery : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

private class ConventionNamedQuery : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

@QBQuery(name = "")
private class EmptyNamedQuery : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

@QBQuery(alias = "")
private class EmptyAliasQuery : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = error("Not used")
}

private class EmptyOutputMetadataQuery : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "")
    val emptyLogicalName: Int get() = error("Query output getter must not be read")
    @QBQuerySelect(alias = "")
    val emptyPublishedAlias: Int get() = error("Query output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

private class DuplicateOutputsQuery : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "duplicate", alias = "first_sql_alias")
    val first: Int get() = error("Query output getter must not be read")
    @QBQuerySelect(name = "duplicate", alias = "second_sql_alias")
    val second: Int get() = error("Query output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

private class FullNameQuery : ICteDeclaration<Unit> {
    val fullName: String get() = error("Query output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

private open class BaseInheritedQuery : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "shared_code", alias = "inherited_code")
    open val inheritedCode: Int get() = error("Inherited output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

@QBQuery(alias = "derived_source")
private class DerivedInheritedQuery : BaseInheritedQuery() {
    @QBQuerySelect(name = "shared_code", alias = "derived_code")
    val ownCode: Int get() = error("Derived output getter must not be read")
}

class QueryMetadataResolutionTest {

    @Test
    fun `model class and property defaults resolve independently and across typed builders`() {
        val tableQuery = QueryRenderSelectBuilder().from { model(DefaultUserModel::class) }
        assertEquals("default_user", tableQuery.ast.table!!.metadata.nameFromModel)
        assertNull(tableQuery.ast.table!!.metadata.nameFromAnnotation)
        assertEquals("default_user", tableQuery.ast.table!!.metadata.aliasFromModel)
        assertNull(tableQuery.ast.table!!.metadata.aliasFromAnnotation)

        val selectQuery = QueryRenderSelectBuilder().from { model(DefaultUserModel::class) }.select {
            addColumn { column { tableSelector(DefaultUserModel::class); columnSelector(DefaultUserModel::class, DefaultUserModel::userId) } }
        }
        val selectOutput = selectQuery.ast.select!!.columns.single()
        assertEquals("userId", selectOutput.Column!!.metadata.resolveColumnName())
        assertEquals("userId", selectOutput.metadata.ColumnAlias)

        val baseColumn = QueryColumnsBaseBuilder().apply {
            columnSelector(DefaultUserModel::class, DefaultUserModel::userId)
        }
        assertEquals("userId", baseColumn.ast.metadata.resolveColumnName())

        val insert = QueryRenderInsertBuilder().addValue {
            column(DefaultUserModel::class, DefaultUserModel::userId)
            setValue { paramValue(7) }
        }
        assertEquals("userId", insert.ast.columns.single().metadata.columnName)
        assertEquals("userId", insert.ast.columns.single().parameterAst?.metadata?.name)
        assertEquals(7, insert.ast.columns.single().parameterAst?.metadata?.value)

        val update = QueryRenderUpdateBuilder().addValue {
            column(DefaultUserModel::class, DefaultUserModel::userId)
            setValue { paramValue(8) }
        }.table { table(DefaultUserModel::class) }
        assertEquals("userId", update.ast.columns.single().metadata.columnName)
        assertEquals("default_user", update.ast.columns.single().metadata.columnAlias)
        assertEquals(8, update.ast.columns.single().parameterAst?.metadata?.value)

        val explicitEmptyAlias = QueryRenderSelectBuilder().from { model(EmptyAliasModel::class) }
        assertEquals("", explicitEmptyAlias.ast.table!!.metadata.aliasFromAnnotation)
        val emptyColumnAlias = QueryRenderSelectBuilder().select {
            addColumn { column { tableSelector(DefaultUserModel::class); columnSelector(DefaultUserModel::class, DefaultUserModel::displayName) } }
        }.ast.select!!.columns.single()
        assertEquals("displayName", emptyColumnAlias.metadata.ColumnAlias)
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().select {
                addColumn { column { tableSelector(EmptyAliasModel::class); columnSelector(EmptyAliasModel::class, EmptyAliasModel::invalidColumn) } }
            }
        }
    }

    @Test
    fun `explicit model annotation names reach insert and update ASTs`() {
        val insert = QueryRenderInsertBuilder()
            .table { model(ExplicitDmlModel::class) }
            .addValue {
                column(ExplicitDmlModel::class, ExplicitDmlModel::insertId)
                setValue { paramValue(7) }
            }

        assertEquals("explicit_dml_table", insert.ast.table!!.metadata.nameFromAnnotation)
        assertEquals("ed", insert.ast.table!!.metadata.aliasFromAnnotation)
        assertEquals("insert_id", insert.ast.columns.single().metadata.columnName)
        assertEquals("insert_id", insert.ast.columns.single().parameterAst?.metadata?.name)

        val update = QueryRenderUpdateBuilder()
            .addValue {
                column(ExplicitDmlModel::class, ExplicitDmlModel::amount)
                setValue { paramValue(8) }
            }
            .table { model(ExplicitDmlModel::class) }

        assertEquals("explicit_dml_table", update.ast.table!!.metadata.nameFromAnnotation)
        assertEquals("ed", update.ast.table!!.metadata.aliasFromAnnotation)
        assertEquals("updated_amount", update.ast.columns.single().metadata.columnName)
        assertEquals("ed", update.ast.columns.single().metadata.columnAlias)
        assertEquals("ed_updated_amount", update.ast.columns.single().metadata.columnTag)
        assertEquals("ed_updated_amount", update.ast.columns.single().parameterAst?.metadata?.name)

        val explicitlyAliasedUpdate = QueryRenderUpdateBuilder().addValue {
            column(ExplicitDmlModel::class, ExplicitDmlModel::amount, "caller_alias")
            setValue { paramValue(9) }
        }
        assertEquals("caller_alias", explicitlyAliasedUpdate.ast.columns.single().metadata.columnAlias)
        assertEquals(
            "caller_alias_updated_amount",
            explicitlyAliasedUpdate.ast.columns.single().parameterAst?.metadata?.name,
        )
    }

    @Test
    fun `INSERT independent value keeps resolved model column metadata`() {
        val insert = QueryRenderInsertBuilder().addValue {
            column(ExplicitDmlModel::class, ExplicitDmlModel::insertId)
            setValue {
                paramName("insert_parameter")
                paramValue(17)
            }
        }

        val column = insert.ast.columns.single()
        assertEquals("insert_id", column.metadata.columnName)
        assertEquals("insert_id", column.metadata.columnTag)
        assertEquals("insert_parameter", column.parameterAst?.metadata?.name)
        assertEquals(17, column.parameterAst?.metadata?.value)
    }

    @Test
    fun `UPDATE independent value keeps resolved model column metadata and caller alias`() {
        val update = QueryRenderUpdateBuilder().addValue {
            column(ExplicitDmlModel::class, ExplicitDmlModel::amount, "caller_alias")
            setValue {
                paramName("update_parameter")
                paramValue(18)
            }
        }

        val column = update.ast.columns.single()
        assertEquals("caller_alias", column.metadata.columnAlias)
        assertEquals("updated_amount", column.metadata.columnName)
        assertEquals("caller_alias_updated_amount", column.metadata.columnTag)
        assertEquals("update_parameter", column.parameterAst?.metadata?.name)
        assertEquals(18, column.parameterAst?.metadata?.value)
    }

    @Test
    fun `explicit factory runtime name overrides query metadata while occurrence alias stays independent`() {
        val relation = cte("runtime_people", MetadataQuery::class) {
            from { table("source_people") }
            select {
                addColumn {
                    column { tableAttribute("1") }
                    execute(DataType.LONG)
                    alias(MetadataQuery::id)
                }
                addColumn {
                    column { tableAttribute("'Ada'") }
                    alias(MetadataQuery::label)
                    alias("dsl_label")
                }
            }
        }
        assertEquals("runtime_people", relation.name)
        assertEquals("logical_people", (relation as BuiltCteComposition).logicalQueryName)
        assertEquals("published_id", relation.definition.ast.select!!.columns[0].metadata.ColumnAlias)
        assertEquals(DataType.LONG, relation.definition.ast.select!!.columns[0].metadata.ExecutionType)
        assertEquals("dsl_label", relation.definition.ast.select!!.columns[1].metadata.ColumnAlias)
        assertEquals(Int::class, MetadataQuery::id.returnType.classifier)

        val defaultOccurrence = QueryRenderSelectBuilder().from { cte(relation) }
        assertEquals("people_source", defaultOccurrence.ast.table!!.metadata.aliasFromAst)
        val explicitOccurrence = QueryRenderSelectBuilder().from { cte(relation).alias("explicit_people") }
        assertEquals("explicit_people", explicitOccurrence.ast.table!!.metadata.aliasFromAst)

        val consumer = QueryRenderSelectBuilder().from { cte(relation) }.select {
            addColumn {
                column { tableSelector(MetadataQuery::class); columnSelector(MetadataQuery::label) }
                alias(MetadataQuery::label)
            }
        }
        val output = consumer.ast.select!!.columns.single()
        assertEquals("dsl_label", output.Column!!.metadata.resolveColumnName())
        assertEquals("dsl_label", output.metadata.ColumnAlias)
        assertEquals(MetadataQuery::label, output.metadata.PropertyReference)
    }

    @Test
    fun `declaration helper resolves annotation name without a runtime name property`() {
        val declaration = DefaultNamedQuery()
        val annotated = declaration.createCte(Unit)
        assertEquals("annotation_named_default", annotated.name)
        assertEquals(DefaultNamedQuery::class, (annotated as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `direct declaration helper falls back to permitted class convention`() {
        val declaration = ConventionNamedQuery()
        val relation = declaration.createCte(Unit)
        assertEquals("convention_named", relation.name)
        assertEquals(ConventionNamedQuery::class, (relation as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `declaration helper rejects explicitly empty query name`() {
        val declaration = EmptyNamedQuery()
        assertFailsWith<IllegalArgumentException> { declaration.createCte(Unit) }
    }

    @Test
    fun `factory without owner preserves caller supplied runtime name`() {
        val relation = cte("caller_named") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        assertEquals("caller_named", relation.name)
        assertEquals(null, (relation as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `explicit ownerless factory name does not conflict with QBQuery name in typed output`() {
        val relation = cte("factory_runtime_override") {
            select {
                addColumn {
                    column { tableAttribute("1") }
                    alias(MetadataQuery::id)
                }
            }
        }

        assertEquals("factory_runtime_override", relation.name)
        assertEquals("published_id", relation.definition.ast.select!!.columns.single().metadata.ColumnAlias)
    }

    @Test
    fun `logical output duplicates fail even when published SQL aliases differ`() {
        assertFailsWith<IllegalArgumentException> {
            cte("duplicate_logical", DuplicateOutputsQuery::class) {
                from { table("source_people") }
                select {
                    addColumn { column { tableAttribute("1") }; alias(DuplicateOutputsQuery::first) }
                    addColumn { column { tableAttribute("2") }; alias(DuplicateOutputsQuery::second) }
                }
            }
        }
    }

    @Test
    fun `valid inherited query output participates in logical name validation`() {
        val relation = cte("derived_inherited", DerivedInheritedQuery::class) {
            from { table("source_people") }
            select {
                addColumn {
                    column { tableAttribute("1") }
                    alias(DerivedInheritedQuery::inheritedCode)
                }
            }
        }
        assertEquals("inherited_code", relation.definition.ast.select!!.columns.single().metadata.ColumnAlias)

        assertFailsWith<IllegalArgumentException> {
            cte("derived_duplicate", DerivedInheritedQuery::class) {
                from { table("source_people") }
                select {
                    addColumn {
                        column { tableAttribute("1") }
                        alias(DerivedInheritedQuery::inheritedCode)
                    }
                    addColumn {
                        column { tableAttribute("2") }
                        alias(DerivedInheritedQuery::ownCode)
                    }
                }
            }
        }
    }

    @Test
    fun `empty metadata occurrence alias is validated even when explicit alias is supplied`() {
        val relation = cte("empty_alias_runtime", EmptyAliasQuery::class) {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { cte(relation).alias("explicit_occurrence") }
        }

        assertEquals(
            "@QBQuery on ${EmptyAliasQuery::class.qualifiedName} must declare a non-empty query alias",
            error.message,
        )
    }
}
