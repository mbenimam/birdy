package me.birdy.config;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Declares the container-managed MySQL connection pool used by Jakarta Persistence.
 *
 * <p>The {@code ${ENV=...}} placeholders are resolved by the application server from environment
 * variables at deployment time, so no connection detail is ever stored in the application.
 *
 * <p>{@code fallbackToSystemKeyStore=false}: the server JVM has a default key store (its own TLS identity). Without
 * this setting the MySQL driver would present that certificate as a client certificate and MySQL would drop the
 * TLS handshake. Connections stay encrypted whenever the MySQL server offers TLS.
 */
@DataSourceDefinition(
        name = DataSourceConfig.JNDI_NAME,
        className = "com.mysql.cj.jdbc.MysqlDataSource",
        url = "${ENV=BIRDY_DB_URL}",
        user = "${ENV=BIRDY_DB_USER}",
        password = "${ENV=BIRDY_DB_PASSWORD}",
        initialPoolSize = 2,
        minPoolSize = 2,
        maxPoolSize = 20,
        properties = {"fallbackToSystemKeyStore=false"})
@ApplicationScoped
public class DataSourceConfig {

    public static final String JNDI_NAME = "java:app/jdbc/birdy";
}
