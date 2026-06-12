import java.util.*;

public class MiniCompilador {

    enum TipoToken {
        PALABRA_CLAVE, IDENTIFICADOR, NUMERO, OPERADOR, DELIMITADOR, EOF
    }

    static class Token {
        String valor;
        TipoToken tipo;

        Token(String valor, TipoToken tipo) {
            this.valor = valor;
            this.tipo = tipo;
        }

        public String toString() {
            return "<" + valor + ", " + tipo + ">";
        }
    }

    static class Simbolo {
        String nombre;
        String tipo;
        boolean inicializado;

        Simbolo(String nombre, String tipo, boolean inicializado) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.inicializado = inicializado;
        }
    }

    static ArrayList<String> erroresLexicos = new ArrayList<>();
    static ArrayList<String> erroresSintacticos = new ArrayList<>();
    static ArrayList<String> erroresSemanticos = new ArrayList<>();
    static HashMap<String, Simbolo> tabla = new HashMap<>();

    // ANALIZADOR LEXICO
    static ArrayList<Token> lexer(String codigo) {
        ArrayList<Token> tokens = new ArrayList<>();

        String simbolos = "()+-*/=;";
        for (char c : simbolos.toCharArray()) {
            codigo = codigo.replace("" + c, " " + c + " ");
        }

        String[] partes = codigo.split("\\s+");

        for (String p : partes) {
            if (p.isEmpty()) continue;

            if (p.equals("int") || p.equals("print")) {
                tokens.add(new Token(p, TipoToken.PALABRA_CLAVE));
            } else if (p.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                tokens.add(new Token(p, TipoToken.IDENTIFICADOR));
            } else if (p.matches("[0-9]+")) {
                tokens.add(new Token(p, TipoToken.NUMERO));
            } else if (p.matches("[+\\-*/=]")) {
                tokens.add(new Token(p, TipoToken.OPERADOR));
            } else if (p.matches("[();]")) {
                tokens.add(new Token(p, TipoToken.DELIMITADOR));
            } else {
                erroresLexicos.add("Error léxico: símbolo desconocido -> " + p);
            }
        }

        tokens.add(new Token("EOF", TipoToken.EOF));
        return tokens;
    }

    // ANALIZADOR SINTACTICO
    static class Parser {
        ArrayList<Token> tokens;
        int pos = 0;

        Parser(ArrayList<Token> tokens) {
            this.tokens = tokens;
        }

        Token actual() {
            return tokens.get(pos);
        }

        void avanzar() {
            if (pos < tokens.size() - 1) pos++;
        }

        boolean aceptar(String valor) {
            if (actual().valor.equals(valor)) {
                avanzar();
                return true;
            }
            return false;
        }

        void analizar() {
            while (actual().tipo != TipoToken.EOF) {
                sentencia();
            }
        }

        void sentencia() {
            if (actual().valor.equals("int")) {
                avanzar();

                if (actual().tipo != TipoToken.IDENTIFICADOR) {
                    erroresSintacticos.add("Error sintáctico: se esperaba identificador después de int");
                    avanzar();
                    return;
                }

                avanzar();

                if (!aceptar(";")) {
                    erroresSintacticos.add("Error sintáctico: falta ';' en declaración");
                    recuperar();
                }

            } else if (actual().tipo == TipoToken.IDENTIFICADOR) {
                avanzar();

                if (!aceptar("=")) {
                    erroresSintacticos.add("Error sintáctico: orden incorrecto en asignación");
                    recuperar();
                    return;
                }

                expresion();

                if (!aceptar(";")) {
                    erroresSintacticos.add("Error sintáctico: falta ';'");
                    recuperar();
                }

            } else if (actual().valor.equals("print")) {
                avanzar();

                if (!aceptar("(")) {
                    erroresSintacticos.add("Error sintáctico: falta '(' en print");
                }

                expresion();

                if (!aceptar(")")) {
                    erroresSintacticos.add("Error sintáctico: falta ')' en print");
                }

                if (!aceptar(";")) {
                    erroresSintacticos.add("Error sintáctico: falta ';'");
                    recuperar();
                }

            } else {
                erroresSintacticos.add("Error sintáctico: token inesperado -> " + actual().valor);
                avanzar();
            }
        }

        void expresion() {
            termino();

            while (actual().valor.equals("+") || actual().valor.equals("-")) {
                avanzar();
                termino();
            }
        }

        void termino() {
            factor();

            while (actual().valor.equals("*") || actual().valor.equals("/")) {
                avanzar();
                factor();
            }
        }

        void factor() {
            if (actual().tipo == TipoToken.IDENTIFICADOR ||
                actual().tipo == TipoToken.NUMERO) {
                avanzar();
            } else if (aceptar("(")) {
                expresion();

                if (!aceptar(")")) {
                    erroresSintacticos.add("Error sintáctico: paréntesis no balanceados");
                }
            } else {
                erroresSintacticos.add("Error sintáctico: falta expresión");
                avanzar();
            }
        }

        void recuperar() {
            while (!actual().valor.equals(";") && actual().tipo != TipoToken.EOF) {
                avanzar();
            }

            if (actual().valor.equals(";")) {
                avanzar();
            }
        }
    }

    // ANALIZADOR SEMANTICO
    static void semantico(ArrayList<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);

            if (t.valor.equals("int")) {
                Token id = tokens.get(i + 1);

                if (id.tipo == TipoToken.IDENTIFICADOR) {
                    tabla.put(id.valor, new Simbolo(id.valor, "int", false));
                }
            }

            else if (t.tipo == TipoToken.IDENTIFICADOR) {
                if (i + 1 < tokens.size() && tokens.get(i + 1).valor.equals("=")) {
                    String nombre = t.valor;

                    if (!tabla.containsKey(nombre)) {
                        erroresSemanticos.add("Error: variable no declarada -> " + nombre);
                    } else {
                        revisarExpresion(tokens, i + 2);
                        tabla.get(nombre).inicializado = true;
                    }
                }
            }

            else if (t.valor.equals("print")) {
                revisarExpresion(tokens, i + 2);
            }
        }
    }

    static void revisarExpresion(ArrayList<Token> tokens, int inicio) {
        for (int i = inicio; i < tokens.size(); i++) {
            Token t = tokens.get(i);

            if (t.valor.equals(";") || t.valor.equals(")")) {
                break;
            }

            if (t.tipo == TipoToken.IDENTIFICADOR) {
                if (!tabla.containsKey(t.valor)) {
                    erroresSemanticos.add("Error: variable no declarada -> " + t.valor);
                } else if (!tabla.get(t.valor).inicializado) {
                    erroresSemanticos.add("Error: variable no inicializada -> " + t.valor);
                }
            }
        }
    }

    public static void main(String[] args) {

        String codigo = """
                int x;
                int z;
                print(x + z);
                """;

        ArrayList<Token> tokens = lexer(codigo);

        System.out.println("CODIGO:");
        System.out.println(codigo);

        System.out.println("TOKENS:");
        for (Token t : tokens) {
            if (t.tipo != TipoToken.EOF) {
                System.out.println(t);
            }
        }

        if (!erroresLexicos.isEmpty()) {
            System.out.println("\nERRORES LEXICOS:");
            for (String e : erroresLexicos) System.out.println(e);
            return;
        }

        Parser parser = new Parser(tokens);
        parser.analizar();

        if (!erroresSintacticos.isEmpty()) {
            System.out.println("\nERRORES SINTACTICOS:");
            for (String e : erroresSintacticos) System.out.println(e);
            return;
        } else {
            System.out.println("\nSintaxis correcta.");
        }

        semantico(tokens);

        if (!erroresSemanticos.isEmpty()) {
            System.out.println("\nERRORES SEMANTICOS:");
            for (String e : erroresSemanticos) System.out.println(e);
        } else {
            System.out.println("\nSemántica correcta.");
        }

        System.out.println("\nTABLA DE SIMBOLOS:");
        for (Simbolo s : tabla.values()) {
            System.out.println(s.nombre + " | " + s.tipo + " | inicializado: " + s.inicializado);
        }
    }
}