/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package groovyx.jupyter;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;

/**
 * Lifts top-level variable declarations in a cell ({@code def x = 1},
 * {@code int x = 1}) into plain assignments, so the values land in the shared
 * session {@link groovy.lang.Binding} and persist across cells — the classic
 * notebook expectation, which plain Groovy script semantics do not provide
 * (script locals evaporate when the cell ends).
 * <p>
 * Rules:
 * <ul>
 *   <li>only statements at the top level of the cell body are lifted — locals
 *       inside blocks, loops, closures, methods and classes keep normal scoping;</li>
 *   <li>typed declarations keep their coercion via an {@code as}-style cast of
 *       the initializer (the static type itself is not tracked — binding
 *       variables are dynamic);</li>
 *   <li>annotated declarations (e.g. {@code @Field}) are left untouched;</li>
 *   <li>multi-assignment declarations ({@code def (a, b) = ...}) are left
 *       untouched (cell-local) for now;</li>
 *   <li>declarations without an initializer get the type's default value.</li>
 * </ul>
 */
public class DeclarationLifting extends CompilationCustomizer {

    public DeclarationLifting() {
        super(CompilePhase.CONVERSION);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (!classNode.isScript()) {
            return;
        }
        // the statement objects are shared with the script class's run() body,
        // so mutating them in place transforms the generated script
        for (Statement statement : source.getAST().getStatementBlock().getStatements()) {
            if (!(statement instanceof ExpressionStatement)) {
                continue;
            }
            ExpressionStatement exprStatement = (ExpressionStatement) statement;
            if (!(exprStatement.getExpression() instanceof DeclarationExpression)) {
                continue;
            }
            DeclarationExpression decl = (DeclarationExpression) exprStatement.getExpression();
            if (!decl.getAnnotations().isEmpty() || decl.isMultipleAssignmentDeclaration()) {
                continue;
            }
            VariableExpression declared = decl.getVariableExpression();
            Expression init = decl.getRightExpression();
            if (init == null || init instanceof EmptyExpression) {
                init = defaultValueFor(declared.getOriginType());
            } else if (!declared.isDynamicTyped()) {
                init = CastExpression.asExpression(declared.getOriginType(), init);
            }
            VariableExpression target = new VariableExpression(declared.getName());
            target.setSourcePosition(declared);
            BinaryExpression assignment = new BinaryExpression(
                    target,
                    Token.newSymbol(Types.ASSIGN, decl.getLineNumber(), decl.getColumnNumber()),
                    init);
            assignment.setSourcePosition(decl);
            exprStatement.setExpression(assignment);
        }
    }

    private static Expression defaultValueFor(ClassNode type) {
        if (ClassHelper.isPrimitiveBoolean(type)) {
            return ConstantExpression.PRIM_FALSE;
        }
        if (ClassHelper.isPrimitiveChar(type)) {
            return new ConstantExpression('\0');
        }
        if (ClassHelper.isPrimitiveType(type)) {
            return new ConstantExpression(0);
        }
        return ConstantExpression.NULL;
    }
}
