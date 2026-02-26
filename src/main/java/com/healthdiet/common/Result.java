package com.healthdiet.common;

import lombok.Data;

@Data // Lombok 注解，自动帮我们生成 get/set 方法
public class Result<T> {
    private Integer code; // 200表示成功，500表示失败
    private String msg;   // 提示信息，比如"登录成功"
    private T data;       // 具体数据

    // 成功的快捷方法
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    // 失败的快捷方法
    public static <T> Result<T> error(String msg) {
        Result<T> r = new Result<>();
        r.code = 500;
        r.msg = msg;
        return r;
    }
}