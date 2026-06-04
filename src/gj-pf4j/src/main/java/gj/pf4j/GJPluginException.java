package gj.pf4j;

public class GJPluginException extends RuntimeException  {
    public GJPluginException(String message) {
        super(message);
    }

    public GJPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
