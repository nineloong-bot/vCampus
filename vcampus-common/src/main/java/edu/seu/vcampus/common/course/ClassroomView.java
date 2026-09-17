package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/** One active classroom selectable by a course administrator. */
public record ClassroomView(String classroom, int capacity,
                            boolean sharedSportsVenue) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
