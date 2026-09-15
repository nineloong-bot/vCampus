package edu.seu.vcampus.server.shop.composition;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.shop.order.OrderSchemaInitializer;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
/** Installs all additive commerce structures from the authoritative SQL resources. */
public final class CommerceSchemaInitializer {
 private final Path schema;
 /** Accepts the schema directory containing the numbered commerce scripts. */
 public CommerceSchemaInitializer(Path schema){this.schema=schema;}
 /** Preserves historical rows and makes repeated startup idempotent. */
 public void initialize(ConnectionProvider connections)throws IOException,SQLException{
  for(String file:new String[]{"052_shop_catalog.sql","053_shop_orders.sql","054_shop_governance.sql"})
   new OrderSchemaInitializer(schema.resolve(file)).initialize(connections);
 }
}
