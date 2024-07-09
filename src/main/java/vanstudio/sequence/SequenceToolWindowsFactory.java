package vanstudio.sequence;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import vanstudio.sequence.ui.Welcome;
import org.jetbrains.annotations.NotNull;

/**
 * 当用户单击工具窗口按钮时，将调用工厂类的方法，并初始化工具窗口的 UI
 * 这里生成一个默认的 welcome content
 */
public class SequenceToolWindowsFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        addEmptyContent(project, toolWindow);
    }

    private void addEmptyContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        Welcome welcome = new Welcome();
        ContentManager contentManager = toolWindow.getContentManager();
        Content emptyDiagram = contentManager.getFactory().createContent(welcome.getMainPanel(), "Welcome AAAA", false);
        emptyDiagram.setCloseable(false);
        contentManager.addContent(emptyDiagram);

    }


}
