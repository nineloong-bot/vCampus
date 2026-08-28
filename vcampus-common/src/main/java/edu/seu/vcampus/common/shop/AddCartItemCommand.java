package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record AddCartItemCommand(String skuId, int quantity) implements Serializable { }
