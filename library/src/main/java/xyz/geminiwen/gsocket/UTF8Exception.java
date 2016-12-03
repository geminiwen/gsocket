package xyz.geminiwen.gsocket;

import java.io.IOException;

/**
 * Created by geminiwen on 2016/12/3.
 */

public class UTF8Exception extends IOException {

    public UTF8Exception() {
        super();
    }

    public UTF8Exception(String message) {
        super(message);
    }

    public UTF8Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public UTF8Exception(Throwable cause) {
        super(cause);
    }
}
