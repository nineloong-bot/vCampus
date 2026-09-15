package edu.seu.vcampus.server.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resource resolution shared by the application schema initializer segments. */
abstract class ApplicationSchemaInitializerBase {
    private final Path resourceRoot;

    /** Creates an initializer for a root that contains {@code schema/} and {@code seed/}. */
    ApplicationSchemaInitializerBase(Path resourceRoot) {
        this.resourceRoot = Objects.requireNonNull(resourceRoot, "resourceRoot").toAbsolutePath().normalize();
    }

    Path schema(String name) throws IOException {
        return required(resourceRoot.resolve("schema").resolve(name));
    }

    Path seed(String name) throws IOException {
        return required(resourceRoot.resolve("seed").resolve(name));
    }

    private static Path required(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Database resource is missing: " + file);
        }
        return file;
    }
}
