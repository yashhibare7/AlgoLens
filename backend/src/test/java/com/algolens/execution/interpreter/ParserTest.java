package com.algolens.execution.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParserTest {

    @Test
    @DisplayName("accepts a bare statement snippet with no class wrapper")
    void parsesSnippet() {
        Ast.Program program = Parser.parse("""
                int[] arr = {3, 1, 2};
                int total = 0;
                for (int i = 0; i < arr.length; i++) {
                    total += arr[i];
                }
                """);

        assertThat(program.entryMethod()).isNull();
        assertThat(program.statements()).hasSize(3);
        assertThat(program.methods()).isEmpty();
    }

    @Test
    @DisplayName("accepts a full class with a main method")
    void parsesClassWithMain() {
        Ast.Program program = Parser.parse("""
                public class Main {
                    static int twice(int n) {
                        return n * 2;
                    }

                    public static void main(String[] args) {
                        System.out.println(twice(21));
                    }
                }
                """);

        assertThat(program.className()).isEqualTo("Main");
        assertThat(program.entryMethod()).isEqualTo("main");
        assertThat(program.methods()).hasSize(2);
    }

    @Test
    @DisplayName("accepts statements mixed with helper methods and no class")
    void parsesSnippetWithHelpers() {
        Ast.Program program = Parser.parse("""
                static int square(int n) {
                    return n * n;
                }

                int result = square(7);
                """);

        assertThat(program.entryMethod()).isNull();
        assertThat(program.methods()).hasSize(1);
        assertThat(program.statements()).hasSize(1);
    }

    @Test
    @DisplayName("skips package and import declarations")
    void skipsPackageAndImports() {
        Ast.Program program = Parser.parse("""
                package com.example;

                import java.util.Arrays;

                int x = 1;
                """);

        assertThat(program.statements()).hasSize(1);
    }

    @Test
    @DisplayName("static fields become globals rather than statements")
    void parsesStaticFields() {
        Ast.Program program = Parser.parse("""
                public class Main {
                    static int counter = 5;

                    public static void main(String[] args) {
                        counter++;
                    }
                }
                """);

        assertThat(program.fields()).hasSize(1);
        assertThat(program.fields().get(0).declarators().get(0).name()).isEqualTo("counter");
    }

    @Test
    @DisplayName("distinguishes a declaration from a multiplication that looks like one")
    void distinguishesDeclarationFromExpression() {
        // 'foo * bar;' must not be read as declaring 'bar' of type 'foo'.
        Ast.Program program = Parser.parse("""
                int foo = 2;
                int bar = 3;
                int product = foo * bar;
                """);

        assertThat(program.statements()).hasSize(3);
        assertThat(program.statements().get(2)).isInstanceOf(Ast.VarDecl.class);
    }

    @Test
    @DisplayName("reports the offending line on a syntax error")
    void reportsLineNumbers() {
        assertThatThrownBy(() -> Parser.parse("""
                int a = 1;
                int b = ;
                """))
                .isInstanceOf(SyntaxException.class)
                .hasMessageContaining("Line 2");
    }

    @Test
    @DisplayName("rejects a class with no entry point, naming what is missing")
    void rejectsClassWithoutEntryPoint() {
        assertThatThrownBy(() -> Parser.parse("""
                public class Main {
                    static int a(int n) { return n; }
                    static int b(int n) { return n; }
                }
                """))
                .isInstanceOf(SyntaxException.class)
                .hasMessageContaining("main");
    }

    @Test
    @DisplayName("parses for-each, do-while and the ternary operator")
    void parsesRemainingControlFlow() {
        Ast.Program program = Parser.parse("""
                int[] values = {1, 2, 3};
                int sum = 0;
                for (int value : values) {
                    sum += value;
                }
                int i = 0;
                do {
                    i++;
                } while (i < 3);
                int label = sum > 5 ? 1 : 0;
                """);

        assertThat(program.statements()).hasSize(6);
        assertThat(program.statements().get(2)).isInstanceOf(Ast.ForEach.class);
        assertThat(program.statements().get(4)).isInstanceOf(Ast.DoWhile.class);
    }
}
