package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** A selectable classroom and its maximum occupancy. */
public record ClassroomView(String classroom, int capacity) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
