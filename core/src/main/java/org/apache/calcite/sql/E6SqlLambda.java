/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6SqlLambda.java
 *
 * Created On: 2024-09-09
 */

// custom Sql Lambda

package org.apache.calcite.sql;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.E6SqlLambdaScope;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import java.util.List;

import static org.apache.calcite.sql.SqlPivot.stripList;
import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A <code>E6SqlLambda</code> is a node of a parse tree which
 * represents a lambda expression.
 */
public class E6SqlLambda extends SqlLambda
{

public static final SqlOperator OPERATOR = new E6SqlLambda.E6SqlLambdaOperator();

public E6SqlLambda(SqlParserPos pos, SqlNodeList parameters,
    SqlNode expression) {
    super(pos, parameters, expression);
}

@Override public SqlOperator getOperator()
{
    return OPERATOR;
}

/**
 * The {@code SqlLambdaOperator} represents a lambda expression.
 * The syntax :
 * {@code IDENTIFIER -> EXPRESSION} or {@code (IDENTIFIER, IDENTIFIER, ...) -> EXPRESSION}.
 */
private static class E6SqlLambdaOperator extends SqlSpecialOperator {

    E6SqlLambdaOperator() {
        super("->", SqlKind.LAMBDA);
    }

    @Override
    public void unparse(SqlWriter writer, SqlCall call, int leftPrec, int rightPrec)
    {
        SqlNodeList parameters = call.operand(0);
        SqlNode expression = call.operand(1);
        if (parameters.size() != 1) {
            writer.list(SqlWriter.FrameTypeEnum.PARENTHESES, SqlWriter.COMMA, stripList(parameters));
        } else {
            parameters.unparse(writer, leftPrec, rightPrec);
        }
        writer.keyword(OPERATOR.getName());
        expression.unparse(writer, leftPrec, rightPrec);
    }

    @Override public RelDataType deriveType(
        SqlValidator validator, SqlValidatorScope scope, SqlCall call) {
        final E6SqlLambda lambdaExpr = (E6SqlLambda) call;
        final E6SqlLambdaScope lambdaScope = (E6SqlLambdaScope) scope;
        final List<String> paramNames = lambdaExpr.getParameters().stream()
            .map(SqlNode::toString)
            .collect(toImmutableList());
        final List<RelDataType> paramTypes = lambdaScope.getParameterTypes().values()
            .stream()
            .collect(toImmutableList());
        final RelDataType paramRowType =
            validator.getTypeFactory().createStructType(paramTypes, paramNames);
        final RelDataType returnType = validator.getValidatedNodeType(lambdaExpr.getExpression());
        return validator.getTypeFactory().createFunctionSqlType(paramRowType, returnType);
    }
}

} ///////// End of class
