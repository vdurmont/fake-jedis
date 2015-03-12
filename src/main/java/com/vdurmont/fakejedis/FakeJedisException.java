package com.vdurmont.fakejedis;

public class FakeJedisException extends RuntimeException {
    public FakeJedisException(String msg) {
        super(msg);
    }

    public FakeJedisException(String msg, Throwable t) {
        super(msg, t);
    }
}
