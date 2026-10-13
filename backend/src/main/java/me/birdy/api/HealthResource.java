package me.birdy.api;

import com.mongodb.client.MongoDatabase;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import me.birdy.auth.Public;
import me.birdy.config.DataSourceConfig;
import org.bson.Document;

/** Liveness of the application and of its two stores. */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Public
public class HealthResource {

    private static final Logger LOG = System.getLogger(HealthResource.class.getName());
    private static final int TIMEOUT_SECONDS = 2;
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    /** Body of the health endpoint. */
    public record Health(String status, String mysql, String mongo) {
    }

    @Resource(lookup = DataSourceConfig.JNDI_NAME)
    private DataSource dataSource;

    @Inject
    private MongoDatabase mongo;

    @GET
    public Response health() {
        boolean mysqlUp = mysqlUp();
        boolean mongoUp = mongoUp();
        boolean up = mysqlUp && mongoUp;
        Health body = new Health(up ? UP : DOWN, mysqlUp ? UP : DOWN, mongoUp ? UP : DOWN);
        return Response.status(up ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE).entity(body).build();
    }

    private boolean mysqlUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(TIMEOUT_SECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "MySQL health check failed: {0}", e.toString());
            return false;
        }
    }

    private boolean mongoUp() {
        try {
            mongo.withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "MongoDB health check failed: {0}", e.toString());
            return false;
        }
    }
}
