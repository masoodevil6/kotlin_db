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
import gog.my_project.data_base.query.builder.ast.select_builder.metadata.QueryBuilderMetadata
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.fromCteInternal
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

@QBQuery(name = "logical_people", alias = "people_source")
private class MetadataQuery(override val params: Unit) : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "person_id", alias = "published_id")
    val id: Int get() = error("Query output getter must not be read")

    @QBQuerySelect(name = "person_label", alias = "annotation_label")
    val label: String get() = error("Query output getter must not be read")

    override fun createCte(params: Unit): Cte = error("Factory is invoked directly in this test")
}

@QBQuery(name = "annotation_named_default")
private class DefaultNamedQuery(override val params: Unit) : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

private class ConventionNamedQuery(override val params: Unit) : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

@QBQuery(name = "")
private class EmptyNamedQuery(override val params: Unit) : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

@QBQuery(alias = "")
private class EmptyAliasQuery(override val params: Unit) : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = error("Not used")
}

private class EmptyOutputMetadataQuery(override val params: Unit) : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "")
    val emptyLogicalName: Int get() = error("Query output getter must not be read")
    @QBQuerySelect(alias = "")
    val emptyPublishedAlias: Int get() = error("Query output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

private class DuplicateOutputsQuery(override val params: Unit) : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "duplicate", alias = "first_sql_alias")
    val first: Int get() = error("Query output getter must not be read")
    @QBQuerySelect(name = "duplicate", alias = "second_sql_alias")
    val second: Int get() = error("Query output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

private class FullNameQuery(override val params: Unit) : ICteDeclaration<Unit> {
    val fullName: String get() = error("Query output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

private open class BaseInheritedQuery(override val params: Unit) : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "shared_code", alias = "inherited_code")
    open val inheritedCode: Int get() = error("Inherited output getter must not be read")
    override fun createCte(params: Unit): Cte = error("Not used")
}

@QBQuery(alias = "derived_source")
private class DerivedInheritedQuery : BaseInheritedQuery(Unit) {
    @QBQuerySelect(name = "shared_code", alias = "derived_code")
    val ownCode: Int get() = error("Derived output getter must not be read")
}

class QueryMetadataResolutionTest {

    @Test
    fun `model class and property defaults resolve independently and across typed builders`() {
        val tableQuery = QueryRenderSelectBuilder().from { model(DefaultUserModel::class) }
        assertEquals("default_user", tableQuery.ast.table!!.table)
        assertEquals("default_user", tableQuery.ast.table!!.tableAlias)

        val selectQuery = QueryRenderSelectBuilder().from { model(DefaultUserModel::class) }.select {
            addColumn { column(DefaultUserModel::class, DefaultUserModel::userId) }
        }
        val selectOutput = selectQuery.ast.select!!.columns.single()
        assertEquals("userId", selectOutput.Column!!.column)
        assertEquals("userId", selectOutput.ColumnAlias)

        val baseColumn = QueryColumnsBaseBuilder().tableColumn(
            DefaultUserModel::class,
            DefaultUserModel::userId,
            "du",
        )
        assertEquals("userId", baseColumn.ast.column)

        val insert = QueryRenderInsertBuilder().addValue {
            column(DefaultUserModel::class, DefaultUserModel::userId, 7)
        }
        assertEquals("userId", insert.ast.columns.single().columnName)
        assertEquals("userId", insert.params.single().name)

        val update = QueryRenderUpdateBuilder().addValue {
            column(DefaultUserModel::class, DefaultUserModel::userId, 8)
        }.table { table(DefaultUserModel::class) }
        assertEquals("userId", update.ast.columns.single().columnName)
        assertEquals("default_user", update.ast.columns.single().columnAlias)

        val explicitEmptyAlias = QueryRenderSelectBuilder().from { model(EmptyAliasModel::class) }
        assertEquals("", explicitEmptyAlias.ast.table!!.tableAlias)
        val emptyColumnAlias = QueryRenderSelectBuilder().select {
            addColumn { column(DefaultUserModel::class, DefaultUserModel::displayName) }
        }.ast.select!!.columns.single()
        assertEquals("displayName", emptyColumnAlias.ColumnAlias)
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().select {
                addColumn { column(EmptyAliasModel::class, EmptyAliasModel::invalidColumn) }
            }
        }
    }

    @Test
    fun `explicit factory runtime name overrides query metadata while occurrence alias stays independent`() {
        assertEquals("logical_people", QueryBuilderMetadata.queryName(MetadataQuery::class))
        assertEquals("annotation_named_default", QueryBuilderMetadata.queryName(DefaultNamedQuery::class))

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
        assertEquals("published_id", relation.definition.ast.select!!.columns[0].ColumnAlias)
        assertEquals(DataType.LONG, relation.definition.ast.select!!.columns[0].ExecutionType)
        assertEquals("dsl_label", relation.definition.ast.select!!.columns[1].ColumnAlias)
        assertEquals(Int::class, MetadataQuery::id.returnType.classifier)

        val defaultOccurrence = QueryRenderSelectBuilder().fromCteInternal(relation)
        assertEquals("people_source", defaultOccurrence.ast.table!!.cteAlias)
        val explicitOccurrence = QueryRenderSelectBuilder().fromCteInternal(relation, "explicit_people")
        assertEquals("explicit_people", explicitOccurrence.ast.table!!.cteAlias)

        val consumer = QueryRenderSelectBuilder().fromCteInternal(relation).select {
            addColumn {
                cteOutput(MetadataQuery::label, "people_source")
                alias(MetadataQuery::label)
            }
        }
        val output = consumer.ast.select!!.columns.single()
        assertEquals("dsl_label", output.Column!!.select)
        assertEquals("dsl_label", output.ColumnAlias)
        assertEquals(MetadataQuery::label, output.PropertyReference)
    }

    @Test
    fun `declaration helper resolves annotation name without a runtime name property`() {
        val declaration = DefaultNamedQuery(Unit)
        val annotated = declaration.createCte(declaration.params)
        assertEquals("annotation_named_default", annotated.name)
        assertEquals(DefaultNamedQuery::class, (annotated as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `direct declaration helper falls back to permitted class convention`() {
        val declaration = ConventionNamedQuery(Unit)
        val relation = declaration.createCte(declaration.params)
        assertEquals("convention_named", relation.name)
        assertEquals(ConventionNamedQuery::class, (relation as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `declaration helper rejects explicitly empty query name`() {
        val declaration = EmptyNamedQuery(Unit)
        assertFailsWith<IllegalArgumentException> { declaration.createCte(declaration.params) }
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
        assertEquals("published_id", relation.definition.ast.select!!.columns.single().ColumnAlias)
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
        assertEquals("inherited_code", relation.definition.ast.select!!.columns.single().ColumnAlias)

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
    fun `logical output default is distinct from published alias and explicit execution type`() {
        assertEquals("full_name", QueryBuilderMetadata.queryLogicalOutputName(FullNameQuery::fullName))
        assertEquals("full_name", QueryBuilderMetadata.queryPublishedAlias(FullNameQuery::fullName))
        assertFailsWith<IllegalArgumentException> { QueryBuilderMetadata.queryName(EmptyNamedQuery::class) }
        assertFailsWith<IllegalArgumentException> { QueryBuilderMetadata.queryAlias(EmptyAliasQuery::class) }
        assertFailsWith<IllegalArgumentException> {
            QueryBuilderMetadata.queryLogicalOutputName(EmptyOutputMetadataQuery::emptyLogicalName)
        }
        assertFailsWith<IllegalArgumentException> {
            QueryBuilderMetadata.queryPublishedAlias(EmptyOutputMetadataQuery::emptyPublishedAlias)
        }
    }
}
