package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSkuView;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopSummary;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Objects;

/** Buyer-facing catalog and storefront queries. */
public final class ShopService {
    private static final int MAX_CATALOG_PAGE_SIZE = 100;
    private static final long MAX_CATALOG_PAGE_OFFSET = 10_000_000L;
    private final ShopRepository repository;
    private final TransactionManager transactions;

    public ShopService(ShopRepository repository, TransactionManager transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public PageResult<ProductSummary> getHomeProducts(HomeProductQuery query) {
        Objects.requireNonNull(query, "query");
        return searchProducts(new ProductSearchQuery(null, null, query.minPrice(), query.maxPrice(),
                ProductSortMode.SALES_DESC, query.pageNumber(), query.pageSize()));
    }

    public PageResult<ProductSummary> searchProducts(ProductSearchQuery query) {
        validate(query.minPrice(), query.maxPrice(), query.pageNumber(), query.pageSize());
        return transactions.inTransaction(connection -> repository.searchCatalog(connection, query, null));
    }

    public ProductDetail getProduct(String productId) {
        SellerApplicationService.requireId(productId, "productId");
        return transactions.inTransaction(connection -> {
            Product product = repository.findProductById(connection, productId)
                    .orElseThrow(() -> SellerApplicationService.error(
                            ShopErrorCode.SHOP_PRODUCT_INACTIVE, "Product does not exist"));
            Shop shop = requireVisibleShop(connection, product.shopId());
            if (product.status() != ProductStatus.ACTIVE) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_PRODUCT_INACTIVE,
                        "Product is inactive");
            }
            return new ProductDetail(product.productId(), product.productName(), product.category(),
                    product.description(), product.coverImageUrl(), product.status(), product.salesCount(),
                    new ShopSummary(shop.shopId(), shop.shopName()),
                    repository.findSkusByProduct(connection, product.productId()).stream()
                            .filter(sku -> sku.active() && sku.availableQuantity() > 0)
                            .map(ProductService::toSkuView).toList(), product.createdAt());
        });
    }

    public ShopDetail getShop(String shopId) {
        SellerApplicationService.requireId(shopId, "shopId");
        return transactions.inTransaction(connection -> toDetail(requireVisibleShop(connection, shopId)));
    }

    public PageResult<ProductSummary> getShopProducts(ShopProductQuery query) {
        Objects.requireNonNull(query, "query");
        SellerApplicationService.requireId(query.shopId(), "shopId");
        validate(query.minPrice(), query.maxPrice(), query.pageNumber(), query.pageSize());
        return transactions.inTransaction(connection -> {
            requireVisibleShop(connection, query.shopId());
            ProductSearchQuery catalog = new ProductSearchQuery(query.keyword(), query.category(),
                    query.minPrice(), query.maxPrice(), query.sortMode(),
                    query.pageNumber(), query.pageSize());
            return repository.searchCatalog(connection, catalog, query.shopId());
        });
    }

    private Shop requireVisibleShop(Connection connection, String shopId) throws Exception {
        Shop shop = repository.findShopById(connection, shopId)
                .orElseThrow(() -> SellerApplicationService.error(
                        ShopErrorCode.SHOP_NOT_FOUND, "Shop does not exist"));
        if (shop.status() == ShopStatus.SUSPENDED) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SUSPENDED,
                    "Shop is suspended");
        }
        return shop;
    }

    private static ShopDetail toDetail(Shop shop) {
        return new ShopDetail(shop.shopId(), shop.shopName(), shop.description(),
                shop.category(), shop.contact(), shop.status());
    }

    private static void validate(BigDecimal minPrice, BigDecimal maxPrice,
            int pageNumber, int pageSize) {
        if ((minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_PRICE_FILTER_INVALID,
                    "Price range is invalid");
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_CATALOG_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_CATALOG_PAGE_SIZE);
        }
        long offset = (long) pageNumber * (long) pageSize;
        if (offset > MAX_CATALOG_PAGE_OFFSET) {
            throw new IllegalArgumentException(
                    "page offset must not exceed " + MAX_CATALOG_PAGE_OFFSET);
        }
    }
}
