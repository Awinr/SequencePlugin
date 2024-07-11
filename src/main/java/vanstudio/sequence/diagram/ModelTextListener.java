package vanstudio.sequence.diagram;

import java.util.EventListener;

/**
 * 事件监听器接口
 * 定义处理事件的逻辑
 */
public interface ModelTextListener extends EventListener {

    public void modelTextChanged(ModelTextEvent mte);
}

