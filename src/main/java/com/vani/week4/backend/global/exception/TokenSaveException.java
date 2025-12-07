package com.vani.week4.backend.global.exception;

import com.vani.week4.backend.global.ErrorCode;
import lombok.Getter;

/**
 * @author vani
 * @since 10/13/25
 */
@Getter
public class TokenSaveException extends RuntimeException{

    private final ErrorCode errorCode;

    public TokenSaveException(ErrorCode errorCode) {

        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public TokenSaveException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
