package edu.seu.vcampus.server.shop.port;

/** Adapter boundary for Foundation session and authorization services. */
public interface ShopUserPort {
    ShopUser requireUser(String sessionToken);

    ShopUser requireAdministrator();
}
