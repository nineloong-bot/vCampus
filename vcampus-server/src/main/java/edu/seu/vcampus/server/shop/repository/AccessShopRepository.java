package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for the Access shop tables. */
public final class AccessShopRepository implements ShopRepository {
    @Override
    public Optional<SellerApplication> findApplicationById(
            Connection connection, String applicationId) throws Exception {
        return findApplication(connection, "applicationId", applicationId);
    }

    @Override
    public Optional<SellerApplication> findApplicationByApplicant(
            Connection connection, String applicantUserId) throws Exception {
        return findApplication(connection, "applicantUserId", applicantUserId);
    }

    private Optional<SellerApplication> findApplication(
            Connection connection, String column, String value) throws Exception {
        String sql = "SELECT * FROM tblSellerApplication WHERE " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapApplication(result)) : Optional.empty();
            }
        }
    }

    @Override
    public SellerApplication insertApplication(
            Connection connection, SellerApplication application) throws Exception {
        String sql = "INSERT INTO tblSellerApplication (applicationId, applicantUserId, shopName, "
                + "description, category, contact, applicationStatus, reviewReason, reviewerUserId, "
                + "submittedAt, reviewedAt, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindApplication(statement, application);
            statement.executeUpdate();
            return application;
        }
    }

    @Override
    public SellerApplication updateApplication(Connection connection,
            SellerApplication application, long expectedVersion) throws Exception {
        String sql = "UPDATE tblSellerApplication SET shopName = ?, description = ?, category = ?, "
                + "contact = ?, applicationStatus = ?, reviewReason = ?, reviewerUserId = ?, "
                + "submittedAt = ?, reviewedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE applicationId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, application.shopName());
            statement.setString(2, application.description());
            statement.setString(3, application.category());
            statement.setString(4, application.contact());
            statement.setString(5, application.status().name());
            statement.setString(6, application.reviewReason());
            statement.setString(7, application.reviewerUserId());
            setInstant(statement, 8, application.submittedAt());
            setInstant(statement, 9, application.reviewedAt());
            statement.setString(10, application.applicationId());
            statement.setLong(11, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw invalidApplicationState("Stale seller application version");
            }
        }
        return findApplicationById(connection, application.applicationId()).orElseThrow();
    }

    @Override
    public PageResult<SellerApplication> searchApplications(Connection connection,
            SellerApplicationQuery query) throws Exception {
        if (query.pageNumber() < 0 || query.pageSize() <= 0) {
            throw new IllegalArgumentException("Invalid page");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM tblSellerApplication WHERE 1 = 1");
        List<String> values = new ArrayList<>();
        if (query.applicantUserId() != null && !query.applicantUserId().isBlank()) {
            sql.append(" AND applicantUserId = ?");
            values.add(query.applicantUserId());
        }
        if (query.status() != null) {
            sql.append(" AND applicationStatus = ?");
            values.add(query.status().name());
        }
        sql.append(" ORDER BY applicationId");
        List<SellerApplication> all = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    all.add(mapApplication(result));
                }
            }
        }
        int from = Math.min(query.pageNumber() * query.pageSize(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return new PageResult<>(all.subList(from, to), query.pageNumber(), query.pageSize(), all.size());
    }

    @Override
    public Optional<Shop> findShopById(Connection connection, String shopId) throws Exception {
        return findShop(connection, "shopId", shopId);
    }

    @Override
    public Optional<Shop> findShopByOwner(Connection connection, String ownerUserId) throws Exception {
        return findShop(connection, "ownerUserId", ownerUserId);
    }

    private Optional<Shop> findShop(Connection connection, String column, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblShop WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapShop(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Shop insertShop(Connection connection, Shop shop) throws Exception {
        String sql = "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                + "contact, shopStatus, suspensionReason, suspendedByUserId, suspendedAt, "
                + "rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, shop.shopId());
            statement.setString(2, shop.ownerUserId());
            statement.setString(3, shop.shopName());
            statement.setString(4, shop.description());
            statement.setString(5, shop.category());
            statement.setString(6, shop.contact());
            statement.setString(7, shop.status().name());
            statement.setString(8, shop.suspensionReason());
            statement.setString(9, shop.suspendedByUserId());
            setInstant(statement, 10, shop.suspendedAt());
            statement.setLong(11, shop.rowVersion());
            setInstant(statement, 12, shop.createdAt());
            setInstant(statement, 13, shop.updatedAt());
            statement.executeUpdate();
            return shop;
        }
    }

    @Override
    public Shop updateShopStatus(Connection connection, String shopId,
            ShopStatus expectedStatus, ShopStatus targetStatus, String suspensionReason,
            String suspendedByUserId, Instant suspendedAt, Instant updatedAt,
            long expectedVersion) throws Exception {
        String sql = "UPDATE tblShop SET shopStatus = ?, suspensionReason = ?, suspendedByUserId = ?, "
                + "suspendedAt = ?, updatedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE shopId = ? AND shopStatus = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetStatus.name());
            statement.setString(2, suspensionReason);
            statement.setString(3, suspendedByUserId);
            setInstant(statement, 4, suspendedAt);
            setInstant(statement, 5, updatedAt);
            statement.setString(6, shopId);
            statement.setString(7, expectedStatus.name());
            statement.setLong(8, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ShopException(ShopErrorCode.SHOP_STATUS_INVALID,
                        "Shop status or version does not allow this transition");
            }
        }
        return findShopById(connection, shopId).orElseThrow();
    }

    @Override
    public long countShopsByOwner(Connection connection, String ownerUserId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblShop WHERE ownerUserId = ?")) {
            statement.setString(1, ownerUserId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void bindApplication(PreparedStatement statement,
            SellerApplication application) throws Exception {
        statement.setString(1, application.applicationId());
        statement.setString(2, application.applicantUserId());
        statement.setString(3, application.shopName());
        statement.setString(4, application.description());
        statement.setString(5, application.category());
        statement.setString(6, application.contact());
        statement.setString(7, application.status().name());
        statement.setString(8, application.reviewReason());
        statement.setString(9, application.reviewerUserId());
        setInstant(statement, 10, application.submittedAt());
        setInstant(statement, 11, application.reviewedAt());
        statement.setLong(12, application.rowVersion());
    }

    private static SellerApplication mapApplication(ResultSet result) throws Exception {
        return new SellerApplication(result.getString("applicationId"),
                result.getString("applicantUserId"), result.getString("shopName"),
                result.getString("description"), result.getString("category"),
                result.getString("contact"), SellerApplicationStatus.valueOf(
                        result.getString("applicationStatus")), result.getString("reviewReason"),
                result.getString("reviewerUserId"), instant(result, "submittedAt"),
                instant(result, "reviewedAt"), result.getLong("rowVersion"));
    }

    private static Shop mapShop(ResultSet result) throws Exception {
        return new Shop(result.getString("shopId"), result.getString("ownerUserId"),
                result.getString("shopName"), result.getString("description"),
                result.getString("category"), result.getString("contact"),
                ShopStatus.valueOf(result.getString("shopStatus")),
                result.getString("suspensionReason"), result.getString("suspendedByUserId"),
                instant(result, "suspendedAt"), result.getLong("rowVersion"),
                instant(result, "createdAt"), instant(result, "updatedAt"));
    }

    private static Instant instant(ResultSet result, String column) throws Exception {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value)
            throws Exception {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    private static ShopException invalidApplicationState(String message) {
        return new ShopException(ShopErrorCode.SHOP_SELLER_APPLICATION_STATUS_INVALID, message);
    }
}
