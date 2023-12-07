package com.chat.base.net;


public class ResponseThrowable extends Exception {

    private int status;
    private String msg;
    private String errJson;

    public ResponseThrowable(Throwable throwable, int code) {
        super(throwable);
        this.status = code;
    }

    public int getCode() {
        return status;
    }

    @Override
    public String getMessage() {
        return msg;
    }

    public void setMessage(String message) {
        this.msg = message;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErrJson() {
        return errJson;
    }

    public void setErrJson(String errJson) {
        this.errJson = errJson;
    }

    @Override
    public String toString() {
        return "ResponeThrowable{" +
                "code=" + status +
                ", message='" + msg + '\'' +
                '}';
    }
}