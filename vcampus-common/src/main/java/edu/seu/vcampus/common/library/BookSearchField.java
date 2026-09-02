package edu.seu.vcampus.common.library;

import java.io.Serializable;

/** Book metadata field matched by a keyword search. */
public enum BookSearchField implements Serializable {
    ANY, TITLE, AUTHOR, ISBN, CATEGORY, PUBLISHER
}
