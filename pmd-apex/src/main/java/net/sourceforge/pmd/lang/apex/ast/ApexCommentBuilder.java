/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.apex.ast;

import static java.util.stream.Collectors.toList;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import net.sourceforge.pmd.annotation.InternalApi;
import net.sourceforge.pmd.lang.document.TextFileContent;

import com.nawforce.apexparser.ApexLexer;

@InternalApi
final class ApexCommentBuilder {
    private final TextFileContent sourceContent;
    private final CommentInformation commentInfo;

    ApexCommentBuilder(TextFileContent sourceContent, String suppressMarker) {
	this.sourceContent = sourceContent;
        commentInfo = extractInformationFromComments(sourceContent, suppressMarker);
    }

    private static final class LineColumnPosition implements Comparable<LineColumnPosition> {
        int line;
        int column;

        LineColumnPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }

        public static LineColumnPosition of(Token token) {
            return new LineColumnPosition(token.getLine(), token.getCharPositionInLine() + 1);
        }

        public static LineColumnPosition beginOf(ApexNode<?> node) {
            return new LineColumnPosition(node.getBeginLine(), node.getBeginColumn());
        }

        public static LineColumnPosition endOf(ApexNode<?> node) {
            return new LineColumnPosition(node.getEndLine(), node.getEndColumn());
        }

        @Override
        public int compareTo(LineColumnPosition other) {
            if (this.line != other.line) {
                return this.line - other.line;
            }
            return this.column - other.column;
        }
    }

    public boolean containsComments(ASTCommentContainer commentContainer) {
        if (!commentContainer.hasRealLoc()) {
            // Synthetic nodes don't have a location and can't have comments
            return false;
        }
        LineColumnPosition nodeBeginPosition = LineColumnPosition.beginOf(commentContainer);

        // find the first comment after the start of the container node
        int index = Collections.binarySearch(commentInfo.nonDocTokensByPosition, nodeBeginPosition);

        // no exact hit found - this is expected: there is no comment token starting at
        // the very same index as the node
        assert index < 0 : "comment token is at the same position as non-comment token";
        // extract "insertion point"
        index = ~index;

        // now check whether the next comment after the node is still inside the node
        if (index >= 0 && index < commentInfo.nonDocTokensByPosition.size()) {
            LineColumnPosition commentPosition = commentInfo.nonDocTokensByPosition.get(index);
            return commentPosition.compareTo(nodeBeginPosition) >= 0
                && commentPosition.compareTo(LineColumnPosition.endOf(commentContainer)) <= 0;
        }
        return false;
    }

    public void addFormalComments() {
        for (ApexDocToken docToken : commentInfo.docTokens) {
            ApexNode<?> parent = docToken.nearestNode;
            if (parent != null) {
                ASTFormalComment comment = new ASTFormalComment(docToken.token);
                comment.calculateTextRegion(sourceContent);

                // move existing nodes so that we can insert the comment as the first node
                for (int i = parent.getNumChildren(); i > 0; i--) {
                    parent.jjtAddChild(parent.getChild(i - 1), i);
                }

                parent.jjtAddChild(comment, 0);
                comment.jjtSetParent(parent);
            }
        }
    }

    /**
     * Only remembers the node, to which the comment could belong.
     * Since the visiting order of the nodes does not match the source order,
     * the nodes appearing later in the source might be visiting first.
     * The correct node will then be visited afterwards, and since the distance
     * to the comment is smaller, it overrides the remembered node.
     *
     * @param node the potential parent node, to which the comment could belong
     */
    public void buildFormalComment(ApexNode<?> node) {
        if (!node.hasRealLoc()) {
            // Synthetic nodes such as "<clinit>" don't have a location in the
            // source code, since they are generated by the compiler
            return;
        }
        // find the token, that appears as close as possible before the node
        LineColumnPosition nodeBeginPosition = LineColumnPosition.beginOf(node);
        for (ApexDocToken docToken : commentInfo.docTokens) {
            if (LineColumnPosition.of(docToken.token).compareTo(nodeBeginPosition) > 0) {
                // this and all remaining tokens are after the node
                // so no need to check the remaining tokens.
                break;
            }

            if (docToken.nearestNode == null
                || nodeBeginPosition.compareTo(LineColumnPosition.beginOf(docToken.nearestNode)) < 0) {

                docToken.nearestNode = node;
            }
        }
    }

    private static CommentInformation extractInformationFromComments(TextFileContent sourceContent, String suppressMarker) {
        ApexLexer lexer = new ApexLexer(CharStreams.fromString(sourceContent.getNormalizedText()));

        ArrayList<Token> allCommentTokens = new ArrayList<>();
        Map<Integer, String> suppressMap = new HashMap<>();

        int lastStartIndex = -1;
        Token token = lexer.nextToken();

        boolean checkForCommentSuppression = suppressMarker != null;

        while (token.getType() != Token.EOF) {
            // Keep track of all comment tokens
            if (token.getChannel() == ApexLexer.COMMENT_CHANNEL) {
                assert lastStartIndex < token.getStartIndex()
                    : "Comments should be sorted";
                allCommentTokens.add(token);
            }

            if (checkForCommentSuppression && token.getType() == ApexLexer.LINE_COMMENT) {
                // check if it starts with the suppress marker
                String trimmedCommentText = token.getText().substring(2).trim();

                if (trimmedCommentText.startsWith(suppressMarker)) {
                    String userMessage = trimmedCommentText.substring(suppressMarker.length()).trim();
                    suppressMap.put(token.getLine(), userMessage);
                }
            }

            lastStartIndex = token.getStartIndex();
            token = lexer.nextToken();
        }

        return new CommentInformation(suppressMap, allCommentTokens);
    }

    private static class CommentInformation {

        final Map<Integer, String> suppressMap;
        final TokenListByPosition nonDocTokensByPosition;
        final List<ApexDocToken> docTokens;

        CommentInformation(Map<Integer, String> suppressMap, List<Token> allCommentTokens) {
            this.suppressMap = suppressMap;
            this.docTokens = allCommentTokens.stream()
                .filter((token) -> token.getType() == ApexLexer.DOC_COMMENT)
                .map((token) -> new ApexDocToken(token))
                .collect(toList());
            this.nonDocTokensByPosition = new TokenListByPosition(
                allCommentTokens.stream()
                    .filter((token) -> token.getType() != ApexLexer.DOC_COMMENT)
                    .collect(toList()));
        }
    }

    /**
     * List that maps comment tokens to their start index without copy.
     * This is used to implement a "binary search by key" routine which unfortunately isn't in the stdlib.
     *
     * <p>
     * Note that the provided token list must implement {@link RandomAccess}.
     */
    private static final class TokenListByPosition extends AbstractList<LineColumnPosition> implements RandomAccess {

        private final List<Token> tokens;

        TokenListByPosition(List<Token> tokens) {
            this.tokens = tokens;
        }

        @Override
        public LineColumnPosition get(int index) {
            Token token = tokens.get(index);
            return new LineColumnPosition(token.getLine(), token.getCharPositionInLine());
        }

        @Override
        public int size() {
            return tokens.size();
        }
    }

    private static class ApexDocToken {
        ApexNode<?> nearestNode;
        Token token;

        ApexDocToken(Token token) {
            this.token = token;
        }
    }

    public Map<Integer, String> getSuppressMap() {
        return commentInfo.suppressMap;
    }
}
