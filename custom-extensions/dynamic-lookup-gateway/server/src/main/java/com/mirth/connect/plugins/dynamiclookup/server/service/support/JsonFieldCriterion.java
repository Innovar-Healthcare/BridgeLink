/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service.support;

public class JsonFieldCriterion {
    private String expression;
    private String typeCheckSql;
    private String operatorSql;
    private Object value;
    private String valueSql;

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getTypeCheckSql() {
        return typeCheckSql;
    }

    public void setTypeCheckSql(String typeCheckSql) {
        this.typeCheckSql = typeCheckSql;
    }

    public String getOperatorSql() {
        return operatorSql;
    }

    public void setOperatorSql(String operatorSql) {
        this.operatorSql = operatorSql;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getValueSql() {
        return valueSql;
    }

    public void setValueSql(String valueSql) {
        this.valueSql = valueSql;
    }
}
