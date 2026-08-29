package edu.seu.vcampus.server.shop.demo;

import java.nio.file.Path;

/** Command-line entry point for the local authenticated buyer Shop demo. */
public final class ShopAuthDemoServerMain {
    private static final Path DEFAULT_DATABASE =
            Path.of("vcampus-database/demo/vcampus-shop-auth-demo.accdb");
    private static final Path DEFAULT_SCHEMA_DIRECTORY = Path.of("vcampus-database/schema");
    private static final Path DEFAULT_SEED_DIRECTORY = Path.of("vcampus-database/seed");
    private static final int DEFAULT_PORT = 19090;

    private ShopAuthDemoServerMain() {
    }

    public static void main(String[] args) throws Exception {
        Path database = Path.of(args.length > 0 ? args[0] : DEFAULT_DATABASE.toString())
                .toAbsolutePath().normalize();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        Path schemas = Path.of(args.length > 2 ? args[2] : DEFAULT_SCHEMA_DIRECTORY.toString());
        Path seeds = Path.of(args.length > 3 ? args[3] : DEFAULT_SEED_DIRECTORY.toString());

        ShopAuthDemoDatabase.initialize(database, schemas, seeds);
        ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, port);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> close(runtime), "shop-auth-demo-shutdown"));
        System.out.println("Shop authenticated buyer demo server started");
        System.out.println("Database: " + database);
        System.out.println("Port: " + runtime.localPort());
        System.out.println("Demo login: DEMO_BUYER");
        runtime.await();
    }

    private static void close(ShopAuthDemoRuntime runtime) {
        try {
            runtime.close();
        } catch (Exception error) {
            System.err.println("Shop demo server cleanup failed: " + error.getMessage());
        }
    }
}
