package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record AdminChangeProductStatusCommand(String shopId,
        ChangeProductStatusCommand command) implements Serializable { }
