package jltools.frontend;

import java.io.*;
import jltools.ast.*;
import jltools.util.*;

/**
 * A pass which runs a parser.  After parsing it stores the AST in the Job.
 * so it can be accessed by later passes.
 */
public class ParserPass implements Pass
{
    Job job;
    ExtensionInfo extInfo;

    public ParserPass(Job job, ExtensionInfo extInfo) {
	this.job = job;
	this.extInfo = extInfo;
    }

    public boolean run() {
	Source source = job.source();
	ErrorQueue eq = job.compiler().errorQueue();

	try {
	    Reader reader = source.open();

	    Parser p = extInfo.parser(reader, job);

	    jltools.frontend.Compiler.report(2, "Using parser " + p);

	    Node ast = p.parse();

	    job.ast(ast);

	    source.close();
	}
	catch (IOException e) {
	    eq.enqueue(ErrorInfo.IO_ERROR, e.getMessage());
	    return false;
	}

	return true;
    }

    public String toString() {
	return "Parser";
    }
}
