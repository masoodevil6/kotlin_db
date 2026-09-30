package gog.my_project.data_base.migration.executor.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MySqlCreateTableColumnDefinitionParserTest {
    @Test
    fun preservesQuotedAndNestedColumnDefinitionText() {
        val create = """CREATE TABLE `users` (
  `amount` decimal(10,2) NOT NULL DEFAULT 0,
  `display_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'New user, here' COMMENT 'display (name)',
  `full_name` varchar(255) GENERATED ALWAYS AS (concat(`first`, ',', `last`)) STORED,
  `odd``name` enum('a,b', 'c') NOT NULL DEFAULT 'a,b',
  PRIMARY KEY (`amount`)
) ENGINE=InnoDB"""

        assertEquals(
            " varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'New user, here' COMMENT 'display (name)'",
            MySqlCreateTableColumnDefinitionParser.definitionSuffix(create, "display_name"),
        )
        assertEquals(
            " varchar(255) GENERATED ALWAYS AS (concat(`first`, ',', `last`)) STORED",
            MySqlCreateTableColumnDefinitionParser.definitionSuffix(create, "full_name"),
        )
        assertEquals(
            " enum('a,b', 'c') NOT NULL DEFAULT 'a,b'",
            MySqlCreateTableColumnDefinitionParser.definitionSuffix(create, "odd`name"),
        )
    }

    @Test
    fun rejectsMissingColumnAndMalformedCreateStatement() {
        val create = "CREATE TABLE `users` (`id` int NOT NULL)"
        assertFailsWith<IllegalArgumentException> {
            MySqlCreateTableColumnDefinitionParser.definitionSuffix(create, "missing")
        }
        assertFailsWith<IllegalArgumentException> {
            MySqlCreateTableColumnDefinitionParser.definitionSuffix("not create table", "id")
        }
        assertFailsWith<IllegalArgumentException> {
            MySqlCreateTableColumnDefinitionParser.definitionSuffix("CREATE TABLE `users` (`id` int", "id")
        }
        assertFailsWith<IllegalArgumentException> {
            MySqlCreateTableColumnDefinitionParser.definitionSuffix(
                "CREATE TABLE `users` (`id` int, `id` bigint)",
                "id",
            )
        }
    }
}
