package vanstudio.sequence.generator.testUast;

public class Example {
    public static void main(String[] args) {
        int a = 5;
        int b = 10;
        int sum = add(a, b);
        System.out.println(sum);
    }

    public static int add(int x, int y) {
        return x + y;
    }
}
