package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

/** Restart-safe installation of additive order tables and expiry index. */
public final class OrderSchemaInitializer {
    private final Path script;
    /** Selects the authoritative 053_shop_orders.sql migration. */
    public OrderSchemaInitializer(Path script){this.script=script;}
    /** Creates missing structures while preserving existing order history. */
    public void initialize(ConnectionProvider connections) throws IOException,SQLException {
        var create=Pattern.compile("(?is)^\\s*CREATE TABLE (\\w+)");
        var index=Pattern.compile("(?is)^\\s*CREATE INDEX (\\w+) ON (\\w+)");
        try(var c=connections.open()) {
            var tables=new HashSet<String>();
            try(var r=c.getMetaData().getTables(null,null,null,new String[]{"TABLE"})) {
                while(r.next())tables.add(r.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            for(String sql:Files.readString(script).replace("\uFEFF", "").split(";")) {
                if(sql.isBlank())continue;
                var table=create.matcher(sql);var key=index.matcher(sql);
                if(table.find() && tables.contains(table.group(1).toLowerCase(Locale.ROOT)))continue;
                if(key.find()) {
                    boolean exists=false;
                    try(var r=c.getMetaData().getIndexInfo(null,null,key.group(2),false,false)) {
                        // UCanAccess prefixes persisted index names with the table when reloading Access.
                        String persistedName=key.group(2)+"_"+key.group(1);
                        while(r.next()) {
                            String name=r.getString("INDEX_NAME");
                            if(key.group(1).equalsIgnoreCase(name)||persistedName.equalsIgnoreCase(name))exists=true;
                        }
                    }
                    if(exists)continue;
                }
                try(var statement=c.createStatement()){statement.execute(sql);}
            }
        }
    }
}
