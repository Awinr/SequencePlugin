package vanstudio.sequence.generator.testUast;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UastContextKt;
import org.junit.Test;

public class UastTraversalExample extends LightJavaCodeInsightFixtureTestCase {
    @Test
    public void testUastTraversal() {

        /**
         * UClass：表示类定义 Example
         * UMethod：表示方法定义 main 和 add
         * UVariable：表示变量定义 a, b, sum
         * UCallExpression：表示方法调用 add 和 System.out.println
         * UReturnExpression：表示返回语句
         * UBinaryExpression：表示二元运算 x + y
         */
        String javaCode = "public class Example {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = 5;\n" +
                "        int b = 10;\n" +
                "        int sum = add(a, b);\n" +
                "        System.out.println(sum);\n" +
                "    }\n" +
                "    public static int add(int x, int y) {\n" +
                "        return x + y;\n" +
                "    }\n" +
                "}";

        // 创建PsiFile 提供对源代码的直接表示和操作，包含详细的语法和语义信息
        PsiFile psiFile = myFixture.addFileToProject("Example.java", javaCode);

        // 获取UAST根节点 提供语言无关的统一的抽象语法树表示 UAST 是构建在 PSI 之上的抽象层
        UFile uFile = UastContextKt.toUElement(psiFile, UFile.class);

        if (uFile != null) {
            // 创建并使用访问者遍历UAST
            System.out.println("begin test-----------------------------------------------");
            UastExampleVisitor visitor = new UastExampleVisitor();
            uFile.accept(visitor);
            System.out.println("end test-----------------------------------------------");
        }
    }
}
