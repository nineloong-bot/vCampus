package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationListMode;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.CartItemView;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.OrderStatus;
import edu.seu.vcampus.common.shop.PaidOrderItemView;
import edu.seu.vcampus.common.shop.PaidOrderView;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.ShopAdminSummary;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.shop.ProductManagementSummary;
import edu.seu.vcampus.common.shop.SellerOrderItemView;
import edu.seu.vcampus.common.shop.SellerOrderQuery;
import edu.seu.vcampus.common.shop.SellerOrderView;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.ProductSku;
import edu.seu.vcampus.server.shop.domain.CartItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implements one focused group of Access shop queries and writes. */
abstract class AccessShopRepositorySegment1 extends AccessShopRepositorySegment0 {
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

    @Override
    public SellerApplication insertApplication(
            Connection connection, SellerApplication application) throws Exception {
        String sql = "INSERT INTO tblSellerApplication (applicationId, applicantUserId, shopName, "
                + "description, category, contact, applicationStatement, applicationStatus, reviewReason, reviewerUserId, "
                + "submittedAt, reviewedAt, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                + "contact = ?, applicationStatement = ?, applicationStatus = ?, reviewReason = ?, reviewerUserId = ?, "
                + "submittedAt = ?, reviewedAt = ?, rowVersion = rowVersion + 1 "
                + "WHERE applicationId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, application.shopName());
            statement.setString(2, application.description());
            statement.setString(3, application.category());
            statement.setString(4, application.contact());
            statement.setString(5, application.applicationStatement());
            statement.setString(6, application.status().name());
            statement.setString(7, application.reviewReason());
            statement.setString(8, application.reviewerUserId());
            setInstant(statement, 9, application.submittedAt());
            setInstant(statement, 10, application.reviewedAt());
            statement.setString(11, application.applicationId());
            statement.setLong(12, expectedVersion);
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
        SellerApplicationListMode mode = query.mode() == null
                ? SellerApplicationListMode.PENDING : query.mode();
        if (mode == SellerApplicationListMode.PENDING) {
            sql.append(" AND applicationStatus = 'PENDING' ORDER BY submittedAt DESC, applicationId");
        } else {
            sql.append(" AND applicationStatus IN ('APPROVED', 'REJECTED') ORDER BY reviewedAt DESC, applicationId");
        }
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

    @Override
    public Optional<Shop> findShopByNormalizedName(Connection connection,
            String normalizedShopName) throws Exception {
        return findShop(connection, "normalizedShopName", normalizedShopName);
    }
}
