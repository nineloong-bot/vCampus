package edu.seu.vcampus.common.protocol;

import edu.seu.vcampus.common.error.ErrorDetail;

import java.io.Serial;
import java.io.Serializable;

/**
 * Typed success or failure body carried by a response message.
 * @param <T> successful response data type
 * @param success whether the request succeeded
 * @param code stable protocol result code
 * @param message user-facing result message
 * @param data successful response data
 * @param error structured failure detail
 */
public record ResponseBody<T extends Serializable>(
        boolean success,
        String code,
        String message,
        T data,
        ErrorDetail error) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a successful response.
     * @param data successful response data
     * @param <T> response data type
     * @return successful response body
     */
    public static <T extends Serializable> ResponseBody<T> success(T data) {
        return new ResponseBody<>(true, "SUCCESS", "成功", data, null);
    }

    /**
     * Creates a failed response.
     * @param code stable failure code
     * @param message user-facing failure message
     * @param error structured failure detail
     * @param <T> expected response data type
     * @return failed response body
     */
    public static <T extends Serializable> ResponseBody<T> failure(
            String code, String message, ErrorDetail error) {
        return new ResponseBody<>(false, code, message, null, error);
    }
}
