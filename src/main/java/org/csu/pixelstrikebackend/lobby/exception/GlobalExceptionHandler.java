package org.csu.pixelstrikebackend.lobby.exception;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 专门用来处理 @Valid 注解校验失败时抛出的异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    //@ResponseStatus(HttpStatus.BAD_REQUEST) // 仍然保持 HTTP 状态码为 400
    public CommonResponse<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();

        // 从所有错误中提取第一条错误信息作为提示返回给前端
        // String errorMessage = bindingResult.getFieldErrors().get(0).getDefaultMessage();

        // 或者，将所有错误信息拼接起来返回
        String errorMessage = bindingResult.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return CommonResponse.createForError("参数校验失败: " + errorMessage);
    }
}
