/*
 * StandardTypeSystem.java
 */

package jltools.types;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

import jltools.ast.NodeVisitor;
import jltools.util.InternalCompilerError;


/**
 * StandardTypeSystem
 *
 * Overview:
 *    A StandardTypeSystem is a universe of types, including all Java types.
 **/
public class StandardTypeSystem extends TypeSystem {

  // resolver should handle caching.
  public StandardTypeSystem() {}

  /**
   * Initializes the type system and its internal constants (which depend on
   * the resolver).
   */
  public void initializeTypeSystem( ClassResolver resolver, 
                                    ClassCleaner cleaner) 
    throws SemanticException
  {
    this.resolver = resolver;
    this.cleaner = cleaner;
    this.emptyResolver = new CompoundClassResolver();

    OBJECT_ = resolver.findClass( "java.lang.Object");
    STRING_ = resolver.findClass( "java.lang.String");
    CLASS_ = resolver.findClass( "java.lang.Class");
    THROWABLE_ = resolver.findClass( "java.lang.Throwable");
    EXCEPTION_ = resolver.findClass( "java.lang.Exception");
    ERROR_ = resolver.findClass( "java.lang.Error");
    RTEXCEPTION_ = resolver.findClass( "java.lang.RuntimeException");
    CLONEABLE_ = resolver.findClass( "java.lang.Cloneable");
    SERIALIZABLE_ = resolver.findClass( "java.io.Serializable");
  }

  public LocalContext getLocalContext( ImportTable it,
        NodeVisitor visitor ) {

    return new LocalContext( it, this, visitor );
  }

  ////
  // Functions for two-type comparison.
  ////

  /**
   * Returns true iff childType and ancestorType are distinct
   * reference types, and childType descends from ancestorType.
   **/
  public boolean descendsFrom(Type childType, 
                              Type ancestorType) 
    throws SemanticException 
  {
    if ( childType instanceof AmbiguousType ||
         ancestorType instanceof AmbiguousType)
      throw new InternalCompilerError("Expected fully qualified classes.");

    if(ancestorType instanceof ReferenceType &&
       childType.equals( NULL_)) {
      return true;
    }

    if (ancestorType.equals(childType) ||
        ! (childType instanceof ReferenceType) ||
        ! (ancestorType instanceof ReferenceType) )
    {
      return false;
    }

    boolean isClass = (childType instanceof ClassType) &&
	! ((ClassType)childType).getAccessFlags().isInterface();

    // If the child isn't an interface or array, check whether its
    // supertype is or descends from the ancestorType
    if (isClass) {
      if ( childType.equals ( OBJECT_)) {
        return false;
      }

      ReferenceType parentType = (ReferenceType)
				((ReferenceType)childType).getSuperType();
      if (parentType.equals(ancestorType) ||
	  descendsFrom(parentType, ancestorType)) {
	return true;
      }
    }
    else {
      // if it _is_ an interface or array, check whether the ancestor is Object.
      if (ancestorType.equals(OBJECT_)) {
	return true;
      }
    }

    // Next check interfaces.
    for(Iterator it = getInterfaces((ReferenceType)childType).iterator(); it.hasNext(); ) {
      Type parentType = (Type) it.next();
      if (parentType.equals(ancestorType) ||
	  descendsFrom(parentType, ancestorType))
	return true;            
    }
    return false;
  }

  /**
   * Requires: all type arguments are canonical.
   *
   * Returns true iff childType and ancestorType are non-primitive
   * types, and a variable of type childType may be legally assigned
   * to a variable of type ancestorType.
   **/
  public boolean isAssignableSubtype(Type childType, 
				     Type ancestorType)
    throws SemanticException{

    if ( childType instanceof AmbiguousType ||
         ancestorType instanceof AmbiguousType) {
      throw new InternalCompilerError("Expected fully qualified classes.");
    } 

    // childType is primitive.
    if (childType instanceof PrimitiveType &&
	ancestorType instanceof PrimitiveType) {
      
      if( ((PrimitiveType)childType).isVoid()) {
        return false;
      }

      PrimitiveType c = (PrimitiveType)childType,
        a = (PrimitiveType)ancestorType;

      if( c.isBoolean() && a.isBoolean()) {
        return true;
      }
      if( c.isBoolean() || a.isBoolean()) {
        return false;
      }

      if( c.getKind() <= a.getKind()) {
        return true;
      }
      else {
        return false;
      }
    }

    if( childType instanceof PrimitiveType ||
        ancestorType instanceof PrimitiveType) {
      return false;
    }

    // childType is array.
    if (childType instanceof ArrayType) {
      ArrayType  child = (ArrayType) childType;
      if (ancestorType instanceof ArrayType) {
	ArrayType ancestor = (ArrayType) ancestorType;
	// Both types are arrays, of the same dimensionality.	
	Type childbase = child.getBaseType();
	Type ancestorbase = ancestor.getBaseType();
	return isAssignableSubtype(childbase, ancestorbase);
      } else {
	// childType is an array, but ancestorType not an array.
	return descendsFrom(childType, ancestorType);    
      }
    } 
    
    // childType is null.
    if (childType instanceof NullType) 
      return true;
 
    // kliger - can we say ReferenceType here?
    // So childType is definitely a ClassType.
    if (! (ancestorType instanceof ReferenceType))
	return false;
    
    return (childType.equals(ancestorType) || 
	    descendsFrom(childType, ancestorType));    
  }
  /**
   * Requires: all type arguments are canonical.  ToType is not a NullType.
   *
   * Returns true iff a cast from fromType to toType is valid; in other
   * words, some non-null members of fromType are also members of toType.
   **/
  public boolean isCastValid(Type fromType, Type toType)
    throws SemanticException
  {
    // Are they distinct?
    if (fromType.equals(toType)) return true;
    
    // Are they primitive?
    if (fromType instanceof PrimitiveType) {
      if (! (toType instanceof PrimitiveType)) 
	return false;

      // Distinct primitive types are only convertable if type are numeric.
      if (((PrimitiveType)fromType).isNumeric() && ((PrimitiveType)toType).isNumeric()) 
        return true;
      return false;
    }
    if (toType instanceof PrimitiveType) return false;
  
    if (fromType instanceof NullType) return true;

    // Array cases.
    if (fromType instanceof ArrayType &&
          toType instanceof ArrayType) {
      // FIXME: Make this iterative.
      Type fromBase = ((ArrayType)fromType).getBaseType();
      Type toBase   = ((ArrayType)toType).getBaseType();
      if (fromBase instanceof PrimitiveType) {
        return toBase.equals(fromBase);
      } else if (toBase instanceof PrimitiveType) {
        return false;
      }	
      // Both bases are reference types.
      return isCastValid(fromBase, toBase);
    }
    else if (fromType instanceof ArrayType)
    {
      return descendsFrom(fromType, toType);    
    }
    else if (toType instanceof ArrayType)
    {
      return descendsFrom(toType, fromType);    
    }

    if( fromType instanceof NullType) {
      return (toType instanceof ClassType);
    }

    if (! (fromType instanceof ClassType))
      return false;
    if (! (toType instanceof ClassType))
      return false;

    // From and to are neither primitive nor an array. They are distinct.
    boolean fromInterface = ((ClassType)fromType).getAccessFlags().isInterface();
    boolean toInterface   =   ((ClassType)toType).getAccessFlags().isInterface();
    boolean fromFinal     = ((ClassType)fromType).getAccessFlags().isFinal();
    boolean toFinal       =   ((ClassType)toType).getAccessFlags().isFinal();

    // This is taken from Section 5.5 of the JLS.
    if (!fromInterface) {
      // From is not an interface.
      if (!toInterface) {
	// Nether from nor to is an interface.
	return descendsFrom(fromType, toType) || 
	  descendsFrom(toType, fromType);
      } else if (fromFinal) {
	// From is a final class, and to is an interface
	return descendsFrom(fromType, toType);
      } else {
	// From is a non-final class, and to is an interface.
	return true;
      }
    } else {
      // From is an interface
      if (!toInterface && !toFinal) {
	// To is a non-final class.
	return true;
      } else if (toFinal) {
	// To is a final class.
	return descendsFrom(toType, fromType);
      } else {
	// To and From are both interfaces.
	return true;
      }
    }
  }

  /**
   * Requires: all type arguments are canonical.
   *
   * Returns true iff an implicit cast from fromType to toType is valid;
   * in other words, every member of fromType is member of toType.
   **/
  public boolean isImplicitCastValid(Type fromType, Type toType)
    throws SemanticException
  {
    // TODO: read JLS 5.1 to be sure this is valid.

    if (fromType.equals(toType)) return true;
    
    ////
    // Types are distinct.
    ////

    if (fromType instanceof PrimitiveType) {
      if (! (toType instanceof PrimitiveType)) return false;
      ////
      // Both types are primitive...
      ////
      PrimitiveType ptFromType = (PrimitiveType) fromType;
      PrimitiveType ptToType = (PrimitiveType) toType;
      
      if (! ptFromType.isNumeric() || ! ptToType.isNumeric())       
	return false;

      // ...and numeric.
      switch (ptFromType.getKind()) 
	{
	case PrimitiveType.VOID:
          return false;
	case PrimitiveType.BYTE:
	case PrimitiveType.SHORT:
	case PrimitiveType.CHAR:
	  if (ptToType.getKind() == PrimitiveType.INT) return true;
	case PrimitiveType.INT:
	  if (ptToType.getKind() == PrimitiveType.LONG) return true;
	case PrimitiveType.LONG:
	  if (ptToType.getKind() == PrimitiveType.FLOAT) return true;
	case PrimitiveType.FLOAT:
	  if (ptToType.getKind() == PrimitiveType.DOUBLE) return true;
	case PrimitiveType.DOUBLE:
	default:
	  ;
	}
      return false;
    }
    if (toType instanceof PrimitiveType) return false;
    
    return isAssignableSubtype(fromType,toType);
  }

  /**
   * Returns true iff type1 and type2 are the same type.
   **/
  public boolean isSameType(Type type1, Type type2)
  {
    return type1.equals(type2);
  }

  /**
   * Returns true if <code>value</code> can be implicitly cast to Primitive type
   * <code>t</code>.
   */
  public  boolean numericConversionValid ( Type t, long value)
  {
    if ( !(t instanceof PrimitiveType)) return false;
    
    int kind = ((PrimitiveType)t).getKind();
    switch (kind)
    {
    case PrimitiveType.BYTE: return (Math.abs( value) <= Byte.MAX_VALUE);
    case PrimitiveType.SHORT: return (Math.abs( value) <= Short.MAX_VALUE);
    case PrimitiveType.CHAR: return (value >= 0 && value <= Character.MAX_VALUE);
    case PrimitiveType.INT: return ( Math.abs(value) <= Integer.MAX_VALUE);
    case PrimitiveType.LONG: return true;
    default: return false;
    }
  }


  ////
  // Functions for one-type checking and resolution.
  ////
  
  /**
   * Returns true iff <type> is a canonical (fully qualified) type.
   **/
  public boolean isCanonical(Type type) throws SemanticException {
    return type.isCanonical();
  }
      
  /**
   * Checks whether a method, field or inner class within ctTarget with access flags 'flags' can
   * be accessed from Context context, where context is a class type.
   */
  public boolean isAccessible(ReferenceType rtTarget, AccessFlags flags, LocalContext context) 
    throws SemanticException 
  {

    // check if in same class or public 
    if ( isSameType( rtTarget, context.getCurrentClass() ) ||
         flags.isPublic())
      return true;

    if (! rtTarget.isClassType()) {
      return false;
    }

    ClassType ctTarget = (ClassType) rtTarget;

    // check if context is an inner class of ctEnclosingClass, in which case protection doesnt matter
    if ( isEnclosed ( context.getCurrentClass(), ctTarget))
      return true;
    // check if ctTarget is an inner of context, in which case protection doesnt matter either
    if ( isEnclosed ( ctTarget, context.getCurrentClass()))
      return true;

    if ( ! (context.getCurrentClass() instanceof ClassType))
    {
      throw new InternalCompilerError("Internal error: Context is not a Classtype");
    }

    ClassType ctContext = (ClassType)context.getCurrentClass();
    
    // check for package level scope. ( same package and flags has package level scope.
    if ( ctTarget.getPackage() == null && ctContext.getPackage() == null && flags.isPackage())
      return true;

    if (ctTarget.getPackage() != null &&
        ctTarget.getPackage().equals (ctContext.getPackage()) &&
        flags.isPackage())
      return true;
    
    // protected
    if ( ctContext.descendsFrom( ctTarget ) &&
         flags.isProtected())
      return true;
    
    // else, 
    return false;
  }

  public boolean isEnclosed(ClassType tInner, ClassType tOuter)
  {
    ClassType ct = tInner; 
    while ( (ct = ct.getContainingClass()) != null &&
            ! ct.equals(tOuter));
    return ct != null;
  }

  /**
   * If <type> is a valid type in the given context, returns a
   * canonical form of that type.  Otherwise, returns a String
   * describing the error.
   **/
  public Type checkAndResolveType(Type type, TypeContext context)
    throws SemanticException {
    // System.out.println( "Checking: " + type + " " + type.getTypeString());

    if (type.isCanonical()) return type;

    if (type instanceof ArrayType) {
      ArrayType at = (ArrayType) type;
      Type base = at.getBaseType();
      Type result = checkAndResolveType(base, context);

      if (result.isPackageType()) {
	throw new SemanticException("Type " + result.getTypeString() +
		" is undefined");
      }

      return new ArrayType(this, (Type)result);
    }
    
    if (! (type instanceof AmbiguousType)) 
      throw new InternalCompilerError(
	"Found a non-canonical, non-array, non-ambiguous type: " + type.getTypeString() + ".");

    return checkAndResolveAmbiguousType((AmbiguousType) type, context);
  }

  public Type checkAndResolveType(Type type, Type contextType) throws SemanticException {
    if (contextType.isClassType()) {
	TypeContext classContext = getClassContext(resolver, (ClassType) contextType);
	return checkAndResolveType(type, classContext);
    }
    else if (contextType.isPackageType()) {
	TypeContext packageContext = getPackageContext(resolver,
	    (PackageType) contextType);
	return checkAndResolveType(type, packageContext);
    }
    else {
	throw new SemanticException("Type " + type + " not found in context");
    }
  }

  protected Type checkAndResolveAmbiguousType(AmbiguousType type,
    TypeContext context) throws SemanticException {

    if (! (type instanceof AmbiguousNameType)) {
      throw new InternalCompilerError(
	"Found a non-canonical, non-array, non-ambiguous-name type.");
    }

    AmbiguousNameType ambType = (AmbiguousNameType) type;

    // If the ambiguous name is just an identifier, look it up in the context.
    if (ambType.isShort()) {
      return context.getType(ambType.getName());
    }

    // If the ambiguous name is qualified: classify the prefix to create
    // a new context in which to lookup the unqualified name.
    Type prefixType = ambType.getPrefix();

    if (prefixType instanceof AmbiguousType) {
	prefixType = checkAndResolveAmbiguousType((AmbiguousType) prefixType,
						context);
    }

    // Lookup the unqualified name in the context of the prefix.
    if (prefixType.isClassType()) {
	TypeContext classContext = getClassContext(resolver,
						  (ClassType) prefixType);
	return classContext.getType(ambType.getName());
    }
    else if (prefixType.isPackageType()) {
	TypeContext packageContext = getPackageContext(resolver,
						      (PackageType) prefixType);
	return packageContext.getType(ambType.getName());
    }
    else {
	throw new SemanticException("Type " + type + " not found in context");
    }
  }

  ////
  // Various one-type predicates.
  ////
  /**
   * Returns true iff an object of type <type> may be thrown.
   **/
  public boolean isThrowable(Type type) throws SemanticException {
    return descendsFrom(type,THROWABLE_) || type.equals(THROWABLE_);
  }
  /**
   * Returns true iff an object of type <type> may be thrown by a method
   * without being declared in its 'throws' clause.
   **/
  public boolean isUncheckedException(Type type) throws SemanticException {
    return descendsFrom(type,ERROR_) || type.equals(ERROR_) || 
      descendsFrom(type,RTEXCEPTION_) || type.equals(RTEXCEPTION_);
  }

  ////
  // Functions for type membership.
  ////
  /**
   * Requires: all type arguments are canonical.
   *
   * Returns the fieldMatch named 'name' defined on 'type' visible in
   * context.  If no such field may be found, returns a fieldmatch
   * with an error explaining why. name and context may be null, in which case
   * they will not restrict the output.
   **/
  public FieldInstance getField(Type t, String name, LocalContext context ) 
    throws SemanticException
  {
    FieldInstance fi = null, fiEnclosing = null, fiTemp = null;
    ReferenceType type = null;

    if (t == null)
    {
      throw new InternalCompilerError("getField called with null type");
    }

    if ( !( t instanceof ReferenceType))
    {
      throw new SemanticException("Field access valid only on reference types.");
    }
    type = (ReferenceType)t;
    do 
    {
      for (Iterator i = type.getFields().iterator(); i.hasNext() ; )
      {
	fi = (FieldInstance)i.next();
	if ( fi.getName().equals(name))
	{
	  if ( isAccessible( type, fi.getAccessFlags(), context))
	  {
	    return fi;
	  }
	  throw new SemanticException(" Field \"" + name + "\" found in \"" + 
				       type.getTypeString() + 
				       "\", but with wrong access permissions.");
	}
      }
    }
    while ( (type = (ReferenceType)type.getSuperType()) != null);
    throw new SemanticException( "Field \"" + name + "\" not found in context "
				  + t.getTypeString() );
  }

 /**
   * Requires: all type arguments are canonical.
   * 
   * Returns the MethodMatch named 'name' defined on 'type' visibile in
   * context.  If no such field may be found, returns a fieldmatch
   * with an error explaining why. Considers accessflags.
   **/
  public MethodTypeInstance getMethod(Type t, MethodType method, 
                                      LocalContext context)
    throws SemanticException
  {
    if (t == null)
    {
      throw new InternalCompilerError("getMethod called with null type");
    }

    if ( !( t instanceof ReferenceType))
    {
      throw new SemanticException("Method access valid only on reference types.");
    }

    ReferenceType type = (ReferenceType) t;

    List lAcceptable = new java.util.ArrayList();
    getMethodSet ( lAcceptable, type, method, context);
    
    if (lAcceptable.size() == 0)
      throw new SemanticException ( "No valid method call found for \"" + 
                                     method.getName() + "\".");

    // now, use JLS 15.11.2.2
    Object [] mtiArray = lAcceptable.toArray(  );
    MostSpecificComparator msc = new MostSpecificComparator();
    java.util.Arrays.sort( mtiArray, msc);

    // now check to make sure that we have a maximal most specific method.
    // (if we did, it would be in the 0th index.
    for ( int i = 1 ; i < mtiArray.length; i++)
    {
      if (msc.compare ( mtiArray[0], mtiArray[i]) == 1)
        throw new SemanticException("Ambiguous method \"" + method.getName() 
                                     + "\". More than one invocations are valid"
                                     + " from this context.");
    }
    
    // ok. mtiArray[0] is maximal most specific, so return it.
    return (MethodTypeInstance)mtiArray[0];
  }

  /** 
   * Class to handle the comparisons; dispactes to moreSpecific method. 
   * <p> Should really be an anonymous class, but isn't because the jltools
   * compiler doesn't yet handle anonymous classes.
   */
  class MostSpecificComparator implements java.util.Comparator
  {
    public int compare ( Object o1, Object o2)
    {
      if ( !( o1 instanceof MethodTypeInstance ) ||
           !( o2 instanceof MethodTypeInstance ))
        throw new ClassCastException();
      return moreSpecific ( (MethodTypeInstance)o1, (MethodTypeInstance)o2) ?
        -1 : 1;
    }
  }


  /**
   * populates the list lAcceptible with those MethodTypeInstances which are 
   * Applicable and Accessible as defined by JLS 15.11.2.1
   */
  private void getMethodSet(List lAcceptable, ReferenceType type, MethodType method, 
                            LocalContext context)
    throws SemanticException
  {
    MethodTypeInstance mti = null;

    if (type == null)
    {
      throw new InternalCompilerError(
	"getMethodSet called with null reference type");
    }

    do 
    {
      // System.out.println("collecting methods of " + type.getTypeString());
      for (Iterator i = type.getMethods().iterator(); i.hasNext() ; )
      {
	mti = (MethodTypeInstance)i.next();
	if ( methodCallValid( mti, method))
	{
	  if ( isAccessible( type, mti.getAccessFlags(), context))
	  {
	    lAcceptable.add (mti);
	  }
	}
      }
    }
    while ( (type = (ReferenceType)type.getSuperType()) != null);
    // System.out.println("done collecting methods");
  }

  /**
   * Returns whether MethodType 1 is <i>more specific</i> than MethodTypeInstance 2, 
   * where <i>more specific</i> is defined as JLS 15.11.2.2
   * <p>
   * Note: There is a fair amount of guesswork since the JLS does not include any 
   * info regarding java 1.2, so all inner class rules are found empirically
   * using jikes and javac.
   */
  private boolean moreSpecific(MethodTypeInstance mti1, MethodTypeInstance mti2)
  {
    try
    {
      // rule 1:
      ReferenceType t1 = mti1.getEnclosingType();
      ReferenceType t2 = mti2.getEnclosingType();

      if (t1 instanceof ClassType && t2 instanceof ClassType) {
	if ( ! (t1.descendsFrom(t2) || t1.equals(t2) ||
		isEnclosed((ClassType) t1, (ClassType) t2)))
	  return false;
      }
      else {
	if ( ! (t1.descendsFrom(t2) || t1.equals(t2)))
	  return false;
      }

      // rule 2:
      return ( methodCallValid ( mti2, mti1) );
    }
    catch (SemanticException tce)
    {
      return false;
    }
  }

  /**
   * Returns the supertype of type, or null if type has no supertype.
   **/
  public ReferenceType getSuperType(ReferenceType type) throws SemanticException
  {
    return (ReferenceType)type.getSuperType();
  }

  /**
   * Returns an immutable list of all the interface types which type
   * implements.
   **/
  public List getInterfaces(ReferenceType type) throws SemanticException
  {
    return type.getInterfaces();
  }

  /**
   * Returns true iff <type1> is the same as <type2>.
   **/
  public boolean isSameType(MethodType type1, MethodType type2) 
  {
    return ( type1.equals(type2));
  }

  /**
   * Requires: all type arguments are canonical.
   * Returns the least common ancestor of Type1 and Type2
   **/
  public Type leastCommonAncestor( Type type1, Type type2) throws SemanticException
  {
    if (( type1 instanceof PrimitiveType ) &&
        ( type2 instanceof PrimitiveType ))
    {
      if( ((PrimitiveType)type1).isBoolean()) {
        if( ((PrimitiveType)type2).isBoolean()) {
          return getBoolean();
        }
        else {
          throw new SemanticException( 
                       "No least common ancestor found. The type \"" 
                       + type1.getTypeString() + 
                       "\" is not compatible with the type \"" 
                       + type2.getTypeString() + "\"."); 
        }

      }
      if( ((PrimitiveType)type2).isBoolean()) {
        throw new SemanticException( 
                       "No least common ancestor found. The type \"" 
                       + type1.getTypeString() + 
                       "\" is not compatible with the type \"" 
                       + type2.getTypeString() + "\"."); 
      }
      if( ((PrimitiveType)type1).isVoid()) {
        if( ((PrimitiveType)type2).isVoid()) {
          return getVoid();
        }
        else {
          throw new SemanticException( 
                       "No least common ancestor found. The type \"" 
                       + type1.getTypeString() + 
                       "\" is not compatible with the type \"" 
                       + type2.getTypeString() + "\"."); 
        }
      }
      if( ((PrimitiveType)type2).isVoid()) {
        throw new SemanticException( 
                       "No least common ancestor found. The type \"" 
                       + type1.getTypeString() + 
                       "\" is not compatible with the type \"" 
                       + type2.getTypeString() + "\"."); 
      } 
      
      return new PrimitiveType( this, Math.max ( 
                                        ((PrimitiveType) type1).getKind(), 
                                        ((PrimitiveType) type2).getKind() ));
    }

    if ( ( type1 instanceof ArrayType ) && ( type2 instanceof ArrayType ) ) {
	ArrayType t1 = (ArrayType) type1;
	ArrayType t2 = (ArrayType) type2;

	Type base = leastCommonAncestor(t1.getBaseType(), t2.getBaseType());

	return new ArrayType(this, base);
    }
    
    if ( ( type1 instanceof ReferenceType ) && ( type2 instanceof NullType))
      return type1;
    if ( ( type2 instanceof ReferenceType ) && ( type1 instanceof NullType))
      return type2;
    
    if (!( type1 instanceof ReferenceType) ||
        !( type2 instanceof ReferenceType)) {
      throw new SemanticException( 
                       "No least common ancestor found. The type \"" 
                       + type1.getTypeString() + 
                       "\" is not compatible with the type \"" 
                       + type2.getTypeString() + "\"."); 
    }
    
    ReferenceType tSuper = (ReferenceType)type1;
    
    while ( ! ( type2.descendsFrom ( tSuper ) ||
                type2.equals( tSuper )) &&
            tSuper != null) {
      tSuper = (ReferenceType)tSuper.getSuperType();
    }

    if ( tSuper == null) {
      throw new SemanticException( 
                       "No least common ancestor found. The type \"" 
                       + type1.getTypeString() + 
                       "\" is not compatible with the type \"" 
                       + type2.getTypeString() + "\"."); 
    }
    return tSuper;
                                           
  }


  ////
  // Functions for method testing.
  ////

  /**
   * Returns true iff <type1> has the same arguments as <type2>
   **/
  public boolean hasSameArguments(MethodType type1, MethodType type2)
  {
    List lArgumentTypes1 = type1.argumentTypes();
    List lArgumentTypes2 = type2.argumentTypes();
    
    if (lArgumentTypes1.size() != lArgumentTypes2.size())
      return false;
    
    Iterator iArgumentTypes1 = lArgumentTypes1.iterator();
    Iterator iArgumentTypes2 = lArgumentTypes2.iterator();
    
    for ( ; iArgumentTypes1.hasNext() ; )
      if ( ! isSameType ( (Type)iArgumentTypes1.next(), (Type)iArgumentTypes2.next() ) )
        return false;
    return true;
  }

  /**
   * Returns whether the arguments for MethodType call can call
   * MethodTypeInstance prototype
   */
  public boolean methodCallValid( MethodTypeInstance prototype, MethodType call) 
    throws SemanticException
  {
    if ( ! prototype.getName().equals(call.getName()))
      return false;

    List lArgumentTypesProto = prototype.argumentTypes();
    List lArgumentTypesCall = call.argumentTypes();
    
    if (lArgumentTypesProto.size() != lArgumentTypesCall.size())
      return false;
    
    Iterator iArgumentTypesProto = lArgumentTypesProto.iterator();
    Iterator iArgumentTypesCall  = lArgumentTypesCall.iterator();
    
    for ( ; iArgumentTypesProto.hasNext() ; )
    {
      if ( !isImplicitCastValid (  (Type)iArgumentTypesCall.next(),  (Type)iArgumentTypesProto.next() ) )
        return false;
    }
    
    return true;
  }

  ////
  // Functions which yield particular types.
  ////
  public Type getNull()    { return NULL_; }
  public Type getVoid()    { return VOID_; }
  public Type getBoolean() { return BOOLEAN_; }
  public Type getChar()    { return CHAR_; }
  public Type getByte()    { return BYTE_; }
  public Type getShort()   { return SHORT_; }
  public Type getInt()    { return INT_; }
  public Type getLong()    { return LONG_; }
  public Type getFloat()   { return FLOAT_; }
  public Type getDouble()  { return DOUBLE_; }
  public Type getObject()  { return OBJECT_; }
  public Type getClass_()   { return CLASS_; }
  public Type getString()   { return STRING_; }
  public Type getThrowable() { return THROWABLE_; }
  public Type getError() { return ERROR_; }
  public Type getException() { return EXCEPTION_; }
  public Type getRTException() { return RTEXCEPTION_; }
  public Type getCloneable() { return CLONEABLE_; }
  public Type getSerializable() { return SERIALIZABLE_; }

  protected final Type NULL_    = new NullType(this);
  protected final Type VOID_    = new PrimitiveType(this, PrimitiveType.VOID);
  protected final Type BOOLEAN_ = new PrimitiveType(this, PrimitiveType.BOOLEAN);
  protected final Type CHAR_    = new PrimitiveType(this, PrimitiveType.CHAR);
  protected final Type BYTE_    = new PrimitiveType(this, PrimitiveType.BYTE);
  protected final Type SHORT_   = new PrimitiveType(this, PrimitiveType.SHORT);
  protected final Type INT_     = new PrimitiveType(this, PrimitiveType.INT);
  protected final Type LONG_    = new PrimitiveType(this, PrimitiveType.LONG);
  protected final Type FLOAT_   = new PrimitiveType(this, PrimitiveType.FLOAT);
  protected final Type DOUBLE_  = new PrimitiveType(this, PrimitiveType.DOUBLE);
  protected Type OBJECT_      ;
  protected Type CLASS_       ;
  protected Type STRING_      ;
  protected Type THROWABLE_   ;
  protected Type ERROR_       ;
  protected Type EXCEPTION_   ;
  protected Type RTEXCEPTION_ ;
  protected Type CLONEABLE_   ;
  protected Type SERIALIZABLE_;
  
  protected ClassResolver resolver; //Should do its own caching.
  protected ClassCleaner cleaner;
  protected ClassResolver emptyResolver;

  /**
   * Returns a non-canonical type object for a class type whose name
   * is the provided string.  This type may not correspond to a valid
   * class.
   **/
  public Type getTypeWithName(String name) {
    return new AmbiguousNameType(this, name);
  }

  /**
   * Returns a type identical to <type>, but with <dims> more array
   * dimensions.  If dims is < 0, array dimensions are stripped.
   **/
  public Type extendArrayDims(Type type, int dims) {
    if (dims == 0) {
	return type;
    }
    else if (dims < 0) {
	if (type instanceof ArrayType) {
	  return extendArrayDims(((ArrayType) type).getBaseType(), dims+1);
	}
	else {
	  throw new InternalCompilerError("Cannot strip dimensions of non-array type " + type.getTypeString());
	}
    }
    else {
	return new ArrayType(this, type, dims);
    }
  }

  /**
   * Returns a canonical type corresponding to the Java Class object
   * theClass.  Does not require that <theClass> have a JavaClass
   * registered in this typeSystem.  Does not register the type in
   * this TypeSystem.  For use only by JavaClass implementations.
   **/
  public Type typeForClass(Class clazz) throws SemanticException
  {
    if( clazz == Void.TYPE) {
      return VOID_;
    }
    else if( clazz == Boolean.TYPE) {
      return BOOLEAN_;
    }
    else if( clazz == Byte.TYPE) {
      return BYTE_;
    }
    else if( clazz == Character.TYPE) {
      return CHAR_;
    }
    else if( clazz == Short.TYPE) {
      return SHORT_;
    }
    else if( clazz == Integer.TYPE) {
      return INT_;
    }
    else if( clazz == Long.TYPE) {
      return LONG_;
    }
    else if( clazz == Float.TYPE) {
      return FLOAT_;
    }
    else if( clazz == Double.TYPE) {
      return DOUBLE_;
    }
    else if( clazz.isArray()) {
      return new ArrayType( this, typeForClass( clazz.getComponentType()));
    }
    else {
      return resolver.findClass(clazz.getName());
    }
  }

  public TypeContext getEmptyContext(ClassResolver resolver) {
    return new EmptyContext(this, resolver);
  }

  public TypeContext getClassContext(ClassResolver resolver, ClassType type) throws SemanticException {
    return new ClassContext(resolver, type);
  }

  public TypeContext getPackageContext(ClassResolver resolver, PackageType type) throws SemanticException {
    return new PackageContext(resolver, type);
  }

  public TypeContext getPackageContext(ClassResolver resolver, String name) throws SemanticException {
    return new PackageContext(resolver, new PackageType(this, name));
  }
}
