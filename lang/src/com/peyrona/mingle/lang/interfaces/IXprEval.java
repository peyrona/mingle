
package com.peyrona.mingle.lang.interfaces;

import com.peyrona.mingle.lang.MingleException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This interface represents an expression evaluator with the minimum needed requirements.
 * <p>
 * Different CIL implementations could include their own expression evaluator with more
 * functionality.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IXprEval
{
    /**
     * Set the expression to be evaluated.
     * <p>
     * This is equivalent to <code>build( String infixExpr, null, null )</code><br>
     * In this case the expression evaluator will behave as traditional ones: it will evaluate
     * the expression and will immediately return the result. No groups can be involved, neither
     * futures (AFTER or WITHIN).
     * <br>
     * It is IXprEval responsibility to check if expression has errors.
     *
     * @param infixXpr An infix Une valid expression to be evaluated.
     * @return Itself.
     * @throws MingleException If expression is empty. For any other error, it is returned by ::getErrors().
     */
    IXprEval build( String infixXpr ) throws MingleException;

    /**
     * Set the expression to be evaluated.
     * <p>
     * The Consumer parameter is mandatory for expressions containing AFTER and/or WITHIN clauses.<br>
     * Because these type of expressions can not be evaluated immediately, the Consumer will be invoked
     * as soon as the result of the evaluation produces a result, and only then: only Une Basic Data
     * Types (Boolean, Number and String) can be passed to the Consumer (never pass null).<br>
     * For "normal" expressions (those that do not include AFTER and/or WITHIN), this parameter is
     * optional.<br>
     * If expression does not include 'ANY', neither 'ALL' directives, the fnGroupWise parameter is not
     * needed and can be passed as NULL.
     * <br>
     * It is IXprEval responsibility to check if expression has errors.
     *
     * @param infiXxpr An infix Une valid expression to be evaluated.
     * @param onSolved Invoked after the expression is fully evaluated, receiving the result. Can be null.
     * @param fnGroupWise An instance that complies with IRuntime interface. Needed only if the expression uses ALL or ANY.
     * @return Itself.
     * @throws MingleException If expression is empty. For any other error, it is returned by ::getErrors().
     */
    IXprEval build( String infiXxpr, Consumer<Object> onSolved, Function<String,String[]> fnGroupWise ) throws MingleException;

    /**
     * Updates the internal variable value (only if the variable is part of the expression).
     * <p>
     * It returns true if passed variable is involved in the expression.
     *
     * @param varName name
     * @param varValue value
     * @return true if passed variable is involved in the expression.
     */
    boolean set( String varName, Object varValue );

    /**
     * Evaluates the expression, which only occurs if all variables involved in the expression has already
     * a value and ::getErrors() returns an empty Map.<br>
     * <br>
     *
     * @return The result returned by the expression or null if:
     * <ul>
     *    <li>The expression has errors (this can be checked using <code>::getErrors()</code>)</li>
     *    <li>The expression is not ready to be evaluated: one or more variables are not yet initialized</li>
     *    <li>The expression include AFTER or WITHIN: in these cases onSolved will be invoked at due time.</li>
     * </ul>
     * @see #set(java.lang.String, java.util.function.Consumer)
     * @see #getErrors()
     */
    Object eval();

    /**
     * Adds a variable name with its value or updates the value if the variable already existed and invokes ::eval().<br>
     * <br>
     * It is IXprEval responsibility to check if expression has errors prior to its evaluation.
     *
     * @param varName
     * @param varValue
     * @return The result of evaluating the expression or null if:
     * <ul>
     *    <li>The expression has errors (this can be checked using <code>::getErrors()</code>)</li>
     *    <li>The expression is not ready to be evaluated: one or more variables are not yet initialized</li>
     *    <li>The expression is not affected by passed variable (the variable is not part of the expression)</li>
     *    <li>The expression include AFTER or WITHIN: in these cases onSolved will be invoked at due time.</li>
     * </ul>
     *
     * @see #eval()
     */
    Object eval( String varName, Object varValue );

    /**
     * Returns all variables (names and their current value) associated with the expression
     * or an empty map if the expression has no variables.
     *
     * @return All variables (names and their current value) associated with the expression
     *         or an empty map if the expression has no variables.
     */
    Map<String,Object> getVars();

    /**
     * Returns a List with all errors found in passed expression.
     * <p>
     * Instances of ICandi.IError
     * 
     * @return A List with all errors found in passed expression.
     */
    List<ICandi.IError> getErrors();

    /**
     * Returns true if the value returned by the expression is of type boolean.
     * <p>
     * An expression is considered a boolean expression, if the last operator or function is boolean.
     *
     * @return <code>true</code> true if the value returned by the expression is of type boolean.
     */
    boolean isBoolean();

    /**
     * Returns true if this expression has futures (AFTER and/or WITHIN) and they are on progress.
     *
     * @return true if this expression has futures (AFTER and/or WITHIN) and they are on progress.
     */
    boolean isFutureing();

    /**
     * Cancels an expression that is being evaluated.
     *
     * @return Itself.
     */
    IXprEval cancel();

    /**
     * Closes the expression evaluator and releases any resources it holds.
     */
    void close();

    /**
     * Returns true if this Expression Evaluator recognizes 'oValue' as an Une basic
     * data type: Boolean, Number or String.
     *
     * @param oValue Value to check.
     * @return true if this Expression Evaluator can handle passed type of data.
     */
    boolean isBasicDataType( Object oValue );

    /**
     * Returns true if this Expression Evaluator recognizes 'oValue' as an Une extended
     * data type; at least one of following: date, time, list, pair.
     *
     * @param oValue Value to check.
     * @return true if this Expression Evaluator can handle passed type of data.
     */
    boolean isExtendedDataType( Object oValue );

    /**
     * Returns all operators managed by this Expressions Evaluator.
     *
     * @return All operators managed by this Expressions Evaluator.
     */
    String[] getOperators();

    /**
     * Returns all functions managed by this Expressions Evaluator.
     *
     * @return All functions managed by this Expressions Evaluator.
     */
    String[] getFunctions();

    /**
     * Returns all classes and classes' methods managed by this Expressions Evaluator.<br>
     * <br>
     * Note: returned map 'key' is the extended data name and the 'value' is the list of the associated method's names.
     *
     * @return All classes and classes' methods managed by this Expressions Evaluator.
     */
    Map<String,List<String>> getExtendedTypes();

    /**
     * Returns information about this Expression Evaluator implementation.
     * @return Information about this Expression Evaluator implementation
     */
    String about();
}