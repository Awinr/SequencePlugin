package vanstudio.sequence.generator.testEventObject;

import java.util.EventListener;

public interface MyCustomEventListener extends EventListener {
    void handleEvent(MyCustomEvent event);
}
