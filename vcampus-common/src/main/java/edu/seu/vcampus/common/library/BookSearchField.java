package edu.seu.vcampus.common.library;

import java.io.Serializable;

/** Book metadata field matched by a keyword search. */
public enum BookSearchField implements Serializable {
    /** Represents any. */ ANY, /** Represents title. */ TITLE, /** Represents author. */ AUTHOR, /** Represents isbn. */ ISBN, /** Represents category. */ CATEGORY, /** Represents publisher. */ PUBLISHER
}
