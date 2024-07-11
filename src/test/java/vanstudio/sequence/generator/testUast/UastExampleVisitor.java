package vanstudio.sequence.generator.testUast;

import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class UastExampleVisitor extends AbstractUastVisitor {
    @Override
    public boolean visitClass(UClass node) {
        System.out.println("Visiting class: " + node.getName());
        return super.visitClass(node);
    }

    @Override
    public boolean visitMethod(UMethod node) {
        System.out.println("Visiting method: " + node.getName());
        return super.visitMethod(node);
    }

    @Override
    public boolean visitVariable(UVariable node) {
        System.out.println("Visiting variable: " + node.getName());
        return super.visitVariable(node);
    }

    @Override
    public boolean visitCallExpression(UCallExpression node) {
        System.out.println("Visiting call expression: " + node.getMethodName());
        return super.visitCallExpression(node);
    }

    @Override
    public boolean visitReturnExpression(UReturnExpression node) {
        System.out.println("Visiting return expression");
        return super.visitReturnExpression(node);
    }

    @Override
    public boolean visitBinaryExpression(UBinaryExpression node) {
        System.out.println("Visiting binary expression: " + node.getOperator().getText());
        return super.visitBinaryExpression(node);
    }
}
