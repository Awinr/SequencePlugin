package vanstudio.sequence.generator.testEventObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 该类可以触发事件并允许添加和移除事件监听器。
 */
public class EventSource {
    private List<MyCustomEventListener> listeners = new ArrayList<>();

    public void addMyCustomEventListener(MyCustomEventListener listener) {
        listeners.add(listener);
    }

    public void removeMyCustomEventListener(MyCustomEventListener listener) {
        listeners.remove(listener);
    }

    public void fireEvent(String message) {
        // 1.定义一个具体事件
        MyCustomEvent event = new MyCustomEvent(this, message);
        for (MyCustomEventListener listener : listeners) {
            // 2.监听器处理事件
            listener.handleEvent(event);
        }
    }
}
