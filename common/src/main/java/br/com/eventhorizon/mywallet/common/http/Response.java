package br.com.eventhorizon.mywallet.common.http;

import br.com.eventhorizon.mywallet.common.common.ErrorCategory;
import br.com.eventhorizon.mywallet.common.common.Error;
import br.com.eventhorizon.mywallet.common.common.StatusCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response {

    @NonNull
    StatusCode statusCode;

    Object data;

    ResponseError error;

    public static Response success() {
        return new Response(StatusCode.SUCCESS, null, null);
    }

    public static <T> Response success(T data) {
        return new Response(StatusCode.SUCCESS, data, null);
    }

    public static Response error(ErrorCategory errorCategory, Error error) {
        return new Response(StatusCode.ERROR, null,
                ResponseError.of(errorCategory.getValue(), error.getCode(), error.getMessage(), error.getExtraDetails()));
    }

    public static Response error(ErrorCategory errorCategory, String errorCode, String errorMessage) {
        return new Response(StatusCode.ERROR, null,
                ResponseError.of(errorCategory.getValue(), errorCode, errorMessage, null));
    }

    public static Response error(ErrorCategory errorCategory, String errorCode, String errorMessage, String extraDetails) {
        return new Response(StatusCode.ERROR, null,
                ResponseError.of(errorCategory.getValue(), errorCode, errorMessage, extraDetails));
    }
}
