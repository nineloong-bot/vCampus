import edu.seu.vcampus.server.bootstrap.ApplicationSchemaInitializer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.shop.composition.CommerceSchemaInitializer;
import edu.seu.vcampus.server.wallet.WalletSchemaInitializer;
import java.nio.file.*;
import java.sql.*;

/** 创建新的测试库并导入合成数据；拒绝覆盖任何已有数据库。 */
class BuildDataset {
    public static void main(String[] args) throws Exception {
        Path target = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(target)) throw new IllegalArgumentException("Target already exists: " + target);
        Files.createDirectories(target.getParent());
        String url = "jdbc:ucanaccess://" + target + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        Path resourceRoot = Path.of(args[1]);
        new ApplicationSchemaInitializer(resourceRoot).initialize(provider);
        new WalletSchemaInitializer(resourceRoot.resolve("schema/051_shop_wallet.sql")).initialize(provider);
        new CommerceSchemaInitializer(resourceRoot.resolve("schema")).initialize(provider);
        try (var connection = provider.open()) {
            connection.setAutoCommit(false);
            int count = 0;
            try (var statement = connection.createStatement()) {
                for (String line : Files.readAllLines(Path.of(args[2]))) {
                    if (line.isBlank()) continue;
                    try { statement.execute(line); }
                    catch (SQLException failure) {
                        throw new SQLException("Import failed at statement " + (count+1)
                                + " table " + line.substring(0, Math.min(60,line.length())), failure);
                    }
                    if (++count % 1000 == 0) System.out.println("Imported " + count);
                }
                connection.commit();
                System.out.println("Import committed: " + count);
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }
}
