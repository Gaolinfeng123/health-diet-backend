package com.healthdiet.config;

import com.healthdiet.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 这个注解表示它专门用来捕获 Controller 抛出的异常
public class GlobalExceptionHandler {

    // 捕获参数校验异常 (就是刚才 @Valid 抛出来的)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        // 获取注解里写的 message (比如 "餐点类型必须是 1~4")
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();
        return Result.error(msg);
    }

    // 捕获其他未知异常 (兜底)
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace(); // 在控制台打印报错详情，方便开发调试
        return Result.error("系统错误：" + e.getMessage());
    }
}
