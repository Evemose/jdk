package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

public class TransExtensions extends TreeTranslator {
    private TreeMaker make;
    protected final Context.Key<TransExtensions> transExtensionsKey = new Context.Key<>();
    protected final Attr attr;
    private Types types;
    protected Env<AttrContext> env;
    protected Names names;

    protected TransExtensions(Context context) {
        make = TreeMaker.instance(context);
        attr = Attr.instance(context);
        names = Names.instance(context);
    }

    public static TransExtensions instance(Context context) {
        var thiz = new TransExtensions(context);
        context.put(thiz.transExtensionsKey, thiz);
        return thiz;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        JCTree.JCExpression target;
        Symbol.MethodSymbol sym;
        if (tree.meth instanceof JCTree.JCFieldAccess fieldAccess) {
            target = fieldAccess.selected;
            sym = (Symbol.MethodSymbol) fieldAccess.sym;
        } else if (tree.meth instanceof JCTree.JCIdent ident) {
            Type firstParamType;
            if (((Symbol.MethodSymbol) ident.sym).params != null
            && ((Symbol.MethodSymbol) ident.sym).params.isEmpty()) {
                // method without parameters cant be extension
                return;
            } else {
                firstParamType = ((Symbol.MethodSymbol) ident.sym).params.head.type;
            }
            if (!(env.enclMethod != null ? env.enclMethod.sym.isStatic() : env.enclClass.sym.isStatic())
                    && !env.enclClass.type.tsym.isSubClass(
                    firstParamType.tsym,
                    types
            )) {
                // method is a static invocation
                return;
            }
            target = make.Ident(names._this);
            target.type = env.enclClass.type;
            ((JCTree.JCIdent) target).sym = env.enclClass.sym;
            sym = (Symbol.MethodSymbol) ((JCTree.JCIdent) tree.meth).sym;
        } else {
            throw new AssertionError("Unexpected method invocation target");
        }
        if (isExtensionCall(target.type, sym)) {
            // flip extension to static method call
            var select = make.Select(
                    make.QualIdent(sym.owner),
                    sym
            );
            select.type = sym.type;
            select.sym = sym;
            var newMethod = make.Apply(
                    tree.typeargs,
                    select,
                    tree.args.prepend(target)
            );
            newMethod.type = sym.type.getReturnType();
            var currMethType = (Type.MethodType) tree.meth.type;
            newMethod.meth.type = new Type.MethodType(
                    currMethType.argtypes.prepend(target.type),
                    currMethType.restype,
                    currMethType.thrown,
                    currMethType.tsym
            );
            result = newMethod;
        }
    }

    private boolean isExtensionCall(Type targetType, Symbol.MethodSymbol sym) {
        return !sym.owner.type.equals(targetType)
                && (sym.flags_field & Flags.EXTENSION) != 0
                && targetType.tsym.isSubClass(
                sym.params.getFirst().type.tsym,
                types
        );
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make, Context context) {
        try {
            this.env = env;
            this.make = make;
            this.names = Names.instance(context);
            types = Types.instance(context);
            translate(cdef);
        } finally {
            this.make = null;
        }

        return cdef;
    }
}
