package jltools.ext.jl.parse;

import jltools.ast.*;
import jltools.parse.*;
import jltools.types.*;
import jltools.util.*;

/**
 * Represents an ambiguous, possibly qualified, identifier encountered while parsing.
 */
public class Name {
	public final Name prefix;
	public final String name;
	Position pos;
	NodeFactory nf;
	TypeSystem ts;

	public Name(ParserWrapper parser, Position pos, String name) {
		this(parser, pos, null, name);
	}

	public Name(ParserWrapper parser, Position pos, Name prefix, String name) {
		this.nf = parser.nodeFactory();
		this.ts = parser.typeSystem();
		this.pos = pos;
		this.prefix = prefix;
		this.name = name;
	}

	// expr
	public Expr toExpr() {
		if (prefix == null) {
			return nf.AmbExpr(pos, name);
		}

		return nf.Field(pos, prefix.toReceiver(), name);
	}

	// expr or type
	public Receiver toReceiver() {
		if (prefix == null) {
			return nf.AmbReceiver(pos, name);
		}

		return nf.AmbReceiver(pos, prefix.toPrefix(), name);
	}

	// expr, type, or package
	public Prefix toPrefix() {
		if (prefix == null) {
			return nf.AmbPrefix(pos, name);
		}

		return nf.AmbPrefix(pos, prefix.toPrefix(), name);
	}

	// type or package
	public QualifierNode toQualifier() {
		if (prefix == null) {
			return nf.AmbQualifierNode(pos, name);
		}

		return nf.AmbQualifierNode(pos, prefix.toQualifier(), name);
	}

	// type
	public PackageNode toPackage() {
		return nf.PackageNode(pos, ts.packageForName(toString()));
	}

	// type
	public TypeNode toType() {
		if (prefix == null) {
			return nf.AmbTypeNode(pos, name);
		}

		return nf.AmbTypeNode(pos, prefix.toQualifier(), name);
	}

	public String toString() {
		if (prefix == null) {
			return name;
		}

		return prefix.toString() + "." + name;
	}
}
