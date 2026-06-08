package com.aitrics.vitalmonitoring.interfaces.api;

import com.aitrics.vitalmonitoring.domain.exception.OptimisticLockConflictException;
import com.aitrics.vitalmonitoring.domain.exception.PatientAlreadyExistsException;
import com.aitrics.vitalmonitoring.domain.exception.PatientNotFoundException;
import com.aitrics.vitalmonitoring.interfaces.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePatientNotFound(PatientNotFoundException e) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND.value(), e.getMessage());
    }

    @ExceptionHandler(PatientAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handlePatientAlreadyExists(PatientAlreadyExistsException e) {
        return ErrorResponse.of(HttpStatus.CONFLICT.value(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLockConflict(OptimisticLockConflictException e) {
        return ErrorResponse.of(HttpStatus.CONFLICT.value(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "입력값이 올바르지 않습니다.", errors);
    }

    // JPA @Version 충돌이 트랜잭션 커밋 시점에 전파된 경우의 안전망
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleJpaOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ErrorResponse.of(HttpStatus.CONFLICT.value(), "동시 수정으로 인한 충돌이 발생했습니다. 다시 시도해주세요.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception e) {
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 오류가 발생했습니다.");
    }
}
