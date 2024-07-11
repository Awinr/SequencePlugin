package vanstudio.sequence.generator.testEventObject;

public class Main {
    public static void main(String[] args) {
        // 创建事件源
        EventSource eventSource = new EventSource();

        // 添加事件监听器
        eventSource.addMyCustomEventListener(new MyCustomEventListener() {
            @Override
            public void handleEvent(MyCustomEvent event) {
                System.out.println("Event received: " + event.getMessage());
            }
        });

        // 触发事件
        eventSource.fireEvent("Hello, World!");
        eventSource.fireEvent("Another event!");
    }
}
