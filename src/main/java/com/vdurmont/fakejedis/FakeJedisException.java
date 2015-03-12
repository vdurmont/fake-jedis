package com.vdurmont.fakejedis;

/**
 * Exception thrown when something bad happen ;)
 *
 * @author Vincent DURMONT [vdurmont@gmail.com]
 */
public class FakeJedisException extends RuntimeException {
    public FakeJedisException(String msg) {
        super(msg);
    }

    public FakeJedisException(String msg, Throwable t) {
        super(msg, t);
    }
}
