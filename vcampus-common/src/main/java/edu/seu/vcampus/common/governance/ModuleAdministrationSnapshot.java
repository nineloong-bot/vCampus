package edu.seu.vcampus.common.governance;

import java.io.Serializable;
import java.util.List;

/** Immutable snapshot used by the super-administrator permission page. */
public record ModuleAdministrationSnapshot(
        List<ModuleAdministratorView> administrators
) implements Serializable {
    /** Defensively copies the administrator rows. */
    public ModuleAdministrationSnapshot {
        administrators = List.copyOf(administrators);
    }
}
