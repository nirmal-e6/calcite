/*
 * Copyright (c) 2024 Uniphi Inc
 * All rights reserved.
 *
 * File Name: E6SqlSnapshot.java
 *
 * Created On: 2024-09-09
 */

package org.apache.calcite.sql;

import org.apache.calcite.config.CalciteForkSettings;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.sql.validate.implicit.AbstractTypeCoercion;
import org.apache.calcite.sql.validate.implicit.E6TypeCoersionHelper;
import org.apache.calcite.sql.validate.implicit.TypeCoercion;
import org.apache.calcite.sql.validate.implicit.TypeCoercionImpl;
import org.apache.calcite.util.ImmutableNullableList;
import org.apache.calcite.util.NlsString;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Parse tree node for "{@code FOR SYSTEM_TIME AS OF}" temporal clause.
 */
public class E6SqlSnapshot extends SqlCall
{

private static final int OPERAND_TABLE_REF = 0;
private static final int OPERAND_PERIOD = 1;
private static final int OPERAND_TYPE = 2;

public static final String TYPE_TIMESTAMP = "TIMESTAMP";
public static final String TYPE_VERSION = "VERSION";

//~ Instance fields -------------------------------------------

private SqlNode tableRef;
private SqlNode period;
private SqlCharStringLiteral type; //TIMESTAMP, VERSION

/** Creates a SqlSnapshot. */
public E6SqlSnapshot(SqlParserPos pos, SqlNode tableRef, SqlNode period, SqlCharStringLiteral type) {
    super(pos);
    this.tableRef = Objects.requireNonNull(tableRef, "tableRef");
    this.period = Objects.requireNonNull(period, "period");
    this.type = Objects.requireNonNull(type, "type");
}

// ~ Methods

@Override public SqlOperator getOperator() {
    return E6SqlSnapshot.E6SqlSnapshotOperator.INSTANCE;
}

@Override public List<SqlNode> getOperandList() {
    return ImmutableNullableList.of(tableRef, period, type);
}

public SqlNode getTableRef() {
    return tableRef;
}

public SqlNode getPeriod() {
    return period;
}

public SqlCharStringLiteral getType()
{
    return type;
}

@Override public void setOperand(int i, @Nullable SqlNode operand) {
    switch (i) {
        case OPERAND_TABLE_REF:
            tableRef = Objects.requireNonNull(operand, "operand");
            break;
        case OPERAND_PERIOD:
            period = Objects.requireNonNull(operand, "operand");
            break;
        case OPERAND_TYPE:
            type = (SqlCharStringLiteral) Objects.requireNonNull(operand, "operand");
            break;
        default:
            throw new AssertionError(i);
    }
}

@Override public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    getOperator().unparse(writer, this, 0, 0);
}

public void validate(SqlValidatorScope scope) throws CalciteContextException
{
    RelDataType dataType = scope.getValidator().deriveType(requireNonNull(scope, "scope"), period);
    SqlTypeFamily targetFamily;
    switch (getType().getValueAs(NlsString.class).getValue()) {
        case TYPE_TIMESTAMP:
            targetFamily = SqlTypeFamily.TIMESTAMP;
            break;
        case TYPE_VERSION:
            targetFamily = SqlTypeFamily.INTEGER;
            break;
        default:
            throw CalciteForkSettings.validationException(period,
                "Invalid type expected for snapshot, this shouldn't happen ever");
    }

    // In case it's null fail it
    if(SqlTypeUtil.isNull(dataType) || SqlTypeUtil.containsNullable(dataType))
    {
        throw CalciteForkSettings.validationException(period, "The time travel specification expects ''{0}'' type but is NULL",
            targetFamily, dataType);
    }

    if(targetFamily.contains(dataType))
    {
        return;
    }

    TypeCoercion typeCoercion = scope.getValidator().getTypeCoercion();
    RelDataTypeFactory factory = scope.getValidator().getTypeFactory();
    RelDataType targetType;
    switch (getType().getValueAs(NlsString.class).getValue()) {
        case TYPE_TIMESTAMP:
            targetType = factory.createSqlType(SqlTypeName.TIMESTAMP_TZ);
            break;
        case TYPE_VERSION:
            targetType = factory.createSqlType(SqlTypeName.BIGINT);
            break;
        default:
            throw CalciteForkSettings.validationException(period,
                "Invalid type expected for snapshot, this shouldn't happen ever");
    }

    if(!SqlTypeUtil.canCastFrom(targetType, dataType, true))
    {
        throw CalciteForkSettings.validationException(period, "The time travel specification expects ''{0}'' type but is ''{1}''",
            targetFamily, dataType);
    }

    if(!E6TypeCoersionHelper.needToCast(scope, period, targetType, (AbstractTypeCoercion) typeCoercion))
    {
        return;
    }

    E6TypeCoersionHelper.coerceOperandType(scope, this, OPERAND_PERIOD, targetType, (AbstractTypeCoercion) typeCoercion);

}

/**
 * An operator describing a FOR SYSTEM_TIME specification.
 */
public static class E6SqlSnapshotOperator extends SqlOperator {

    public static final E6SqlSnapshot.E6SqlSnapshotOperator INSTANCE = new E6SqlSnapshot.E6SqlSnapshotOperator();

    private E6SqlSnapshotOperator() {
        super("SNAPSHOT", SqlKind.SNAPSHOT, 2, true, null, null, null);
    }

    @Override public SqlSyntax getSyntax() {
        return SqlSyntax.SPECIAL;
    }

    @SuppressWarnings("argument.type.incompatible")
    @Override public SqlCall createCall(
        @Nullable SqlLiteral functionQualifier,
        SqlParserPos pos,
        @Nullable SqlNode... operands) {
        assert functionQualifier == null;
        assert operands.length == 3;
        return new E6SqlSnapshot(pos, operands[0], operands[1], (SqlCharStringLiteral) operands[2]);
    }

    @Override public <R> void acceptCall(
        SqlVisitor<R> visitor,
        SqlCall call,
        boolean onlyExpressions,
        SqlBasicVisitor.ArgHandler<R> argHandler) {
        if (onlyExpressions) {
            List<SqlNode> operands = call.getOperandList();
            // skip the first operand
            for (int i = 1; i < operands.size(); i++) {
                argHandler.visitChild(visitor, call, i, operands.get(i));
            }
        } else {
            super.acceptCall(visitor, call, false, argHandler);
        }
    }

    @Override public void unparse(
        SqlWriter writer,
        SqlCall call,
        int leftPrec,
        int rightPrec) {
        final E6SqlSnapshot snapshot = (E6SqlSnapshot) call;
        SqlNode tableRef = snapshot.tableRef;

        if (tableRef instanceof SqlBasicCall
            && ((SqlBasicCall) tableRef).getOperator() instanceof SqlAsOperator) {
            SqlBasicCall basicCall = (SqlBasicCall) tableRef;
            basicCall.operand(0).unparse(writer, 0, 0);
            writer.setNeedWhitespace(true);
            writeSnapshot(writer, snapshot);
            writer.keyword("AS");
            basicCall.operand(1).unparse(writer, 0, 0);
        } else {
            tableRef.unparse(writer, 0, 0);
            writeSnapshot(writer, snapshot);
        }
    }

    private static void writeSnapshot(SqlWriter writer, E6SqlSnapshot snapshot) {
        String type = snapshot.type.getValueAs(NlsString.class).getValue();
        switch (type.toUpperCase())
        {
            case TYPE_TIMESTAMP:
                writer.keyword("AT (TIMESTAMP => ");
                break;
            case TYPE_VERSION:
                writer.keyword("AT (VERSION => ");
                break;
            default:
                throw new RuntimeException("Unknown type: " + type);
        }
        snapshot.period.unparse(writer, 0, 0);
        writer.keyword(")");
    }
}

} ///////// End of class
