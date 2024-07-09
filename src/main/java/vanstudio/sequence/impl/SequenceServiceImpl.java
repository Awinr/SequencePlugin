package vanstudio.sequence.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import vanstudio.sequence.SequencePanel;
import vanstudio.sequence.SequenceService;

/**
 * 时序服务
 */
public class SequenceServiceImpl implements SequenceService {

    private final Project _project;
    private final ToolWindow _toolWindow;

    public SequenceServiceImpl(Project project) {
        _project = project;
        _toolWindow = ToolWindowManager.getInstance(_project).getToolWindow(PLUGIN_NAME);

    }

    /**
     * 显示某个 PsiElement 的时序图
     * @param psiElement
     */
    @Override
    public void showSequence(@NotNull PsiElement psiElement) {
        // 创建时序图面板
        final SequencePanel sequencePanel = new SequencePanel(_project, psiElement);
        final Content content = addSequencePanel(sequencePanel);
        //生成中title为：Generate...  生成完成后注册回调，更新内容标题。
        sequencePanel.withFinishedListener(content::setDisplayName);

        Runnable postAction = sequencePanel::generate;
        if (_toolWindow.isActive())
            _toolWindow.show(postAction);
        else
            _toolWindow.activate(postAction);
    }

    private Content addSequencePanel(final SequencePanel sequencePanel) {
        ContentManager contentManager = _toolWindow.getContentManager();
        final Content content = contentManager.getFactory().createContent(sequencePanel, sequencePanel.getTitleName(), false);
        contentManager.addContent(content);
        // 将刚刚添加的 Content 对象设置为当前选中的内容，使其在工具窗口中显示为激活状态。即展示当前内容
        contentManager.setSelectedContent(content);
        return content;
    }

}
