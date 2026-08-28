package edu.seu.vcampus.common.protocol;

import edu.seu.vcampus.common.error.ErrorDetail;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSerializationTest {
    @Test
    void roundTripsMessageAndTypedResponse() throws Exception {
        ErrorDetail error = new ErrorDetail("COMMON_FORBIDDEN", "无权限",
                Map.of(), "trace-1", false);
        ResponseBody<String> body = ResponseBody.failure("COMMON_FORBIDDEN",
                "无权限", error);
        Message source = new Message("req-1", MessageType.RESPONSE,
                "TEST", "token", body, 1L);

        byte[] bytes;
        try (var buffer = new ByteArrayOutputStream();
             var out = new ObjectOutputStream(buffer)) {
            out.writeObject(source);
            bytes = buffer.toByteArray();
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            assertThat(in.readObject()).isEqualTo(source);
        }
    }
}
