package gog.my_project.data_base.query.builder.metadata.models

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@QBTable
private class ConventionUserModel {
    @QBColumn
    val userID: Int get() = error("Metadata resolution must not invoke the getter")

    @QBColumn(alias = "")
    val displayName: String get() = error("Metadata resolution must not invoke the getter")
}

@QBTable(name = "account_rows", alias = "ar")
private class ExplicitAccountModel {
    @QBColumn(name = "account_id", alias = "published_id")
    val id: Int get() = error("Metadata resolution must not invoke the getter")
}

@QBTable(alias = "")
private class NoAliasModel {
    @QBColumn
    val id: Int get() = error("Metadata resolution must not invoke the getter")
}

@QBTable(name = "", alias = "")
private class EmptyTableNameModel {
    @QBColumn(name = "", alias = "")
    val invalidColumn: Int get() = error("Metadata resolution must not invoke the getter")
}

private class MissingTableModel

@QBTable
private class MissingColumnModel {
    val unannotated: Int = 0
}

private open class InheritedPropertyModel {
    @QBColumn(name = "inherited_code", alias = "ic")
    open val code: Int get() = error("Metadata resolution must not invoke the getter")
}

@QBTable
private class DerivedInheritedModel : InheritedPropertyModel()

class ModelMetadataResolverTest {

    @Test
    fun `unset table and column metadata use current conventions`() {
        assertEquals("convention_user", ModelMetadataResolver.tableName(ConventionUserModel::class))
        assertEquals("convention_user", ModelMetadataResolver.tableAlias(ConventionUserModel::class))
        assertEquals("userID", ModelMetadataResolver.columnName(ConventionUserModel::class, ConventionUserModel::userID))
        assertEquals("userID", ModelMetadataResolver.columnAlias(ConventionUserModel::userID))
        assertEquals("displayName", ModelMetadataResolver.columnAlias(ConventionUserModel::displayName))
    }

    @Test
    fun `explicit annotation names and aliases are returned independently`() {
        assertEquals("account_rows", ModelMetadataResolver.tableName(ExplicitAccountModel::class))
        assertEquals("ar", ModelMetadataResolver.tableAlias(ExplicitAccountModel::class))
        assertEquals("account_id", ModelMetadataResolver.columnName(ExplicitAccountModel::class, ExplicitAccountModel::id))
        assertEquals("published_id", ModelMetadataResolver.columnAlias(ExplicitAccountModel::id))
    }

    @Test
    fun `empty aliases retain model and column fallback semantics`() {
        assertEquals("no_alias", ModelMetadataResolver.tableName(NoAliasModel::class))
        assertEquals("", ModelMetadataResolver.tableAlias(NoAliasModel::class))
        assertEquals("id", ModelMetadataResolver.columnAlias(NoAliasModel::id))
    }

    @Test
    fun `field resolution does not validate an unrelated empty field`() {
        assertEquals("", ModelMetadataResolver.tableAlias(EmptyTableNameModel::class))
        assertFailsWith<IllegalArgumentException> {
            ModelMetadataResolver.tableName(EmptyTableNameModel::class)
        }

        assertEquals("invalidColumn", ModelMetadataResolver.columnAlias(EmptyTableNameModel::invalidColumn))
        assertFailsWith<IllegalArgumentException> {
            ModelMetadataResolver.columnName(EmptyTableNameModel::class, EmptyTableNameModel::invalidColumn)
        }
    }

    @Test
    fun `required metadata rejects missing annotations and empty names`() {
        assertFailsWith<IllegalArgumentException> { ModelMetadataResolver.requireTable(MissingTableModel::class) }
        assertFailsWith<IllegalArgumentException> { ModelMetadataResolver.tableName(MissingTableModel::class) }
        assertFailsWith<IllegalArgumentException> {
            ModelMetadataResolver.requireColumn(MissingColumnModel::class, MissingColumnModel::unannotated)
        }
        assertFailsWith<IllegalArgumentException> {
            ModelMetadataResolver.requireTable(EmptyTableNameModel::class)
        }
        assertFailsWith<IllegalArgumentException> {
            ModelMetadataResolver.requireColumn(EmptyTableNameModel::class, EmptyTableNameModel::invalidColumn)
        }
    }

    @Test
    fun `inherited property metadata is read from the supplied property reference`() {
        assertEquals(
            "inherited_code",
            ModelMetadataResolver.columnName(DerivedInheritedModel::class, InheritedPropertyModel::code),
        )
        assertEquals("ic", ModelMetadataResolver.columnAlias(InheritedPropertyModel::code))
    }
}
