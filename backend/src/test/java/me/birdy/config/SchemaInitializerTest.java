package me.birdy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaInitializerTest {

    @Test
    void splitsOnSemicolonsAndDropsCommentLines() {
        String script = """
                -- header; with a semicolon in the comment
                CREATE TABLE a (id INT);

                  -- indented comment
                CREATE TABLE b (
                  id INT
                );
                """;

        assertEquals(List.of("CREATE TABLE a (id INT)", "CREATE TABLE b (\n  id INT\n)"),
                SchemaInitializer.statements(script));
    }

    @Test
    void packagedSchemaCreatesTheThreeTablesOnlyIfMissing() throws IOException {
        String script;
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(SchemaInitializer.SCRIPT)) {
            assertNotNull(in, "db/schema.sql must be packaged at " + SchemaInitializer.SCRIPT);
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<String> statements = SchemaInitializer.statements(script);

        assertEquals(3, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS users"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE IF NOT EXISTS user_sessions"));
        assertTrue(statements.get(2).startsWith("CREATE TABLE IF NOT EXISTS follows"));
    }
}
