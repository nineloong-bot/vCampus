import java.nio.file.Files;
import java.nio.file.Path;

/** Revalidates a candidate or a server-startup copy against the preserved snapshots. */
public final class VerifyMainCommerceData {
    /** Arguments: main source candidate [manifest], or --after-startup main candidate. */
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--after-startup")) {
            CommerceDataChecks.verifyNoncommerce(Path.of(args[1]), Path.of(args[2]));
            System.out.println("PASS: main noncommerce exact; current commerce references and wallet reconcile");
            return;
        }
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Expected main source candidate [manifest]");
        }
        String report = CommerceDataChecks.verify(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        if (args.length == 4) {
            Files.writeString(Path.of(args[3]), report);
        }
        System.out.println("PASS: exact main noncommerce and source commerce records; accounts; wallet; references");
    }
}
