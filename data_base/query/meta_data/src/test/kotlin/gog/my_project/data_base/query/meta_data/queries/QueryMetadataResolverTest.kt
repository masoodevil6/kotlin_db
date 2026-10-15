package gog.my_project.data_base.query.meta_data.queries

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class ConventionUserQuery

@QBQuery
private class SentinelReportQuery

@QBQuery(name = "account_lookup", alias = "al")
private class ExplicitAccountQuery

@QBQuery(alias = "")
private class EmptyAliasQuery

@QBQuery(name = "")
private class EmptyNameQuery

private class QueryOutputProperties {
    @QBQuerySelect(name = "logical_full_name", alias = "sql_full_name")
    val fullName: String get() = error("Metadata resolution must not invoke the getter")

    @QBQuerySelect(name = "logical_only", alias = "")
    val emptyPublishedAlias: String get() = error("Metadata resolution must not invoke the getter")

    @QBQuerySelect(name = "", alias = "published_only")
    val emptyLogicalName: String get() = error("Metadata resolution must not invoke the getter")

    @QBQuerySelect
    val sentinelOutput: String get() = error("Metadata resolution must not invoke the getter")

    val conventionOutput: String get() = error("Metadata resolution must not invoke the getter")
}

class QueryMetadataResolverTest {

    @Test
    fun `query name and alias use class convention when annotation is absent or unset`() {
        assertEquals("convention_user", QueryMetadataResolver.queryName(ConventionUserQuery::class))
        assertEquals("convention_user", QueryMetadataResolver.queryAlias(ConventionUserQuery::class))
        assertEquals("sentinel_report", QueryMetadataResolver.queryName(SentinelReportQuery::class))
        assertEquals("sentinel_report", QueryMetadataResolver.queryAlias(SentinelReportQuery::class))
    }

    @Test
    fun `query name and alias read explicit annotation values independently`() {
        assertEquals("account_lookup", QueryMetadataResolver.queryName(ExplicitAccountQuery::class))
        assertEquals("al", QueryMetadataResolver.queryAlias(ExplicitAccountQuery::class))
    }

    @Test
    fun `query name resolution does not validate an empty alias`() {
        assertEquals("empty_alias", QueryMetadataResolver.queryName(EmptyAliasQuery::class))
        val error = assertFailsWith<IllegalArgumentException> {
            QueryMetadataResolver.queryAlias(EmptyAliasQuery::class)
        }
        assertEquals("@QBQuery on ${EmptyAliasQuery::class.qualifiedName} must declare a non-empty query alias", error.message)
    }

    @Test
    fun `query alias resolution does not validate an empty name`() {
        assertEquals("empty_name", QueryMetadataResolver.queryAlias(EmptyNameQuery::class))
        val error = assertFailsWith<IllegalArgumentException> {
            QueryMetadataResolver.queryName(EmptyNameQuery::class)
        }
        assertEquals("@QBQuery on ${EmptyNameQuery::class.qualifiedName} must declare a non-empty query name", error.message)
    }

    @Test
    fun `logical output name and published alias resolve independently`() {
        assertEquals("logical_full_name", QueryMetadataResolver.queryLogicalOutputName(QueryOutputProperties::fullName))
        assertEquals("sql_full_name", QueryMetadataResolver.queryPublishedAlias(QueryOutputProperties::fullName))

        assertEquals(
            "logical_only",
            QueryMetadataResolver.queryLogicalOutputName(QueryOutputProperties::emptyPublishedAlias),
        )
        val emptyAliasError = assertFailsWith<IllegalArgumentException> {
            QueryMetadataResolver.queryPublishedAlias(QueryOutputProperties::emptyPublishedAlias)
        }
        assertEquals(
            "@QBQuerySelect on emptyPublishedAlias must declare a non-empty SQL alias",
            emptyAliasError.message,
        )

        assertEquals(
            "published_only",
            QueryMetadataResolver.queryPublishedAlias(QueryOutputProperties::emptyLogicalName),
        )
        val emptyNameError = assertFailsWith<IllegalArgumentException> {
            QueryMetadataResolver.queryLogicalOutputName(QueryOutputProperties::emptyLogicalName)
        }
        assertEquals(
            "@QBQuerySelect on emptyLogicalName must declare a non-empty logical name",
            emptyNameError.message,
        )
    }

    @Test
    fun `unset and absent output metadata use property convention`() {
        assertEquals("sentinel_output", QueryMetadataResolver.queryLogicalOutputName(QueryOutputProperties::sentinelOutput))
        assertEquals("sentinel_output", QueryMetadataResolver.queryPublishedAlias(QueryOutputProperties::sentinelOutput))
        assertEquals("convention_output", QueryMetadataResolver.queryLogicalOutputName(QueryOutputProperties::conventionOutput))
        assertEquals("convention_output", QueryMetadataResolver.queryPublishedAlias(QueryOutputProperties::conventionOutput))
    }
}
