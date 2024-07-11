package vanstudio.sequence.ext.uast;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.Query;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import vanstudio.sequence.config.SequenceSettingsState;
import vanstudio.sequence.generator.filters.ImplementClassFilter;
import vanstudio.sequence.openapi.IGenerator;
import vanstudio.sequence.openapi.SequenceParams;
import vanstudio.sequence.openapi.model.CallStack;
import vanstudio.sequence.openapi.model.MethodDescription;
import vanstudio.sequence.util.MyPsiUtil;
import vanstudio.sequence.util.MyUastUtilKt;

import java.util.ArrayList;
import java.util.List;

/**
 * 通过遍历和分析 UAST 结构生成方法调用序列图
 */
public class UastSequenceGenerator extends AbstractUastVisitor implements IGenerator {
    private static final Logger LOGGER = Logger.getInstance(UastSequenceGenerator.class);

    /**
     * 存储的是与代码元素相关的偏移量,通过偏移量,可以定位到某个元素
     */
    private final Stack<Integer> offsetStack = new Stack<>();

    /**
     * 缓存接口实现类的名称
     */
    private final ArrayList<String> imfCache = new ArrayList<>();

    /**
     * topStackElement 即栈底的元素
     */
    private CallStack topStackElement;
    private CallStack currentStackElement;
    private final SequenceParams params;

    /**
     * 展示 lambda 调用
     */
    private final boolean SHOW_LAMBDA_CALL;

    /**
     * 智能接口
     */
    private final boolean SMART_INTERFACE;

    public UastSequenceGenerator(SequenceParams params) {
        this.params = params;
        SHOW_LAMBDA_CALL = SequenceSettingsState.getInstance().SHOW_LAMBDA_CALL;
        SMART_INTERFACE = SequenceSettingsState.getInstance().SMART_INTERFACE;
    }

    public UastSequenceGenerator(SequenceParams params, int offset) {
        this(params);
        offsetStack.push(offset);
    }

    @Override
    public CallStack generate(PsiElement psiElement, @Nullable CallStack parent) {
        if (parent != null) {
            topStackElement = parent;
            currentStackElement = topStackElement;
        }
        if (psiElement instanceof UMethod) {
            generateMethod((UMethod) psiElement);
        } else {
            // 获取 UAST 文件表示
            UMethod uMethod = UastContextKt.toUElement(psiElement, UMethod.class);
            // generateMethod(uMethod) -> method.accept(this) -> visitMethod(@NotNull UMethod node)
            //  -> makeMethodCallExceptcurrentStackElementIsRecursive(MethodDescription method)
            generateMethod(uMethod);
        }
        // 这里返回的是 main 方法的调用栈 callStack中的_calls已经满了，什么时候满的呢？ 见上
        return topStackElement;
    }

    private void generateLambda(ULambdaExpression node) {
        MethodDescription method = MyUastUtilKt.createMethod(node, MyPsiUtil.findNaviOffset(node.getSourcePsi()));
        makeMethodCallExceptcurrentStackElementIsRecursive(method);
        node.getBody().accept(this);
    }

    /**
     * 生成给定 UMethod 的方法调用序列
     * @param uMethod
     */
    private void generateMethod(UMethod uMethod) {
        // 获取包含该方法的类
        UClass containingUClass = UastUtils.getContainingUClass(uMethod);

        /**
         * 检查该类是否为接口并且不是外部类
         * 如果包含uMethod的类是一个接口的话，会查询该接口的实现类的实现方法去生成调用序列
         */
        if (containingUClass != null && containingUClass.isInterface() && !MyUastUtilKt.isExternal(containingUClass)) {
            // 接受当前对象（Visitor 模式）
            uMethod.accept(this);

            // 获取方法的原始 PsiElement 表示
            PsiElement sourcePsi = uMethod.getSourcePsi();
            if (sourcePsi != null) {
                // 查询该方法的实现
                Query<PsiElement> search = DefinitionsScopedSearch.search(sourcePsi).allowParallelProcessing();

                for (PsiElement psiElement : search) {
                    if (psiElement instanceof PsiMethod) {
                        // 转换为 UMethod
                        UMethod method = UastContextKt.toUElement(psiElement, UMethod.class);

                        // 检查实现是否允许
                        if (method != null && params.getImplementationWhiteList().allow(psiElement)) {
                            // 检查方法是否允许
                            if (params.getMethodFilter().allow(psiElement)) {
                                // 接受当前对象（Visitor 模式） 在accept中，visitor会调用 visitMethod 方法访问 method 对象
                                method.accept(this);
                            }
                        }
                    }
                }
            }
        } else {
            // 如果不是接口，处理变量初始化
            if (SMART_INTERFACE
                    && !MyUastUtilKt.isExternal(containingUClass)
                    && containingUClass != null
                    && !imfCache.contains(containingUClass.getQualifiedName())) {
                // 接受新的 MyImplFinder 对象
                containingUClass.accept(new MyImplFinder());
                // 缓存处理过的类名
                imfCache.add(containingUClass.getQualifiedName());
            }
            // Visitor 模式， uastVisitor 会调用相应的visit方法访问 uMethod 对象
            uMethod.accept(this);
        }
    }

    /**
     * 确保入栈的栈帧不存在递归调用
     * @param newMethod
     * @return
     */
    private boolean makeMethodCallExceptcurrentStackElementIsRecursive(MethodDescription newMethod) {
        //  // 如果 topStackElement 为 null，说明还没有初始化调用栈
        if (topStackElement == null) {
            topStackElement = new CallStack(newMethod);
            currentStackElement = topStackElement;
        } else {
            // // 如果 params.isNotAllowRecursion() 为 true，检查当前调用栈是否已经存在递归调用
            if (params.isNotAllowRecursion() && currentStackElement.isRecursive(newMethod))
                // 如果存在递归调用，则返回 true，不将方法添加到调用栈中
                return true;
            // 新方法入栈， 更新当前栈帧
            currentStackElement = currentStackElement.methodCall(newMethod);
        }
        // 返回 false 表示方法已被成功添加到调用栈中
        return false;
    }

    /**
     * 遵守最大递归深度限制,确保不超出预设的深度限制。
     * @param psiMethod
     * @param offset
     */
    private void methodCall(PsiMethod psiMethod, int offset) {
        if (psiMethod == null) return;
        if (!params.getMethodFilter().allow(psiMethod)) return;

        if (currentStackElement.level() < params.getMaxDepth()) {
            CallStack oldStack = currentStackElement;
            LOGGER.debug("+ depth = " + currentStackElement.level() + " method = " + psiMethod.getName());
            offsetStack.push(offset);
            UMethod uMethod = UastContextKt.toUElement(psiMethod, UMethod.class);
            generateMethod(uMethod);
            LOGGER.debug("- depth = " + currentStackElement.level() + " method = " + psiMethod.getName());
            currentStackElement = oldStack;
        } else {
            UMethod uMethod = UastContextKt.toUElement(psiMethod, UMethod.class);
            if (uMethod != null) currentStackElement.methodCall(MyUastUtilKt.createMethod(uMethod, offset));
        }
    }

    // --------------------------------------------- Visitor 方法--------------------------------------------------- //

    /**
     * UAST 使用访问者模式来允许客户端代码在不修改树结构的情况下操作树节点。
     * 在这种模式中，访问者（即实现了 UastVisitor 的类）访问每个节点，并对其执行特定操作。
     * UAST 遍历是一个系统的过程，它遍历代码树的每个节点，并调用相应的访问方法。
     * 代码遍历过程中，遍历到每一个节点时，可以记录对应方法声明以及方法调用的偏移量，用户可以快速跳转到特定的代码行或方法位置，以便查看或修改代码。
     *
     *
     * UMethod类型的 node：表示方法声明。对应访问方法是 visitMethod。
     * debug发现：对于调用的方法，也会执行 visitMethod
     * @param node
     * @return
     */
    @Override
    public boolean visitMethod(@NotNull UMethod node) {
        int offset = offsetStack.isEmpty() ? MyPsiUtil.findNaviOffset(node.getSourcePsi()) : offsetStack.pop();

        MethodDescription method = MyUastUtilKt.createMethod(node, offset);
        return makeMethodCallExceptcurrentStackElementIsRecursive(method);
//        return super.visitMethod(node);
    }

    /**
     * UAST 遍历是一个系统的过程，它遍历代码树的每个节点，并调用相应的访问方法。
     * 对于 UCallExpression 节点（调用表达式），UastVisitor 会调用 visitCallExpression 方法来处理这个节点
     * 如果参数也是调用表达式的话，会继续递归访问该参数
     * @param node
     * @return
     */
    @Override
    public boolean visitCallExpression(@NotNull UCallExpression node) {
        boolean isComplexCall = false;
        List<UExpression> valueArguments = node.getValueArguments();
        for (UExpression valueArgument : valueArguments) {
            if (valueArgument instanceof UQualifiedReferenceExpression
                    /* || valueArgument instanceof ULambdaExpression*/
                    || valueArgument instanceof UCallExpression
                /* || valueArgument instanceof UCallableReferenceExpression*/) {
                // generate value argument before call expression
                valueArgument.accept(this);
                isComplexCall = true;
            }
        }
        PsiMethod method = node.resolve();
        methodCall(method, MyPsiUtil.findNaviOffset(node.getSourcePsi()));
        return isComplexCall;
    }

    /**
     * ULambdaExpression：表示 Lambda 表达式。对应访问方法是 visitLambdaExpression。
     * @param node
     * @return
     */
    @Override
    public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
        if (SHOW_LAMBDA_CALL) {
            // generate dummy () -> call, and it's body in separate generator
            generateLambda(node);
            //true:  do not need to generate lambda body in this generator.
            return true;
        }
        return super.visitLambdaExpression(node);
    }

    /**
     * 当代码中出现方法或函数的引用时（引用表达式），对应访问方法如下
     * @param node
     * @return
     */
    @Override
    public boolean visitCallableReferenceExpression(@NotNull UCallableReferenceExpression node) {
        // 解析引用的元素
        final PsiElement resolve = node.resolve();
        if (resolve instanceof PsiMethod) {
            final PsiMethod psiMethod = (PsiMethod) resolve;
            final int offset = MyPsiUtil.findNaviOffset(node.getSourcePsi());
            methodCall(psiMethod, offset);
        }
        return super.visitCallableReferenceExpression(node);
    }

    @Override
    public boolean visitDeclaration(@NotNull UDeclaration node) {
        if (SMART_INTERFACE && node instanceof ULocalVariable) {
            ULocalVariable localVariable = (ULocalVariable) node;
            variableImplementationFinder(localVariable.getTypeReference(), localVariable.getUastInitializer());
        }
        return super.visitDeclaration(node);
    }

    /**
     * 表示二元运算
     * @param node
     * @return
     */
    @Override
    public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
        UExpression uExpression = node.getRightOperand();
        if (SMART_INTERFACE && uExpression instanceof UCallExpression) {
            findAssignmentImplFilter(node.getLeftOperand().getExpressionType(), uExpression);
        }
        return super.visitBinaryExpression(node);
    }

    private void variableImplementationFinder(UTypeReferenceExpression typeReference, UExpression uastInitializer) {
        if (typeReference == null || uastInitializer == null) return;

        String face = typeReference.getQualifiedName();

        if (face == null) return;

        if (uastInitializer instanceof UCallExpression) {
            UastCallKind kind = ((UCallExpression) uastInitializer).getKind();
            if (kind.equals(UastCallKind.CONSTRUCTOR_CALL)) {
                PsiType initializerType = uastInitializer.getExpressionType();
                if (initializerType != null) {
                    ArrayList<String> list = new ArrayList<>();

                    String impl = initializerType.getCanonicalText();
                    if (!face.equals(impl)) {
                        list.add(impl);
                    }

                    PsiType[] superTypes = initializerType.getSuperTypes();
                    for (PsiType superType : superTypes) {
                        String superImpl = superType.getCanonicalText();
                        if (!face.equals(superImpl)) {
                            list.add(superImpl);
                        }
                    }

                    if (!list.isEmpty()) {
                        params.getImplementationWhiteList().putIfAbsent(face, new ImplementClassFilter(list.toArray(new String[0])));
                    }
                }
            }
        }


    }

    private void findAssignmentImplFilter(PsiType psiType, UExpression expression) {

        if (expression instanceof UCallExpression) {
            UastCallKind kind = ((UCallExpression) expression).getKind();
            if (kind.equals(UastCallKind.CONSTRUCTOR_CALL)) {
                String face = psiType.getCanonicalText();
                PsiType type = expression.getExpressionType();
                if (type != null) {
                    String impl = type.getCanonicalText();
                    if (!impl.equals(face)) {
                        params.getImplementationWhiteList().putIfAbsent(face, new ImplementClassFilter(impl));
                    }
                }
            }

        }
    }


    /**
     * Find interface -> implementation in assignment
     */
    private class MyImplFinder extends AbstractUastVisitor {
//        @Override
//        public boolean visitClass(@NotNull UClass node) {
//            List<UTypeReferenceExpression> uastSuperTypes = node.getUastSuperTypes();
//            for (UTypeReferenceExpression uastSuperType : uastSuperTypes) {
////                if (!MyUastUtilKt.isExternal(uastSuperType)) {
//                uastSuperType.accept(this);
////                }
//            }
//
//            return node.isInterface() || MyUastUtilKt.isExternal(node);
//        }

        @Override
        public boolean visitField(@NotNull UField node) {
            UTypeReferenceExpression typeReference = node.getTypeReference();
            UExpression uastInitializer = node.getUastInitializer();
            variableImplementationFinder(typeReference, uastInitializer);
            return super.visitField(node);
        }
//
//        @Override
//        public boolean visitMethod(@NotNull UMethod node) {
//            return !node.isConstructor();
//        }

    }
}
