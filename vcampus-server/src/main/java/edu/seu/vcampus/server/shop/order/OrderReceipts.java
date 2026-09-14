package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.OrderResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

final class OrderReceipts {
    String key(String actor,String request,String action) {
        OrderSql.require(actor!=null && !actor.isBlank() && actor.length()<=36
                && request!=null && !request.isBlank() && request.length()<=100,"ORDER_INVALID_REQUEST");
        return actor+":"+action+":"+request;
    }
    String digest(Object payload) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    OrderResult replay(Connection c,String key,String digest) throws SQLException {
        var rows=OrderSql.list(c,"SELECT requestDigest,orderIds FROM tblShopOrderReceipt WHERE receiptKey=?",
                r->new String[]{r.getString(1),r.getString(2)},key);
        if(rows.isEmpty()) return null;
        OrderSql.require(rows.getFirst()[0].equals(digest),"ORDER_REQUEST_CONFLICT");
        try(var in=new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(rows.getFirst()[1])))) {
            return (OrderResult)in.readObject();
        } catch(IOException | ClassNotFoundException error){throw new IllegalStateException("Order receipt unavailable",error);}
    }
    void save(Connection c,String key,String digest,OrderResult result) throws SQLException {
        try(var bytes=new ByteArrayOutputStream();var out=new ObjectOutputStream(bytes)) {
            out.writeObject(result);out.flush();
            OrderSql.update(c,"INSERT INTO tblShopOrderReceipt (receiptKey,requestDigest,orderIds) VALUES (?,?,?)",
                    key,digest,Base64.getEncoder().encodeToString(bytes.toByteArray()));
        } catch(IOException error){throw new IllegalStateException("Order receipt unavailable",error);}
    }
}
