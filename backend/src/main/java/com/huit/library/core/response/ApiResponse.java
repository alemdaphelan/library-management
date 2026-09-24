package com.huit.library.core.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String errorDetails;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Thành công", data, null);
    }

    public static <T> ApiResponse<T> error(int code, String message, String errorDetails) {
        return new ApiResponse<>(code, message, null, errorDetails);
    }
}
