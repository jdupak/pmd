/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.ast;


import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.annotation.InternalApi;
import net.sourceforge.pmd.lang.ast.NodeStream;
import net.sourceforge.pmd.lang.ast.TextAvailableNode;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.symbols.table.JSymbolTable;
import net.sourceforge.pmd.lang.symboltable.ScopedNode;


/**
 * Root interface for all Nodes of the Java AST.
 */
public interface JavaNode extends ScopedNode, TextAvailableNode {

    /**
     * Calls back the visitor's visit method corresponding to the runtime type of this Node.
     *
     * @param visitor Visitor to dispatch
     * @param data    Visit data
     */
    Object jjtAccept(JavaParserVisitor visitor, Object data);


    /**
     * Dispatches the given visitor to the children of this node. This is the default implementation
     * of {@link JavaParserVisitor#visit(JavaNode, Object)}, to which all other default
     * implementations for visit methods delegate. Unless visit methods are overridden without calling
     * {@code super.visit}, the visitor performs a depth-first tree walk.
     *
     * <p>The return value of the visit methods called on children are ignored.
     *
     * @param visitor Visitor to dispatch
     * @param data    Visit data
     *
     * @deprecated This method is not useful, the logic for combining
     *     children values should be present on the visitor, not the node
     */
    @Deprecated
    Object childrenAccept(JavaParserVisitor visitor, Object data);


    /**
     * Calls back the visitor's visit method corresponding to the runtime type of this Node.
     *
     * @param visitor Visitor to dispatch
     * @param data    Visit data
     * @param <T>     Type of data
     */
    <T> void jjtAccept(SideEffectingVisitor<T> visitor, T data);


    /**
     * Dispatches the given visitor to the children of this node. This is the default implementation
     * of {@link SideEffectingVisitor#visit(JavaNode, Object)}, to which all other default
     * implementations for visit methods delegate. Unless visit methods are overridden without calling
     * {@code super.visit}, the visitor performs a depth-first tree walk.
     *
     * @param visitor Visitor to dispatch
     * @param data    Visit data
     * @param <T>     Type of data
     */
    <T> void childrenAccept(SideEffectingVisitor<T> visitor, T data);


    /** Returns the first child of this node, or null if this node has no children. */
    @Nullable
    default JavaNode getFirstChild() {
        return getNumChildren() > 0 ? getChild(0) : null;
    }


    @Nullable
    default JavaNode getLastChild() {
        return getNumChildren() > 0 ? getChild(getNumChildren() - 1) : null;
    }

    @Override
    JavaNode getChild(int index);


    @Override
    JavaNode getParent();


    @Override
    NodeStream<JavaNode> children();

    JavaccToken jjtGetFirstToken();


    JavaccToken jjtGetLastToken();


    /**
     * Returns the node representing the type declaration this node is
     * found in. The type of that node is the type of the {@code this}
     * expression.
     *
     * <p>This returns null for nodes that aren't enclosed in a type declaration.
     * This includes {@linkplain ASTPackageDeclaration PackageDeclaration},
     * This includes {@linkplain ASTImportDeclaration ImportDeclaration},
     * {@linkplain ASTModuleDeclaration ModuleDeclaration},
     * {@linkplain ASTCompilationUnit CompilationUnit}, and top-level
     * {@linkplain ASTAnyTypeDeclaration AnyTypeDeclaration}s.
     */
    default ASTAnyTypeDeclaration getEnclosingType() {
        return getFirstParentOfType(ASTAnyTypeDeclaration.class);
    }



    /**
     * FIXME figure that out
     */
    Comment comment();


    @Override
    @NonNull ASTCompilationUnit getRoot();

    /**
     * Returns the symbol table for the program point represented by
     * this node.
     *
     * TODO
     */
    JSymbolTable getSymbolTable();
}
