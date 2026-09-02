package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.common.shop.SellerApplicationView;
import java.awt.Component;
import java.util.Optional;

/** Modal boundary for editing or viewing a seller application. */
@FunctionalInterface
public interface SellerApplicationDialogPort {
    void open(Component parent, Optional<SellerApplicationView> application, Runnable changed);
}
