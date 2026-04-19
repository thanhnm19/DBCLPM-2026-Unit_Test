package com.example.schedule_service.dto;

import lombok.*;

@Getter
@Setter
public class Response<T> {
    private int statusCode;
    private String error;
    private Object message;
    private T data;

    // Explicit accessors to avoid compilation issues when Lombok annotation
    // processing is not enabled
    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
