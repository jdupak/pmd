/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.apex.ast;

import com.google.summit.ast.statement.SwitchStatement;

public final class ASTElseWhenBlock extends AbstractApexNode.Single<SwitchStatement.WhenElse> {

    ASTElseWhenBlock(SwitchStatement.WhenElse whenElse) {
        super(whenElse);
    }


    @Override
    protected <P, R> R acceptApexVisitor(ApexVisitor<? super P, ? extends R> visitor, P data) {
        return visitor.visit(this, data);
    }
}
