package org.example;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.gen.cssLexer;
import org.gen.cssParser;

public class Main {
	private static void handleFileErrors() {
		System.err.println("fatal error: couldn't open the input file");
		System.exit(1);
	}

	public static void main(String[] args) throws Exception{
		CharStream in = null;
		if (args.length != 1)
			handleFileErrors();
		try {
			in = CharStreams.fromFileName(args[0]);
		} catch (Exception e) {
			handleFileErrors();
		}
		cssLexer lexer = new cssLexer(in);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		cssParser parser = new cssParser(tokens);
		ParseTree tree = parser.program();
		MyVisitor visitor = new MyVisitor();
		visitor.visit(tree);
	}
}
