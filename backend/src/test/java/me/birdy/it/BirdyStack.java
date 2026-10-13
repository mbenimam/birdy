package me.birdy.it;

import com.github.dockerjava.api.DockerClient;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * The whole system under test, started once for all integration tests: MySQL (initialised with db/schema.sql exactly
 * as in production), MongoDB and the application image built from the project's Dockerfile, on one Docker network.
 */
public final class BirdyStack {

    public static final String MYSQL_IMAGE = "mysql:8.4.11";
    public static final String MONGO_IMAGE = "mongo:8.0.32";

    /** Short enough for the tests to observe the background purge of expired sessions. */
    public static final int SESSION_CLEANUP_SECONDS = 2;

    private static final String DATABASE = "birdy";
    private static final String DB_USER = "birdy";
    private static final int HTTP_PORT = 8080;
    private static final int MONGO_PORT = 27017;

    private static final BirdyStack INSTANCE = new BirdyStack();

    private final MySQLContainer mysql;
    private final GenericContainer<?> mongo;
    private final GenericContainer<?> app;
    private final String dbPassword = UUID.randomUUID().toString();

    private BirdyStack() {
        Network network = Network.newNetwork();
        Path projectDirectory = Path.of(System.getProperty("user.dir"));

        mysql = new MySQLContainer(MYSQL_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("mysql")
                .withDatabaseName(DATABASE)
                .withUsername(DB_USER)
                .withPassword(dbPassword)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(projectDirectory.resolve("db/schema.sql")),
                        "/docker-entrypoint-initdb.d/schema.sql");

        mongo = new GenericContainer<>(MONGO_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("mongo")
                .withExposedPorts(MONGO_PORT)
                .waitingFor(Wait.forListeningPort());

        mysql.start();
        mongo.start();

        app = new GenericContainer<>(
                new ImageFromDockerfile("birdy-backend-it", false)
                        .withDockerfile(projectDirectory.resolve("Dockerfile")))
                .withNetwork(network)
                .withExposedPorts(HTTP_PORT)
                .withEnv("BIRDY_DB_URL", "jdbc:mysql://mysql:3306/" + DATABASE)
                .withEnv("BIRDY_DB_USER", DB_USER)
                .withEnv("BIRDY_DB_PASSWORD", dbPassword)
                .withEnv("BIRDY_MONGO_URI", "mongodb://mongo:" + MONGO_PORT)
                .withEnv("BIRDY_MONGO_DB", DATABASE)
                .withEnv("BIRDY_SESSION_TTL_HOURS", "1")
                .withEnv("BIRDY_SESSION_CLEANUP_SECONDS", Integer.toString(SESSION_CLEANUP_SECONDS))
                .waitingFor(Wait.forHttp("/api/health").forPort(HTTP_PORT).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(5));
        app.start();
    }

    public static BirdyStack get() {
        return INSTANCE;
    }

    public URI httpUri(String path) {
        return URI.create("http://" + app.getHost() + ":" + app.getMappedPort(HTTP_PORT) + path);
    }

    public URI webSocketUri(String path) {
        return URI.create("ws://" + app.getHost() + ":" + app.getMappedPort(HTTP_PORT) + path);
    }

    /** A direct connection to the MySQL database of the stack, to inspect or prepare rows. */
    public Connection jdbc() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), DB_USER, dbPassword);
    }

    /** The distinct exception lines of the application server log, to explain unexpected answers. */
    public String serverExceptions() {
        return app.getLogs().lines()
                .filter(line -> line.contains("Exception") && !line.startsWith("\tat "))
                .map(String::strip)
                .distinct()
                .limit(20)
                .collect(Collectors.joining("\n"));
    }

    public void pauseMongo() {
        docker().pauseContainerCmd(mongo.getContainerId()).exec();
    }

    public void unpauseMongo() {
        docker().unpauseContainerCmd(mongo.getContainerId()).exec();
    }

    private static DockerClient docker() {
        return DockerClientFactory.instance().client();
    }
}
