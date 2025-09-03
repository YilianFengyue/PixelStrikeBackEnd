package org.csu.pixelstrikebackend.lobby.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResponse<T> {
    private final int status;
    private final String message;
    private T data;

    private CommonResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    private CommonResponse(int status, String message,T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }


    public static <T> CommonResponse<T> createForSuccess() {
        return new CommonResponse<T>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getDesc());
    }

    public static <T> CommonResponse<T> createForSuccessMessage(String message) {
        return new CommonResponse<T>(ResponseCode.SUCCESS.getCode(), message);
    }
    public static <T> CommonResponse<T> createForSuccess(T data) {
        return new CommonResponse<T>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getDesc(),data);
    }

    public static <T> CommonResponse<T> createForSuccess(String message,T data) {
        return new CommonResponse<T>(ResponseCode.SUCCESS.getCode(), message,data);
    }

    public static <T> CommonResponse<T> createForError(){
        return new CommonResponse<T>(ResponseCode.ERROR.getCode(), ResponseCode.ERROR.getDesc());
    }

    public static <T> CommonResponse<T> createForError(String message){
        return new CommonResponse<T>(ResponseCode.ERROR.getCode(),message);
    }

    public static <T> CommonResponse<T> createForError(int code,String message){
        return new CommonResponse<T>(code,message);
    }

    @JsonIgnore
    public boolean isSuccess() {
        return this.status == ResponseCode.SUCCESS.getCode();
    }
}
