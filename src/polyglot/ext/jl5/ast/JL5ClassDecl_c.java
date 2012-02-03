package polyglot.ext.jl5.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import polyglot.ast.*;
import polyglot.ext.jl5.types.*;
import polyglot.ext.param.types.MuPClass;
import polyglot.types.Context;
import polyglot.types.Flags;
import polyglot.types.SemanticException;
import polyglot.types.TypeSystem;
import polyglot.util.CodeWriter;
import polyglot.util.CollectionUtil;
import polyglot.util.Position;
import polyglot.util.TypedList;
import polyglot.visit.NodeVisitor;
import polyglot.visit.PrettyPrinter;
import polyglot.visit.TypeBuilder;
import polyglot.visit.TypeChecker;

/**
 * A <code>ClassDecl</code> is the definition of a class, abstract class, or
 * interface. It may be a public or other top-level class, or an inner named
 * class, or an anonymous class.
 */
public class JL5ClassDecl_c extends ClassDecl_c implements JL5ClassDecl {

	protected List<ParamTypeNode> paramTypes;

	public JL5ClassDecl_c(Position pos, Flags flags, Id name,
			TypeNode superClass, List interfaces, ClassBody body) {
		this(pos, flags, name, superClass, interfaces, body,
				new ArrayList<ParamTypeNode>());
	}

	public JL5ClassDecl_c(Position pos, Flags fl, Id name, TypeNode superType,
			List interfaces, ClassBody body, List<ParamTypeNode> paramTypes) {
		super(pos, fl, name, superType, interfaces, body);
		if (paramTypes == null)
			paramTypes = new ArrayList<ParamTypeNode>();
		this.paramTypes = paramTypes;
		if (pos == null) {
			this.position = Position.compilerGenerated();
		}
	}

	@Override
	public List<ParamTypeNode> paramTypes() {
		return this.paramTypes;
	}
	
	

	@Override
    public Node buildTypes(TypeBuilder tb) throws SemanticException {
        JL5ClassDecl n = (JL5ClassDecl)super.buildTypes(tb);
        JL5TypeSystem ts = (JL5TypeSystem)tb.typeSystem();
        JL5ParsedClassType ct = (JL5ParsedClassType) n.type();
                
        MuPClass pc = ts.mutablePClass(ct.position());
        ct.setPClass(pc);
        pc.clazz(ct);
        
        if (paramTypes() != null && !paramTypes().isEmpty()) {
            List<TypeVariable> typeVars = new ArrayList(this.paramTypes().size());
            for (ParamTypeNode ptn : this.paramTypes()) {
                TypeVariable tv = (TypeVariable)ptn.type(); 
                typeVars.add(tv);
                tv.declaringClass(ct);
            }
            ct.setTypeVariables(typeVars);
            pc.formals(new ArrayList(typeVars));
        }
        
        return n;

    }

	// !@! What do I need to do with this?
    public JL5ClassDecl paramTypes(List<ParamTypeNode> types) {
		JL5ClassDecl_c n = (JL5ClassDecl_c) copy();
		n.paramTypes = types;
		return n;
	}

	protected ClassDecl reconstruct(Id name, TypeNode superClass, List interfaces,
			ClassBody body, List paramTypes) {
		if (superClass != this.superClass
				|| !CollectionUtil.equals(interfaces, this.interfaces)
				|| body != this.body
				|| !CollectionUtil.equals(paramTypes, this.paramTypes)) {
			JL5ClassDecl_c n = (JL5ClassDecl_c) copy();
			n.superClass = superClass;
			n.interfaces = TypedList.copyAndCheck(interfaces, TypeNode.class,
					false);
			n.body = body;
			n.paramTypes = paramTypes;
			return n;
		}
		return this;
	}
	
	@Override
	public Node visitChildren(NodeVisitor v) {
		List paramTypes = visitList(this.paramTypes, v);
        Id name = (Id) visitChild(this.name, v);
		TypeNode superClass = (TypeNode) visitChild(this.superClass, v);
		List interfaces = visitList(this.interfaces, v);
		ClassBody body = (ClassBody) visitChild(this.body, v);
		return reconstruct(name, superClass, interfaces, body, paramTypes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see polyglot.ast.NodeOps#enterScope(polyglot.types.Context)
	 */
	@Override
	public Context enterChildScope(Node child, Context c) {
        if (child == this.body) {
            TypeSystem ts = c.typeSystem();
            c = c.pushClass(type, ts.staticTarget(type).toClass());
        }
        else {
            // Add this class to the context, but don't push a class scope.
            // This allows us to detect loops in the inheritance
            // hierarchy, but avoids an infinite loop.
            c = c.pushBlock();
            c.addNamed(this.type);
        }
		for (ParamTypeNode tn : paramTypes) {
			c = ((JL5Context) c).addTypeVariable((TypeVariable) tn.type());
		}
        return super.enterChildScope(child, c);
	}

	@Override
	public Node typeCheck(TypeChecker tc) throws SemanticException {
		JL5TypeSystem ts = (JL5TypeSystem) tc.typeSystem();

		if (type().superType() != null
				&& JL5Flags.isEnum(type().superType().toClass().flags())) {
			throw new SemanticException("Cannot extend enum type", position());
		}

		if (ts.equals(ts.Object(), type()) && !paramTypes.isEmpty()) {
			throw new SemanticException("Type: " + type()
					+ " cannot declare type variables.", position());
		}

		// check not extending java.lang.Throwable (or any of its subclasses)
		// with a generic class
		if (type().superType() != null
				&& ts.isSubtype(type().superType(), ts.Throwable())
				&& !paramTypes.isEmpty()) {
		    // JLS 3rd ed. 8.1.2
			throw new SemanticException(
					"Cannot subclass java.lang.Throwable or any of its subtypes with a generic class",
					superClass().position());
		}

		// check duplicate type variable decls
		for (int i = 0; i < paramTypes.size(); i++) {
			TypeNode ti = paramTypes.get(i);
			for (int j = i + 1; j < paramTypes.size(); j++) {
				TypeNode tj = paramTypes.get(j);
				if (ti.name().equals(tj.name())) {
					throw new SemanticException(
							"Duplicate type variable declaration.",
							tj.position());
				}
			}
		}
		return super.typeCheck(tc);
	}

	// XXX: pretty printing is not fixed up yet.
	public void prettyPrintModifiers(CodeWriter w, PrettyPrinter tr) {
		if (flags.isInterface()) {
			w.write(flags.clearInterface().clearAbstract().translate());
		} else {
			w.write(flags.translate());
		}

		if (flags.isInterface()) {
			w.write("interface ");
		} else {
			w.write("class ");
		}
	}

	public void prettyPrintName(CodeWriter w, PrettyPrinter tr) {
		w.write(name.id());
	}

	public void prettyPrintHeaderRest(CodeWriter w, PrettyPrinter tr) {
		if (superClass() != null && !JL5Flags.isEnum(type.flags())) {
			w.write(" extends ");
			print(superClass(), w, tr);
		}

		if (!interfaces.isEmpty()) {
			if (flags.isInterface()) {
				w.write(" extends ");
			} else {
				w.write(" implements ");
			}

			for (Iterator i = interfaces().iterator(); i.hasNext();) {
				TypeNode tn = (TypeNode) i.next();
				print(tn, w, tr);

				if (i.hasNext()) {
					w.write(", ");
				}
			}
		}

		w.write(" {");
	}

	@Override
	public void prettyPrintHeader(CodeWriter w, PrettyPrinter tr) {
		prettyPrintModifiers(w, tr);
		prettyPrintName(w, tr);
		//NB: Dropping the type parameters
		prettyPrintHeaderRest(w, tr);

	}	
}