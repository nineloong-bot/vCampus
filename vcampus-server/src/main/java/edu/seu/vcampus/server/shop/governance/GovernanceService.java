package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Transactional governance operations; identity must originate from the session adapter. */
public final class GovernanceService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;
    private final GovernanceApplications applications=new GovernanceApplications();
    private final GovernanceQueries queries=new GovernanceQueries();
    private final GovernanceQualifications qualifications;
    private final GovernanceCases cases;
    private final GovernanceActions actions;
    private final GovernancePolicy policy;
    /** Shares the transaction boundary and uses commerce-specific resource locks. */
    public GovernanceService(TransactionManager transactions,ResourceLockManager locks,Clock clock,GovernanceEffects effects) {
        this.transactions=transactions;
        this.locks=locks;
        this.clock=clock;
        policy=new GovernancePolicy(clock);
        qualifications=new GovernanceQualifications(clock,policy,effects);
        cases=new GovernanceCases(clock);
        actions=new GovernanceActions(effects,clock);
    }
    /** Current policy shared with catalog and purchase validation. */
    public GovernancePolicy policy() { return policy; }
    /** Reconciles expiry using the same transactional boundary as mutations. */
    public void maintain() {
        locks.withLocks(List.of(new ResourceKey("SHOP_GOV","GLOBAL")),()->transactions.inTransaction(c->{qualifications.expire(c);return null;}));
    }
    /** Executes an authenticated typed request; writes have durable content-bound receipts. */
    public Serializable execute(ShopUser user,String command,Serializable body,String requestId) {
        if(user==null||!user.active()) throw new SecurityException("Active account required");
        if(List.of("REVIEW_APPLICATION","REVIEW_QUALIFICATION","REVIEW_CASE","ACTION","AUDIT","APPLICATIONS").contains(command)) administrator(user);
        return locks.withLocks(List.of(new ResourceKey("SHOP_GOV","GLOBAL")),()->transactions.inTransaction(c->{
            qualifications.expire(c);
            if(List.of("SELF","APPLICATIONS","SHOP","QUALIFICATIONS","CASES","AUDIT").contains(command))
                return read(c,user,command,body);
            String key=text(user.userId(),36)+":"+text(requestId,100);
            String hash=fingerprint(command,body);
            var previous=rows(c,"SELECT fingerprint,resultId FROM tblShopGovReceipt WHERE receiptKey=?",key);
            if(!previous.isEmpty()) {
                if(!hash.equals(previous.getFirst().get(0))) throw new IllegalArgumentException("Request identity reused with different content");
                return receipt(previous.getFirst().get(1));
            }
            String result=write(c,user,command,body);
            update(c,"INSERT INTO tblShopGovReceipt VALUES (?,?,?)",key,hash,result);
            return receipt(result);
        }));
    }
    private Serializable read(Connection c,ShopUser user,String command,Serializable body) throws SQLException {
        if("SELF".equals(command)) {
            if(!(body instanceof edu.seu.vcampus.common.protocol.EmptyRequest)) throw new IllegalArgumentException("Empty request required");
            var shops=queries.shops(c,user.userId());
            return shops.items().isEmpty()?queries.applications(c,user.userId()):shops;
        }
        Query q=require(body,Query.class);
        if(!List.of("SELF","ADMIN","SHOP").contains(q.scope())) throw new IllegalArgumentException("Invalid scope");
        boolean admin="ADMIN".equals(q.scope());
        if(admin) administrator(user);
        return switch(command) {
            case "APPLICATIONS" -> { administrator(user); yield queries.applications(c,null); }
            case "SHOP" -> queries.shops(c,admin?null:user.userId());
            case "QUALIFICATIONS" -> queries.qualifications(c,admin?null:ownedShop(c,user.userId()));
            case "CASES" -> queries.cases(c,admin?null:"SHOP".equals(q.scope())?null:user.userId(),"SHOP".equals(q.scope())?ownedShop(c,user.userId()):null);
            case "AUDIT" -> { administrator(user); yield GovernanceAudit.list(c,q.objectId()); }
            default -> throw new IllegalArgumentException("Invalid query");
        };
    }
    private String write(Connection c,ShopUser user,String command,Serializable body) throws SQLException {
        String actor=user.userId();
        return switch(command) {
            case "APPLY" -> {
                if(!user.sellerEligible()) throw new SecurityException("Student or teacher required");
                String id=applications.apply(c,actor,require(body,Apply.class),clock.instant());
                GovernanceAudit.record(c,actor,id,"APPLY","Platform rules accepted",clock.instant(),"","PENDING",id);
                yield id;
            }
            case "REVIEW_APPLICATION" -> { administrator(user); yield applications.review(c,actor,require(body,Review.class),clock.instant()); }
            case "SETTINGS" -> {
                String shop=ownedShop(c,actor);
                String description=require(body,Settings.class).description();
                if(description==null||description.length()>4000) throw new IllegalArgumentException("Invalid introduction");
                update(c,"UPDATE tblShop SET description=?,updatedAt=?,rowVersion=rowVersion+1 WHERE shopId=?",description,clock.instant(),shop);
                GovernanceAudit.record(c,actor,shop,"SETTINGS","Store introduction updated",clock.instant(),"","",null);
                yield shop;
            }
            case "SUBMIT_QUALIFICATION" -> {
                String shop=ownedShop(c,actor);
                String id=qualifications.submit(c,shop,require(body,Qualification.class));
                GovernanceAudit.record(c,actor,shop,"SUBMIT_QUALIFICATION","Text qualification submitted",clock.instant(),"","PENDING",id);
                yield id;
            }
            case "REVIEW_QUALIFICATION" -> { administrator(user); yield qualifications.review(c,actor,require(body,Review.class)); }
            case "SUBMIT_CASE" -> cases.submit(c,actor,require(body,SubmitCase.class));
            case "REVIEW_CASE" -> { administrator(user); yield cases.review(c,actor,require(body,Review.class)); }
            case "ACTION" -> { administrator(user); yield actions.apply(c,actor,require(body,Action.class)); }
            default -> throw new IllegalArgumentException("Unknown command");
        };
    }
    private static String ownedShop(Connection c,String actor) throws SQLException {
        String shop=scalar(c,"SELECT shopId FROM tblShop WHERE ownerUserId=?",actor);
        if(shop==null) throw new SecurityException("Shop owner required");
        return shop;
    }
    private static void administrator(ShopUser user) {
        if(user.kind()!=ShopUserKind.ADMINISTRATOR) throw new SecurityException("Administrator required");
    }
    private static <T> T require(Object body,Class<T> type) {
        if(!type.isInstance(body)) throw new IllegalArgumentException("Invalid request body");
        return type.cast(body);
    }
    private static View receipt(String id) { return new View(id,"RECEIPT",id,"COMPLETED","","","","","",null); }
    private static String fingerprint(String command,Object body) {
        try {
            var buffer=new java.io.ByteArrayOutputStream();
            try(var output=new java.io.ObjectOutputStream(buffer)) {
                output.writeUTF(command);
                output.writeObject(body);
            }
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray());
            return java.util.HexFormat.of().formatHex(bytes);
        } catch(java.security.NoSuchAlgorithmException | java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
