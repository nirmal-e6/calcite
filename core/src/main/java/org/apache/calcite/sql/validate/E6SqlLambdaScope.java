// custom SqlLambdaScope

package org.apache.calcite.sql.validate;

import org.apache.calcite.sql.*;
import org.apache.calcite.util.Litmus;

import static org.apache.calcite.util.Static.RESOURCE;

public class E6SqlLambdaScope extends SqlLambdaScope
{
    private final SqlLambda sqlLambda;
    public E6SqlLambdaScope(SqlValidatorScope parent, SqlLambda lambdaExpr)
    {
        super(parent, lambdaExpr);
        sqlLambda = lambdaExpr;
    }
    @Override
    public SqlQualified fullyQualify(SqlIdentifier identifier)
    {
        SqlIdentifier name = new SqlIdentifier(identifier.names.get(0), identifier.getParserPosition());
        boolean found = sqlLambda.getParameters()
                .stream()
                .anyMatch(param -> param.equalsDeep(name, Litmus.IGNORE));
        if (found) {
            return SqlQualified.create(this, 1, null, identifier);
        } else {
            throw validator.newValidationError(identifier,
                    RESOURCE.paramNotFoundInLambdaExpression(identifier.toString(), sqlLambda.toString()));
        }
    }
}
