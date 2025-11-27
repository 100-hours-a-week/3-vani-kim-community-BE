package com.vani.week4.backend.global;

import com.vani.week4.backend.global.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author vani
 * @since 10/13/25
 */
@Slf4j      // 로거 이용하기
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e){
        ErrorResponse response = new ErrorResponse("INTERNAL_SERVER_ERROR", "서버가 아파요");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AuthNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuthNotFound(AuthNotFoundException ex){
        ErrorCode code = ex.getErrorCode();

        // Enum 이용하기
        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("권한 없는 접근 발생: code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFoundException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("존재하지 않는 댓글 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("이미 존재하는 이메일 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(FileSizeExceedException.class)
    public ResponseEntity<ErrorResponse> handleFileSizeExceed(FileSizeExceedException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("요청 파일 사이즈 규격 초과 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(InvalidCommentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidComment(InvalidCommentException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("잘못된 댓글 요청 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(InvalidContentTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidContentType(InvalidContentTypeException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("허용되지 않는 컨첸트 타입 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("유효하지 않은 비밀번호 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("유효하지 않은 토큰 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(MaxDepthExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxDepthExceeded(MaxDepthExceededException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("댓글 깊이 초과 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(NicknameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleNicknameAlreadyExists(NicknameAlreadyExistsException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("이미 존재하는 닉네임 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("존재하지 않는 게시글 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(TokenSaveException.class)
    public ResponseEntity<ErrorResponse> handleTokenSave(TokenSaveException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("토큰 저장 중 오류 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("권한 없는 접근 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }
    @ExceptionHandler(UserAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleUserAccessDenied(UserAccessDeniedException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("유저 접근 거부 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }


    @ExceptionHandler(UserDeletedException.class)
    public ResponseEntity<ErrorResponse> handleUserDeletedException(UserDeletedException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("탈퇴한 유저입니다. : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex){
        ErrorCode code = ex.getErrorCode();

        ErrorResponse response = new ErrorResponse(code.getCode(), ex.getMessage());

        log.warn("존재하지 않는 유저 : code={}, msg={}", code.getMessage(), ex.getMessage());

        return new ResponseEntity<>(response, code.getStatus());
    }
}
