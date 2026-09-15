package edu.seu.vcampus.common.protocol;

/** Identifies whether a wire message is a request, response, or event. */
public enum MessageType {
    /** Represents request. */ REQUEST,
    /** Represents response. */ RESPONSE,
    /** Represents event. */ EVENT
}
