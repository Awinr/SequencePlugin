package vanstudio.sequence;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vanstudio.sequence.openapi.ActionFinder;
import vanstudio.sequence.openapi.ElementTypeFinder;

/**
 * 显示序列生成选项对话框。
 */
public class ShowSequenceAction extends AnAction implements DumbAware {

    public ShowSequenceAction() {
    }

    /**
     * 动作启用/禁用状态
     *根据文件类型启用或禁用菜单。当前只有 java 文件才能启用菜单。
     *
     * @param event event
     */
    public void update(@NotNull AnActionEvent event) {
        super.update(event);

        // Presentation对象包含了与用户界面相关的信息，如操作的名称、描述、图标和 启用/禁用开关。
        Presentation presentation = event.getPresentation();

        //Program Structure Interface  PSI 会将代码解析成一个树状结构，其中包含了类、方法、语句等节点。
        @Nullable PsiElement psiElement = event.getData(CommonDataKeys.PSI_FILE);
        presentation.setEnabled(isEnabled(psiElement));

    }

    private boolean isEnabled(PsiElement psiElement) {
        return psiElement != null
                && ActionFinder.isValid(psiElement.getLanguage());
    }

    /**
     * 动作逻辑
     * @param event
     */
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        SequenceService sequenceService = project.getService(SequenceService.class);

        //一个 PsiFile 表示整个文件，而文件中的所有代码元素（如类、方法、字段等）都是 PsiElement。可以通过 PsiFile 访问和操作这些代码元素。
        // 可以是代码中的任何元素，如类、方法、变量等。
        PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);

        // 当前编辑的文件。
        final PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);

        if (psiElement == null) {
            final Caret caret = event.getData(CommonDataKeys.CARET);

            if (psiFile != null && caret != null) {
                // try to find the enclosed PsiMethod / KtFunction of caret
                ElementTypeFinder typeFinder = ElementTypeFinder.EP_NAME.forLanguage(psiFile.getLanguage());
                if (typeFinder != null) {
                    Class<? extends PsiElement> method = typeFinder.findMethod();
                    psiElement = PsiTreeUtil.findElementOfClassAtOffset(psiFile, caret.getOffset(), method, false);

                    // try to get top PsiClass / KtClass
                    if (psiElement == null) {
                        Class<? extends PsiElement> aClass = typeFinder.findClass();
                        psiElement = PsiTreeUtil.findElementOfClassAtOffset(psiFile, caret.getOffset(), aClass, false);
                        if (psiElement != null) {
                            chooseMethodToGenerate(event, sequenceService, psiElement, project);
                            return;
                        }
                    }
                }
            }
        }
        // 如果找到具体的方法，直接生成时序图
        if (psiElement != null) {
            sequenceService.showSequence(psiElement);
        } else {
            // 如果没有找到具体的方法，选择当前文件中的某个方法生成时序图
            if (psiFile != null) {
                chooseMethodToGenerate(event, sequenceService, psiFile, project);
            }
        }
    }

    private void chooseMethodToGenerate(@NotNull AnActionEvent event, SequenceService sequenceService, PsiElement psiElement, @NotNull Project project) {

        // for PsiClass, show popup menu list method to choose
//        AnAction[] list;

        /*
          对于找到的每个PsiElement（PsiMethod/KtFunction），调用｛@code SequenceService.showSequence（PsiElement）｝
         */
        ActionFinder.Task task = (method, myProject) -> sequenceService.showSequence(method);

        /*
          Get {@code ActionMenuFinder} by PsiFile's Language and find all PsiMethod/KtFunction with gaven task.
         */
//        list = ReadAction.compute(() -> {
//            ActionFinder actionFinder = ActionFinder.getInstance(psiElement.getLanguage());
//            if (actionFinder == null) {
//                return AnAction.EMPTY_ARRAY;
//            } else {
//                return actionFinder.find(project, psiElement, task);
//            }
//        });

        /**
         * 异步读取当前文件中的所有方法，避免阻塞 UI 线程
         * todo: 如果我找到了三个方法，然后点击第一个方法，如何根据我的点击操作，去展示对应方法的时序图呢  打断点
         */
        ReadAction.nonBlocking(() -> {
            ActionFinder actionFinder = ActionFinder.getInstance(psiElement.getLanguage());
            if (actionFinder == null) {
                return AnAction.EMPTY_ARRAY;
            } else {
                return actionFinder.find(project, psiElement, task);
            }
        }).inSmartMode(project).finishOnUiThread(ModalityState.defaultModalityState(), list -> {
            ActionGroup actionGroup = new ActionGroup() {
                @Override
                public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
                    return list;
                }
            };

            JBPopupFactory.getInstance().createActionGroupPopup("Choose Method ...", actionGroup, event.getDataContext(),
                    null, false).showInBestPositionFor(event.getDataContext());
        }).submit(NonUrgentExecutor.getInstance());
    }

//    面向 IntelliJ Platform 2022.3 或更高版本时，必须实现AnAction.getActionUpdateThread()
//    @Override
//    public @NotNull ActionUpdateThread getActionUpdateThread() {
//        return ActionUpdateThread.BGT;
//    }
}
