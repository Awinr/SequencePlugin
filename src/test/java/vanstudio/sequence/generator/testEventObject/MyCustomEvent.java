package vanstudio.sequence.generator.testEventObject;

import java.util.EventObject;

public class MyCustomEvent extends EventObject {
    private String message;

    public MyCustomEvent(Object source, String message) {
        super(source);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
