package edu.seu.vcampus.server.bootstrap;

import java.nio.file.Path;

/**
 * Installs the common, user, role seed, and course database resources in dependency order.
 *
 * <p>Script parsing, seed idempotency checks and the ordered installation itself are
 * implemented by the package-private segment chain this class extends, keeping the
 * public constructor and {@code initialize} entry point unchanged.</p>
 */
public final class ApplicationSchemaInitializer extends ApplicationSchemaInitializerLifecycle {

    /** Creates an initializer for a root that contains {@code schema/} and {@code seed/}. */
    public ApplicationSchemaInitializer(Path resourceRoot) {
        super(resourceRoot);
    }
}
