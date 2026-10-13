package me.birdy.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import me.birdy.it.Api.Account;
import org.junit.jupiter.api.Test;

/** Many authenticated requests at once: every one must succeed and be answered for the user who sent it. */
class ConcurrencyIT {

    private static final int ACCOUNTS = 8;
    private static final int THREADS = 16;
    private static final int ROUNDS_PER_THREAD = 60;
    private static final int READERS = 10;
    private static final int READS_PER_READER = 4_000;

    @Test
    void concurrentRequestsAllSucceedAndAreAnsweredForTheirOwnUser() throws Exception {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            accounts.add(Api.newAccount("load" + i));
        }
        String sharedPost = Api.newPost(accounts.getFirst(), "everybody likes this");
        Queue<String> problems = new ConcurrentLinkedQueue<>();
        AtomicInteger requests = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                Account self = accounts.get(thread % ACCOUNTS);
                Account other = accounts.get((thread + 1) % ACCOUNTS);
                workers.add(pool.submit(() -> {
                    for (int round = 0; round < ROUNDS_PER_THREAD; round++) {
                        exercise(self, other, sharedPost, round, problems, requests);
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get(5, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(), List.copyOf(problems), () -> problems.size() + " of " + requests.get()
                + " requests failed; server log:\n" + BirdyStack.get().serverExceptions());
        assertEquals(THREADS * ROUNDS_PER_THREAD * 7, requests.get());
        for (Account account : accounts) {
            assertEquals(1, Api.get("/api/users/me", account.token()).object().getInt("followingCount"),
                    "every account ends up following exactly its neighbour");
        }
        assertEquals(ACCOUNTS, Api.get("/api/posts/" + sharedPost, accounts.getFirst().token()).object()
                .getInt("likeCount"));
    }

    /**
     * Read-only load. With the persistence layer built on Payara's Jakarta Data repositories, about one request in
     * 10,000 to 40,000 of this test failed with "Attempting to execute an operation on a closed EntityManager".
     */
    @Test
    void concurrentAuthenticatedReadsNeverFail() throws Exception {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < READERS / 2; i++) {
            accounts.add(Api.newAccount("read" + i));
        }
        Queue<String> problems = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(READERS);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int thread = 0; thread < READERS; thread++) {
                Account self = accounts.get(thread % accounts.size());
                workers.add(pool.submit(() -> {
                    for (int i = 0; i < READS_PER_READER; i++) {
                        ApiResponse me = Api.get("/api/users/me", self.token());
                        if (me.status() != 200) {
                            problems.add("GET me answered " + me.status() + " " + me.body());
                        } else if (!self.username().equals(me.object().getString("username"))) {
                            problems.add("GET me answered for another user: " + me.body());
                        }
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get(10, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(), List.copyOf(problems), () -> problems.size() + " of " + READERS * READS_PER_READER
                + " requests failed; server log:\n" + BirdyStack.get().serverExceptions());
    }

    @Test
    void simultaneousSignupsWithTheSameUsernameCreateExactlyOneAccount() throws Exception {
        String username = Api.uniqueName("race");
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<ApiResponse>> attempts = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String email = username + "-" + i + "@example.org";
                attempts.add(pool.submit(() -> Api.post("/api/users", null, Api.signupBody(username, email))));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<ApiResponse> attempt : attempts) {
                ApiResponse response = attempt.get(2, TimeUnit.MINUTES);
                statuses.add(response.status());
                if (response.status() != 201) {
                    assertEquals("conflict", response.error(), () -> BirdyStack.get().serverExceptions());
                }
            }
            assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
            assertEquals(THREADS - 1, statuses.stream().filter(status -> status == 409).count(), statuses.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(201, Api.login(username, Api.PASSWORD).status());
    }

    /** One round of reads and writes on behalf of {@code self}; anything unexpected is recorded. */
    private static void exercise(Account self, Account other, String sharedPost, int round, Queue<String> problems,
            AtomicInteger requests) {
        ApiResponse me = call(requests, problems, 200, Api.get("/api/users/me", self.token()), "GET me");
        if (me.status() == 200 && (me.object().getJsonNumber("id").longValue() != self.id()
                || !self.username().equals(me.object().getString("username"))
                || !self.email().equals(me.object().getString("email")))) {
            problems.add("GET me answered for another user: expected " + self.username() + ", got " + me.body());
        }

        ApiResponse profile = call(requests, problems, 200,
                Api.get("/api/users/" + other.username(), self.token()), "GET profile");
        if (profile.status() == 200 && profile.object().getJsonNumber("id").longValue() != other.id()) {
            problems.add("GET profile returned the wrong user: " + profile.body());
        }

        call(requests, problems, 204, Api.put("/api/users/me/following/" + other.username(), self.token()), "follow");
        call(requests, problems, 200, Api.get("/api/users/" + self.username() + "/following", self.token()),
                "GET following");
        call(requests, problems, 204, Api.put("/api/posts/" + sharedPost + "/like", self.token()), "like");

        ApiResponse created = call(requests, problems, 201,
                Api.post("/api/posts", self.token(), Api.content(self.username() + " round " + round)), "POST post");
        if (created.status() == 201
                && !self.username().equals(created.object().getJsonObject("author").getString("username"))) {
            problems.add("POST post was attributed to another user: " + created.body());
        }

        call(requests, problems, 200, Api.get("/api/posts?feed=home&limit=5", self.token()), "GET feed");
    }

    private static ApiResponse call(AtomicInteger requests, Queue<String> problems, int expectedStatus,
            ApiResponse response, String what) {
        requests.incrementAndGet();
        if (response.status() != expectedStatus) {
            problems.add(what + " answered " + response.status() + " " + response.body());
        }
        return response;
    }
}
