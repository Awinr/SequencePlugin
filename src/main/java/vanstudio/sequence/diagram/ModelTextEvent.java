package vanstudio.sequence.diagram;

import java.util.EventObject;

/**
 * 定义模型文本事件
 */
public class ModelTextEvent extends EventObject {

    private String _text = null;

    ModelTextEvent(Object source, String text) {
        super(source);
        _text = text;
    }

    public String getText() {
        return _text;
    }
}
