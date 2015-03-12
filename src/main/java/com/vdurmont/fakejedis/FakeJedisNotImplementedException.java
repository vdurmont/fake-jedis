package com.vdurmont.fakejedis;

public class FakeJedisNotImplementedException extends UnsupportedOperationException {
    public FakeJedisNotImplementedException() {
        super("The method " + getCurrentMethodName() + " is not implemented in your version of FakeJedis. Contribute on github! https://github.com/vdurmont/fake-jedis");
    }

    private static String getCurrentMethodName() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        if (elements.length < 4) {
            return "\"unknown\"";
        }
        return elements[3].toString();
    }
}
