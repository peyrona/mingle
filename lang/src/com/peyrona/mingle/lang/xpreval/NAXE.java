
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.lexer.Lexer;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.operators.StdXprOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

// NEXT: Unicode: AFTER (“→” U+2192)   THROUGHOUT (WITHIN) (“↔” U+2194)

/**
 * NAXE : Not Another eXpression Evaluator (do not change this name).
 * <p>
 * An 'Ad hoc' Expression Evaluator for the Mingle Standard implementation.
 * <p>
 * Highlights:
 * <ul>
 *    <li>Operators have same precedence as MS-Excel and other spread-sheets: this makes things easier for rooky users.</li>
 *    <li>For the same reason, the Operator Associativity is same as MS-Excel and other spread-sheets.</li>
 *    <li>For the same reason, date and time objects work as similar as possible to MS-Excel and other spread-sheets.</li>
 *    <li>Maximum precision is Float (32 bit signed decimal): the standard in the Mingle platform (needed for embedded devices).</li>
 *    <li>Capable to evaluate expressions throughout time.</li>
 *    <li>Introducing an innovative human-friendly notation</li>
 * </ul>
 * <br>
 * Notes about expressions:
 * <ul>
 *  <li>An expression can can have no variables; e.g.: "TRUE || FALSE" or "3 + 4".</li>
 *  <li>An expression can be evaluated even if not all its variables have a value:<br>
 *      temperature > 20 || light == true<br>
 *      Only one of the 2 vars is needed to resolve the expr.</li>
 * </ul>
 * <br>
 * Implementation note: the first implementation was inspired by this work: https://github.com/uklimaschewski/EvalEx.
 * Thanks for sharing your code, man. :-)
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class NAXE implements IXprEval
{
    private EvalByAST                 evaluator    = null;    // Is null only when the expression is empty
    private List<ICandi.IError>       lstErrors    = null;
    private String                    sOriginal    = "";      // Original expression as arrived here (with 'ALL', ANY' etc.)
    private Function<String,String[]> fnGroupWise  = null;

    //------------------------------------------------------------------------//

    @Override
    public IXprEval build( String sXprInfix )
    {
        return build( sXprInfix, (Consumer) null, (Function) null );
    }

    @Override
    public IXprEval build( String sXprInfix, Consumer<Object> onSolved, Function<String,String[]> fnGroupWise )
    {
        this.sOriginal   = sXprInfix;
        this.fnGroupWise = fnGroupWise;

        Lexer lexer = new Lexer( sOriginal );

        addAll( lexer.getErrors() );

        if( getErrors().isEmpty() )
        {
            XprPreProc preproc = new XprPreProc( lexer.getLexemes(), fnGroupWise );

            addAll( preproc.getErrors() );

            if( getErrors().isEmpty() )
            {
                List<XprToken> lstInfix = preproc.getAsInfix();

                // Even if expressions not having Futures can be evaluated by EvalByRPN, it is preferreded to
                // use always EvalByAST beacuse it can make better optimizations (e.g. AST is lazy, RPN is not)

                evaluator = new EvalByAST( lstInfix, onSolved );

                addAll( evaluator.getErrors() );
            }
        }

        return this;
    }

    @Override
    public boolean set( String varName, Object varValue )
    {
        return evaluator.set( varName, varValue );
    }

    @Override
    public Object eval( String varName, Object varValue )
    {
        return evaluator.set( varName, varValue ) ? eval() : null;
    }

    @Override
    public Object eval()
    {
        try
        {
            return evaluator.eval();
        }
        catch( Exception exc )         // Only happens when improper use of this class
        {
            if( evaluator == null )    // null means empty expression
                return null;

            if( UtilColls.isNotEmpty( lstErrors ) )
                throw new MingleException( "Expression:"+ sOriginal +
                                           "\n has errors. Check '::getErrors()' prior to evalute" );    // This Exception is intended

            throw new MingleException( "Error evaluating:\n"+ sOriginal, exc );
        }
    }

    @Override
    public Map<String,Object> getVars()
    {
        return (evaluator == null) ? new HashMap<>()
                                   : evaluator.getVars();    // This is already unmodifiable
    }

    @Override
    public List<ICandi.IError> getErrors()   // This method can not call ::check()
    {
        return (lstErrors == null) ? new ArrayList<>() : Collections.unmodifiableList( lstErrors );
    }

    @Override
    public boolean isBoolean()
    {
        return evaluator.isBoolean();        // NullPointerException can be thrown: this is intended
    }

    @Override
    public boolean isFutureing()
    {
        return evaluator.isFutureing();
    }

    @Override
    public IXprEval cancel()
    {
        if( evaluator != null )
            evaluator.reset();

        return this;
    }

    @Override
    public void close()
    {
        if( evaluator != null )
            evaluator.shutdown();
    }

    @Override
    public boolean isBasicDataType( Object o )
    {
        return StdXprFns.isBasicType( o );
    }

    @Override
    public boolean isExtendedDataType( Object o )
    {
        return StdXprFns.isExtendedType( o );
    }

    @Override
    public String[] getOperators()
    {
        return new StdXprOps().getAll();
    }

    @Override
    public String[] getFunctions()
    {
        return StdXprFns.getAllFunctions();
    }

    @Override
    public Map<String,List<String>> getExtendedTypes()
    {
        return StdXprFns.getAllMethods();
    }

    @Override
    public String about()
    {
        return "NAXE: MSP Expressions Evaluator v.1.4 (the default one for the MSP)";
    }

    @Override
    public String toString()
    {
        return sOriginal;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 59 * hash + Objects.hashCode( evaluator );
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final NAXE other = (NAXE) obj;

        return Objects.equals( evaluator, other.evaluator );
    }

    //------------------------------------------------------------------------//
    // FOR DEBUGGING PURPOSES

    public String debug()
    {
        return "Received    : " + sOriginal +'\n'+
               "Preprocessed: " + new XprPreProc( new Lexer( sOriginal ).getLexemes(), fnGroupWise ) +'\n'+
               "Binary-Tree :\n"+ evaluator.toString();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void addAll( Collection<ICandi.IError> errors )
    {
        if( UtilColls.isEmpty( errors ) )
            return;

        if( lstErrors == null )
            lstErrors = new ArrayList<>();

        lstErrors.addAll( errors );
    }
}
