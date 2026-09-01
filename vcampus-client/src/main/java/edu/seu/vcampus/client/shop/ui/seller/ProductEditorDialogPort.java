package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.common.shop.CreateProductCommand;
import edu.seu.vcampus.common.shop.ProductView;
import edu.seu.vcampus.common.shop.UpdateProductCommand;

import java.awt.Component;
import java.util.Optional;

/** Replaceable modal boundary for seller product creation and updates. */
interface ProductEditorDialogPort {
    Optional<CreateProductCommand> create(Component parent, String category);
    Optional<UpdateProductCommand> update(Component parent, ProductView product);
}
