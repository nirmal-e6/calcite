package org.apache.calcite.sql.parser;

import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlUtil;

import static org.apache.calcite.util.Static.RESOURCE;

/**
 * expanded parser to use enum ExprContext
 */
public abstract class E6SqlAbstractParserImpl extends SqlAbstractParserImpl
{

/**
 * Type-safe enum for context of acceptable expressions.
 */
protected enum ExprContext
{
    /**
     * Accept any kind of expression in this context.
     */
    ACCEPT_ALL,

    /**
     * Accept any kind of expression in this context, with the exception of CURSOR constructors.
     */
    ACCEPT_NONCURSOR,

    /**
     * Accept only query expressions in this context.
     *
     * <p>Valid: "SELECT x FROM a",
     * "SELECT x FROM a UNION SELECT y FROM b", "TABLE a", "VALUES (1, 2), (3, 4)", "(SELECT x FROM a UNION SELECT y
     * FROM b) INTERSECT SELECT z FROM c", "(SELECT x FROM a UNION SELECT y FROM b) ORDER BY 1 LIMIT 10". Invalid: "e
     * CROSS JOIN d". Debatable: "(SELECT x FROM a)".
     */
    ACCEPT_QUERY,

    /**
     * Accept only query expressions or joins in this context.
     *
     * <p>Valid: "(SELECT x FROM a)",
     * "e CROSS JOIN d", "((SELECT x FROM a) CROSS JOIN d)", "((e CROSS JOIN d) LEFT JOIN c)". Invalid: "e, d", "SELECT
     * x FROM a", "(e)".
     */
    ACCEPT_QUERY_OR_JOIN,

    /**
     * Accept only non-query expressions in this context.
     */
    ACCEPT_NON_QUERY,

    /**
     * Accept only parenthesized queries or non-query expressions in this context.
     */
    ACCEPT_SUB_QUERY,

    /**
     * Accept only CURSOR constructors, parenthesized queries, or non-query expressions in this context.
     */
    ACCEPT_CURSOR;

    @Deprecated // to be removed before 2.0
    public static final ExprContext ACCEPT_SUBQUERY = ACCEPT_SUB_QUERY;

    @Deprecated // to be removed before 2.0
    public static final ExprContext ACCEPT_NONQUERY = ACCEPT_NON_QUERY;

    public void throwIfNotCompatible(SqlNode e)
    {
        switch (this)
        {
            case ACCEPT_NON_QUERY:
            case ACCEPT_SUB_QUERY:
            case ACCEPT_CURSOR:
                if (e.isA(SqlKind.QUERY))
                {
                    throw SqlUtil.newContextException(e.getParserPosition(), RESOURCE.illegalQueryExpression());
                }
                break;
            case ACCEPT_QUERY:
                if (!e.isA(SqlKind.QUERY))
                {
                    throw SqlUtil.newContextException(e.getParserPosition(), RESOURCE.illegalNonQueryExpression());
                }
                break;
            case ACCEPT_QUERY_OR_JOIN:
                if (!e.isA(SqlKind.QUERY) && e.getKind() != SqlKind.JOIN)
                {
                    throw SqlUtil.newContextException(e.getParserPosition(), RESOURCE.expectedQueryOrJoinExpression());
                }
                break;
            default:
                break;
        }
    }

    public void throwIfNotCompatible(SqlNode e, boolean isParenthesisedTable)
    {
        boolean isQuery = e.isA(SqlKind.QUERY);
        switch (this)
        {
            case ACCEPT_NON_QUERY:
            case ACCEPT_SUB_QUERY:
            case ACCEPT_CURSOR:
                if (isQuery)
                {
                    throw SqlUtil.newContextException(e.getParserPosition(), RESOURCE.illegalQueryExpression());
                }
                break;
            case ACCEPT_QUERY:
                if (!isQuery)
                {
                    throw SqlUtil.newContextException(e.getParserPosition(), RESOURCE.illegalNonQueryExpression());
                }
                break;
            // copied code of SqlAbstractParserImpl.ExprContext for passing table inside parenthesis
            // example "from (table)"
            case ACCEPT_QUERY_OR_JOIN:
                boolean isJoin = e.getKind() == SqlKind.JOIN;
                boolean isNotJoin = !isQuery && !isJoin;
                boolean isNotTableWithParenthesis = !isQuery && !isParenthesisedTable;
                if (isNotJoin && isNotTableWithParenthesis)
                {
                    throw SqlUtil.newContextException(e.getParserPosition(), RESOURCE.expectedQueryOrJoinExpression());
                }
                break;
            default:
                break;
        }
    }
}

}
