package org.compiler;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.gen.cssLexer;
import org.gen.cssParser;

import java.io.FileWriter;
import java.io.IOException;

public class Main {
	public static void main(String[] args) {
		GlobalContext globalVars = new GlobalContext();
		CharStream in = null;
		if (args.length != 1)
			globalVars.handleFileErrors();
		try {
			in = CharStreams.fromFileName(args[0]);
		} catch (Exception e) {
			globalVars.handleFileErrors();
		}
		cssLexer lexer = new cssLexer(in);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		cssParser parser = new cssParser(tokens);
		ParseTree tree = parser.program();
		try {
			FileWriter out = new FileWriter("/home/filipkosecek/IdeaProjects/untitled/test/test.ll");
			out.write(MainVisitor.getInstance(globalVars).visit(tree));
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
