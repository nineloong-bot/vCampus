package edu.seu.vcampus.common.protocol;

import edu.seu.vcampus.common.error.ErrorDetail;

import java.io.Serial;
import java.io.Serializable;

/** Typed success or failure body carried by a response message. */
public record ResponseBody<T extends Serializable>(
        boolean success,
        String code,
        String message,
        T data,
        ErrorDetail error) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Creates a successful response. */
    public static <T extends Serializable> ResponseBody<T> success(T data) {
        return new ResponseBody<>(true, "SUCCESS", "成功", data, null);
    }

    /** Creates a failed response. */
    public static <T extends Serializable> ResponseBody<T> failure(
            String code, String message, ErrorDetail error) {
        return new ResponseBody<>(false, code, message, null, error);
    }
}
