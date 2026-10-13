package me.birdy.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HealthIT {

    @Test
    void healthIsPublicAndReportsBothStores() {
        ApiResponse response = Api.get("/api/health", null);

        assertEquals(200, response.status());
        assertTrue(response.contentType().startsWith("application/json"));
        assertEquals(Json.createObjectBuilder().add("status", "UP").add("mysql", "UP").add("mongo", "UP").build(),
                response.object());
    }

    @Test
    void healthReportsAStoreThatDoesNotAnswer() throws Exception {
        BirdyStack stack = BirdyStack.get();
        stack.pauseMongo();
        try {
            ApiResponse response = Api.get("/api/health", null);

            assertEquals(503, response.status());
            assertEquals(
                    Json.createObjectBuilder().add("status", "DOWN").add("mysql", "UP").add("mongo", "DOWN").build(),
                    response.object());
        } finally {
            stack.unpauseMongo();
        }

        Instant deadline = Instant.now().plusSeconds(30);
        while (Api.get("/api/health", null).status() != 200 && Instant.now().isBefore(deadline)) {
            Thread.sleep(500);
        }
        assertEquals(200, Api.get("/api/health", null).status(), "the application recovers when the store is back");
    }
}
