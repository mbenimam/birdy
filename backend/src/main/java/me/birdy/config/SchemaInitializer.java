package me.birdy.config;

import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.interceptor.Interceptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Applies db/schema.sql (packaged in the WAR) when the application starts, before anything else uses MySQL.
 * Every statement is {@code CREATE TABLE IF NOT EXISTS}, so an empty database gets its tables and an existing one
 * is left as it is.
 */
@ApplicationScoped
public class SchemaInitializer {

    static final String SCRIPT = "/META-INF/sql/schema.sql";

    private static final Logger LOG = System.getLogger(SchemaInitializer.class.getName());

    @Resource(lookup = DataSourceConfig.JNDI_NAME)
    private DataSource dataSource;

    void onStartup(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) Startup event) {
        List<String> statements = statements(readScript());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not apply the MySQL schema", e);
        }
        LOG.log(Level.INFO, "MySQL schema is in place ({0} statements)", statements.size());
    }

    /** Splits a script into statements: drops {@code --} comment lines, then cuts on semicolons. */
    static List<String> statements(String script) {
        String withoutComments = script.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(sql -> !sql.isEmpty())
                .toList();
    }

    private static String readScript() {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(SCRIPT)) {
            if (in == null) {
                throw new IllegalStateException(SCRIPT + " is missing from the application");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
