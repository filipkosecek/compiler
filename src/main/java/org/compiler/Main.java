package org.compiler;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.gen.cssLexer;
import org.gen.cssParser;

import java.io.FileWriter;
import java.io.IOException;

public class Main {
	public static void main(String[] args) {
		/* parse arguments */
		String inputFile = null;
		String outputFile = "a.ll";
		int i = 0;
		while (i < args.length) {
			if (args[i].equals("-o")) {
				if (i >= args.length - 1) {
					System.err.println("Missing input file name.");
					System.exit(4);
				}
				outputFile = args[i + 1];
				i += 2;
				continue;
			}
			inputFile = args[i++];
		}
		if (inputFile == null) {
			System.err.println("No input file specified.");
			System.exit(6);
		}

		GlobalContext globalVars = new GlobalContext();
		CharStream in = null;
		/* try to open the input file */
		try {
			in = CharStreams.fromFileName(inputFile);
		} catch (IOException e) {
			System.err.println("Couldn't open the input file.");
			System.exit(5);
		}

		cssLexer lexer = new cssLexer(in);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		cssParser parser = new cssParser(tokens);
		ParseTree tree = parser.program();
		try (
				FileWriter out = new FileWriter(outputFile)
				)
		{
			System.out.println(outputFile);
			out.write(MainVisitor.getInstance(globalVars).visit(tree));
		} catch (IOException e) {
			System.err.println("Couldn't open the output file.");
			System.exit(3);
		} catch (Exception e) {
			System.err.println("Fatal compilation error.");
		}
	}
}
