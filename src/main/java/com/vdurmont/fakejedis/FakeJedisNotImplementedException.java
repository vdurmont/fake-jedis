package com.vdurmont.fakejedis;

/**
 * This exception is thrown when a Jedis method is called but hasn't been implemented in fake-jedis
 *
 * @author Vincent DURMONT [vdurmont@gmail.com]
 */
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
