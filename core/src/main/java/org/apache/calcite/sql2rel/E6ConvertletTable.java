/*
 * Copyright (c) 2025 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6ConvertletTable.java
 *
 * Created On: 2025-01-03
 */

package org.apache.calcite.sql2rel;

import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeFamily;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.runtime.SqlFunctions;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCastFunction;
import org.apache.calcite.sql.fun.SqlLibraryOperators;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlOperandTypeChecker;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.util.NlsString;
import org.apache.calcite.util.Pair;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.CAST;
import static org.apache.calcite.sql2rel.StandardConvertletTable.castToValidatedType;

public class E6ConvertletTable extends ReflectiveConvertletTable
{

public static final E6ConvertletTable INSTANCE = new E6ConvertletTable();

private E6ConvertletTable()
{
    super();
    registerOp(CAST, this::convertCast);
    registerOp(SqlLibraryOperators.SAFE_CAST, this::convertCast);
    registerOp(SqlLibraryOperators.TRY_CAST, this::convertCast);
    registerOp(SqlLibraryOperators.INFIX_CAST, this::convertCast);
}

/**
 * Aggregate function kinds that the native executor handles natively.
 * Using SqlKind-based matching ensures alias operators (e.g. VARIANCE → VAR_SAMP,
 * STDDEV → STDDEV_SAMP) are caught regardless of which operator object the parser resolves to.
 */
private static final Set<SqlKind> PRESERVE_AGG_KINDS = EnumSet.of(
    SqlKind.AVG,
    SqlKind.VAR_POP,
    SqlKind.VAR_SAMP,
    SqlKind.STDDEV_POP,
    SqlKind.STDDEV_SAMP,
    SqlKind.COVAR_POP,
    SqlKind.COVAR_SAMP);

/**
 * Convertlet that preserves the aggregate call as-is (no expansion into SUM/COUNT).
 * Builds a RexCall with the original operator so that HistogramShuttle can wrap it
 * in a RexOver with the window specification.
 */
private static final SqlRexConvertlet PASSTHROUGH_AGG_CONVERTLET =
    (cx, call) ->
    {
        final List<RexNode> exprs =
            convertOperands(cx, call, SqlOperandTypeChecker.Consistency.NONE);
        return cx.getRexBuilder().makeCall(
            cx.getValidator().getValidatedNodeType(call),
            call.getOperator(),
            exprs);
    };

private static final SqlRexConvertlet ARRAY_CONVERTLET =
    (cx, call) -> StandardConvertletTable.INSTANCE.convertCall(cx, call);

private static final SqlRexConvertlet GREATEST_CONVERTLET = new GreatestConvertlet();

/**
 * Intercepts aggregate functions by SqlKind when running on the native executor,
 * returning a passthrough convertlet that prevents StandardConvertletTable's
 * AvgVarianceConvertlet from decomposing them into SUM/COUNT expressions.
 *
 * For non-window aggregates, AggregateReduceFunctionsRule in the optimizer will
 * still decompose them if needed (controlled separately by CalciteForkSettings.nativeExecutor()).
 */
@Override
public @Nullable SqlRexConvertlet get(SqlCall call)
{
    SqlKind kind = call.getKind();
    if (CalciteForkSettings.nativeExecutor() && PRESERVE_AGG_KINDS.contains(kind))
    {
        return PASSTHROUGH_AGG_CONVERTLET;
    }
    switch (kind)
    {
    case GREATEST:
    case LEAST:
        return GREATEST_CONVERTLET;
    case VARIANT_GET:
    case TRY_VARIANT_GET:
    case COLON:
        return this::convertVariantGet;
    case FROM_JSON:
        return this::convertFromJson;
    case ARRAY_VALUE_CONSTRUCTOR:
        return ARRAY_CONVERTLET;
    default:
        return super.get(call);
    }
}

/** Convertlet that converts {@code GREATEST} and {@code LEAST}. */
private static class GreatestConvertlet implements SqlRexConvertlet {
    @Override public RexNode convertCall(SqlRexContext cx, SqlCall call) {
        // Translate
        //   GREATEST(a, b, c, d)
        // to
        //   CASE
        //   WHEN a IS NULL OR b IS NULL OR c IS NULL OR d IS NULL
        //   THEN NULL
        //   WHEN a > b AND a > c AND a > d
        //   THEN a
        //   WHEN b > c AND b > d
        //   THEN b
        //   WHEN c > d
        //   THEN c
        //   ELSE d
        //   END
        final RexBuilder rexBuilder = cx.getRexBuilder();
        final RelDataType type =
            cx.getValidator().getValidatedNodeType(call);
        final SqlBinaryOperator op;
        switch (call.getKind()) {
            case GREATEST:
                op = SqlStdOperatorTable.GREATER_THAN;
                break;
            case LEAST:
                op = SqlStdOperatorTable.LESS_THAN;
                break;
            default:
                throw new AssertionError();
        }
        final List<RexNode> exprs =
            convertOperands(cx, call, SqlOperandTypeChecker.Consistency.NONE);
        final List<RexNode> list = new ArrayList<>();
        final List<RexNode> orList = new ArrayList<>();
        for (RexNode expr : exprs) {
            orList.add(
                rexBuilder.makeCall(call.getParserPosition(), SqlStdOperatorTable.IS_NULL, expr));
        }
        list.add(RexUtil.composeDisjunction(rexBuilder, orList));
        list.add(rexBuilder.makeNullLiteral(type));
        for (int i = 0; i < exprs.size() - 1; i++) {
            RexNode expr = exprs.get(i);
            final List<RexNode> andList = new ArrayList<>();
            for (int j = i + 1; j < exprs.size(); j++) {
                final RexNode expr2 = exprs.get(j);
                andList.add(rexBuilder.makeCall(call.getParserPosition(), op, expr, expr2));
            }
            list.add(RexUtil.composeConjunction(rexBuilder, andList));
            list.add(expr);
        }
        list.add(exprs.get(exprs.size() - 1));
        return rexBuilder.makeCall(call.getParserPosition(), type, SqlStdOperatorTable.CASE, list);
    }
}

private static List<RexNode> convertOperands(SqlRexContext cx,
    SqlCall call, SqlOperandTypeChecker.Consistency consistency) {
    List<SqlNode> operandList;
    if (call.getOperator() instanceof SqlTableFunction) {
        // skip set semantic table node of table function
        operandList =
            call.getOperandList().stream().filter(
                    operand -> operand.getKind() != SqlKind.SET_SEMANTICS_TABLE)
                .collect(Collectors.toList());
    } else {
        operandList = call.getOperandList();
    }
    return convertOperands(cx, call, operandList, consistency);
}

private static List<RexNode> convertOperands(SqlRexContext cx,
    SqlCall call, List<SqlNode> nodes,
    SqlOperandTypeChecker.Consistency consistency) {
    final List<RexNode> exprs = new ArrayList<>();
    for (SqlNode node : nodes) {
        exprs.add(cx.convertExpression(node));
    }
    final List<RelDataType> operandTypes =
        cx.getValidator().getValidatedOperandTypes(call);
    if (operandTypes != null) {
        final List<RexNode> oldExprs = new ArrayList<>(exprs);
        exprs.clear();
        Pair.forEach(oldExprs, operandTypes, (expr, type) ->
            exprs.add(cx.getRexBuilder().ensureType(call.getParserPosition(), type, expr, true)));
    }
    if (exprs.size() > 1) {
        final RelDataType type =
            consistentType(cx, consistency, RexUtil.types(exprs));
        if (type != null) {
            final List<RexNode> oldExprs = new ArrayList<>(exprs);
            exprs.clear();
            for (RexNode expr : oldExprs) {
                exprs.add(cx.getRexBuilder().ensureType(call.getParserPosition(), type, expr, true));
            }
        }
    }
    return exprs;
}

private static @Nullable RelDataType consistentType(SqlRexContext cx,
    SqlOperandTypeChecker.Consistency consistency, List<RelDataType> types) {
    switch (consistency) {
        case COMPARE:
            if (SqlTypeUtil.areSameFamily(types)) {
                // All arguments are of same family. No need for explicit casts.
                return null;
            }
            final List<RelDataType> nonCharacterTypes = new ArrayList<>();
            for (RelDataType type : types) {
                if (type.getFamily() != SqlTypeFamily.CHARACTER) {
                    nonCharacterTypes.add(type);
                }
            }
            if (!nonCharacterTypes.isEmpty()) {
                final int typeCount = types.size();
                types = nonCharacterTypes;
                if (nonCharacterTypes.size() < typeCount) {
                    final RelDataTypeFamily family =
                        nonCharacterTypes.get(0).getFamily();
                    if (family instanceof SqlTypeFamily) {
                        // The character arguments might be larger than the numeric
                        // argument. Give ourselves some headroom.
                        switch ((SqlTypeFamily) family) {
                            case INTEGER:
                            case NUMERIC:
                                nonCharacterTypes.add(
                                    cx.getTypeFactory().createSqlType(SqlTypeName.BIGINT));
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
            // fall through
        case LEAST_RESTRICTIVE:
            return cx.getTypeFactory().leastRestrictive(types);
        default:
            return null;
    }
}

protected RexNode convertVariantGet(SqlRexContext cx, final SqlCall call)
{
    final RexBuilder rexBuilder = cx.getRexBuilder();

    // convert the first two operands
    final List<RexNode> operands = convertOperands(cx, call, call.getOperandList().subList(0, 2),
        SqlOperandTypeChecker.Consistency.NONE);
    final List<RexNode> exprs = new ArrayList<>(operands);

    // convert the third operand (type)
    SqlDataTypeSpec dataType = call.operand(2);
    RelDataType type = dataType.deriveType(cx.getValidator());

    if (type.getSqlTypeName().equals(SqlTypeName.ANY))
    {
        // We fall back to ANY type in the parser when there's no cast specified.
        // In that case, we assume that no cast is happening and the cast type is the first operand's type.
        type = exprs.get(0).getType();
    }

    return rexBuilder.makeCall(type, call.getOperator(), exprs);
}

protected RexNode convertFromJson(SqlRexContext cx, final SqlCall call)
{
    final RexBuilder rexBuilder = cx.getRexBuilder();

    // convert the third operand
    int operandCount = call.operandCount();
    final List<RexNode> exprs = new ArrayList<RexNode>()
    {
        {
            // convert the first operand
            addAll(
                convertOperands(cx, call, call.getOperandList().subList(0, 1), SqlOperandTypeChecker.Consistency.NONE));
            if (operandCount == 3)
            {
                // convert the third operand
                addAll(convertOperands(cx, call, call.getOperandList().subList(2, 3),
                    SqlOperandTypeChecker.Consistency.NONE));
            }
        }
    };

    RelDataType type = cx.getValidator().getValidatedNodeType(call);

    return rexBuilder.makeCall(type, call.getOperator(), exprs);
}

private RexNode convertCast(@UnknownInitialization E6ConvertletTable this, SqlRexContext cx, final SqlCall call)
{
    RelDataTypeFactory typeFactory = cx.getTypeFactory();
    final SqlValidator validator = cx.getValidator();
    final SqlKind kind = call.getKind();
    checkArgument(kind == SqlKind.CAST || kind == SqlKind.SAFE_CAST, kind);
    final boolean safe = kind == SqlKind.SAFE_CAST;
    SqlNode left = call.operand(0);
    final SqlNode right = call.operand(1);
    final SqlLiteral format = call.getOperandList().size() > 2 ? call.operand(2)
                                                               : SqlLiteral.createNull(SqlParserPos.ZERO);

    final RexBuilder rexBuilder = cx.getRexBuilder();
    RexNode arg = cx.convertExpression(left);
    final RexLiteral formatArg = (RexLiteral) cx.convertLiteral(format);

    if (right instanceof SqlIntervalQualifier)
    {
        final SqlIntervalQualifier intervalQualifier = (SqlIntervalQualifier) right;
        if (left instanceof SqlIntervalLiteral)
        {
            RexLiteral sourceInterval = (RexLiteral) cx.convertExpression(left);
            BigDecimal sourceValue = (BigDecimal) sourceInterval.getValue();
            RexLiteral castedInterval = rexBuilder.makeIntervalLiteral(sourceValue, intervalQualifier);
            return castToValidatedType(call.getParserPosition(), call, castedInterval, validator, rexBuilder, safe);
        }
        else if (left instanceof SqlNumericLiteral)
        {
            RexLiteral sourceInterval = (RexLiteral) cx.convertExpression(left);
            BigDecimal sourceValue = requireNonNull(sourceInterval.getValueAs(BigDecimal.class), "sourceValue");
            final BigDecimal multiplier = intervalQualifier.getUnit().multiplier;
            RexLiteral castedInterval = rexBuilder.makeIntervalLiteral(SqlFunctions.multiply(sourceValue, multiplier),
                intervalQualifier);
            return castToValidatedType(call.getParserPosition(), call, castedInterval, validator, rexBuilder, safe);
        }
        RexNode value = cx.convertExpression(left);
        return castToValidatedType(call.getParserPosition(), call, value, validator, rexBuilder, safe);
    }

    SqlDataTypeSpec dataType = (SqlDataTypeSpec) right;
    RelDataType type = SqlCastFunction.deriveType(cx.getTypeFactory(), arg.getType(), dataType.deriveType(validator),
        safe);

    // changes by E6Data
    // to trim string of boolean characters
    // below are some of the examples
    // cast('yes ' to boolean) -> cast('yes' to boolean)
    // cast(' no' to boolean) -> cast('no' to boolean)
    // cast(' y' to boolean) -> cast('y' to boolean)
    // cast('n' to boolean) -> cast('n' to boolean)
    if(SqlTypeUtil.isBoolean(type) && left instanceof SqlCharStringLiteral && !type.isNullable()) {
        NlsString nlsString = (NlsString) ((SqlCharStringLiteral) left).getValue();
        if(nlsString != null) {
            String value = nlsString.getValue();
            String trimmed = value.trim();
            left = SqlLiteral.createCharString(trimmed, nlsString.getCharsetName(), left.getParserPosition());
            arg = cx.convertExpression(left);
        }
    }

    if (SqlUtil.isNullLiteral(left, false))
    {
        validator.setValidatedNodeType(left, type);
        return cx.convertExpression(left);
    }
    if (null != dataType.getCollectionsTypeName())
    {
        RelDataType argComponentType = arg.getType().getComponentType();

        // arg.getType() may be ANY
        if (argComponentType == null)
        {
            argComponentType = dataType.getComponentTypeSpec().deriveType(validator);
        }

        RexNode finalArg = arg;
        requireNonNull(argComponentType, () -> "componentType of " + finalArg);

        RelDataType typeFinal = type;
        final RelDataType componentType = requireNonNull(type.getComponentType(),
            () -> "componentType of " + typeFinal);
        if (argComponentType.isStruct() && !componentType.isStruct())
        {
            RelDataType tt = typeFactory.builder()
                .add(argComponentType.getFieldList().get(0).getName(), componentType)
                .build();
            tt = typeFactory.createTypeWithNullability(tt, componentType.isNullable());
            boolean isn = type.isNullable();
            type = typeFactory.createMultisetType(tt, -1);
            type = typeFactory.createTypeWithNullability(type, isn);
        }
    }
    return rexBuilder.makeCast(call.getParserPosition(), type, arg, safe, safe, formatArg);
}

} /// ////// End of class
