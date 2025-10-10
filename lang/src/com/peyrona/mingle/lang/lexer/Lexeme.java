
package com.peyrona.mingle.lang.lexer;

import com.peyrona.mingle.lang.interfaces.ITokenable;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;

/**
 * Merely a data structure that holds lexemes recognized by the Lexer.
 * <p>
 * To know why there is not a TYPE_OPERATOR_UNARY, refer to Lexer documentation.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Lexeme implements ITokenable
{
    int    line   = -1;    // 1 based (same as in Lexer)
    int    column = -1;    // 1 based (same as in Lexer)
    int    offset = -1;
    String text   = null;
    short  type   = -1;

    static final short TYPE_BOOLEAN     =  0;
    static final short TYPE_NUMBER      =  1;
    static final short TYPE_STRING      =  2;    // Everything between: '"' and '"'
    static final short TYPE_EXTENDED    =  3;    // date, time, list, pair
    static final short TYPE_OPERATOR    =  4;    // Arithmetic, Booleean or Relational operator
    static final short TYPE_PARENTHESIS =  5;    // '(' or ')'
    static final short TYPE_DELIMITER   =  6;    // A separator (';') or new-line ('\n') in clauses that admit one or more elements. ie.: INIT name = value [, ...]  NOTE: can not be ','
    static final short TYPE_INLINE_CODE =  7;    // Everything between: '{' and its corresponding '}'
    static final short TYPE_NAME        =  8;
    static final short TYPE_CMD_WORD    =  9;    // All reserverd words related with commands. Including: AFTER, WITHIN, ANY and ALL
    static final short TYPE_UNIT_SUFFIX = 10;    // Time and temperature unit suffixes
    static final short TYPE_ERROR       = 11;

    //------------------------------------------------------------------------//
    // PUBLIC STATIC

    /**
     * Allows to manipulate 'ad hoc' Lists of Lexemes after the Lexer was executed.
     *
     * @param c One of: '\n', '(', ')', ','
     * @return A new Lexeme (no line, or column or offset).
     */
    public static Lexeme build( char c )
    {
        assert c == '\n' || c == '(' || c == ')' || c == ',';

        Lexeme lex = new Lexeme();
               lex.text = String.valueOf( c );
               lex.type = (c == '\n') ? TYPE_DELIMITER
                                      : (c == ',') ? TYPE_OPERATOR
                                                   : TYPE_PARENTHESIS;

        return lex;
    }

    public static Lexeme build( String s )
    {
        return new Lexeme().updateUsign( s );
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    Lexeme()
    {
    }

    //------------------------------------------------------------------------//
    // BY IMPLEMENTEING ITokenable

    @Override
    public int line()      // Readonly outside of this package
    {
        return line;
    }

    @Override
    public int column()    // Readonly outside of this package
    {
        return column;
    }

    @Override
    public String text()   // Changeable using ::updateUsign(...)
    {
        return text;
    }

    public int offset()    // Readonly outside of this package
    {
        return offset;
    }

    //------------------------------------------------------------------------//

    /**
     * Updates only the internal 'text' and set the type as STRING.
     *
     * @param value
     * @return
     */
    public Lexeme updateAsStr( String value )
    {
        text = value;
        type = TYPE_STRING;

        return this;
    }

    /**
     * Updates the internal 'text' and 'type' properties.
     *
     * @param value
     * @return
     */
    public Lexeme updateUsign( Object value )
    {
        if( value.equals( UtilType.toUne( text ) ) )     // Sometimes it happens that a tool wants to change a result of an operation that at end is the same value as it is already: this 'if' checks it
            return this;

        if( isInline() )
        {
            text = value.toString();
            return this;
        }

        // After a Lexeme has changed its content (:text) type has to be updated: in case it would change.

        if( ! isChangedByBasics( value ) )
        {
            if( value instanceof String )
                value = UtilType.toUne( (String) value );

            if( ! isChangedByBasics( value ) )
                type = Lexeme.TYPE_STRING;
        }

        this.text = isExtendedDataType() ? UtilType.toJson( value ).toString()
                                         : value.toString();
        return this;
    }

    public boolean isText( String text )
    {
        return this.text.equalsIgnoreCase( text );
    }

    public boolean isBasicDataType()
    {
        return (type == TYPE_BOOLEAN) ||
               (type == TYPE_NUMBER)  ||
               (type == TYPE_STRING);
    }

    /**
     * Returns true if not Boolean or Number or String but is one of the special data types: date, time, list, pair.
     *
     * @return true if not Boolean or Number or String but is one of the special data types: date, time, list, pair.
     */
    public boolean isExtendedDataType()
    {
        return (type == TYPE_EXTENDED);
    }

    public boolean isUneDataType()
    {
        return isBasicDataType() || isExtendedDataType();
    }

    public boolean isQuote()
    {
        return (text.length() == 1) && (text.charAt( 0 ) == Language.QUOTE);
    }

    public boolean isUnitSuffix()
    {
        return type == TYPE_UNIT_SUFFIX;
    }

    public boolean isBoolean()
    {
        return type == TYPE_BOOLEAN;
    }

    public boolean isOperator()
    {
        return type == TYPE_OPERATOR;
    }

    /**
     * Returns true if this Lexeme is Une parameter separator (',').
     *
     * @return true if this Lexeme is Une parameter separator (',').
     */
    public boolean isParamSep()
    {
        return (type != TYPE_STRING) && Language.isParamSep( text );
    }

    public boolean isParenthesis()
    {
        return type == TYPE_PARENTHESIS;
    }

    public boolean isOpenParenthesis()
    {
        return type == TYPE_PARENTHESIS && text.equals( "(" );
    }

    public boolean isClosedParenthesis()
    {
        return type == TYPE_PARENTHESIS && text.equals( ")" );
    }

    public boolean isCommandWord()
    {
        return type == TYPE_CMD_WORD;
    }

    public boolean isName()
    {
        return type == TYPE_NAME;
    }

    public boolean isString()
    {
        return type == TYPE_STRING;
    }

    public boolean isNumber()
    {
        return type == TYPE_NUMBER;
    }

    public boolean isInline()
    {
        return type == TYPE_INLINE_CODE;
    }

    /**
     * Returns true if ::text == cEoL || ::text == ';'
     *
     * @return true if ::text == cEoL || ::text == ';'
     */
    public boolean isDelimiter()
    {
        return type == TYPE_DELIMITER;
    }

    /**
     * Returns true if ::text == cEoL
     *
     * @return true if ::text == cEoL
     */
    public boolean isEoL()
    {
        return (type == TYPE_DELIMITER) &&
               (text != null)           &&
               (text.length() == 1)     &&
               (text.charAt(0) == Language.END_OF_LINE);
    }

    public boolean isError()
    {
        return (type == TYPE_ERROR);
    }

    public boolean equivalent( Lexeme lex )
    {
        return (text != null)     &&
               (type == lex.type) &&
               text.equalsIgnoreCase( lex.text );
    }

    public Lexeme clonar()
    {
        Lexeme lex = new Lexeme();
               lex.text = text;
               lex.type = type;

        return lex;
    }

    //------------------------------------------------------------------------//

    @Override
    public int hashCode()
    {
        int hash = 5;
            hash = 13 * hash + this.line;
            hash = 13 * hash + this.column;
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        return this.offset == ((Lexeme) obj).offset;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private IXprEval xprEval = null;

    private boolean isChangedByBasics( Object value )
    {
        String toStr = value.toString();

        if( (toStr.length() == 1) && ((toStr.charAt( 0 ) == Language.END_OF_LINE) || (toStr.charAt( 0 ) == Language.DELIMITER)) )
        {
            type = TYPE_DELIMITER;
            return true;
        }

        if( Language.isOperator(    toStr )         )  { type = Lexeme.TYPE_OPERATOR;    return true; }     // e.g.: USE "EQUALS" AS "=="
        if( Language.isCmdWord(     toStr )         )  { type = Lexeme.TYPE_CMD_WORD;    return true; }     // e.g.: USE "REGLA"  AS "RULE"
        if( Language.isParenthesis( toStr )         )  { type = Lexeme.TYPE_PARENTHESIS; return true; }
        if( Language.isValidName(   toStr ) == null )  { type = Lexeme.TYPE_NAME;        return true; }
        if( value instanceof String                 )  { type = Lexeme.TYPE_STRING;      return true; }     // ----------------------------------------->>>> Following must be at end <<<<
        if( value instanceof Boolean                )  { type = Lexeme.TYPE_BOOLEAN;     return true; }     // e.g.: USE "YES"    AS "TRUE"
        if( value instanceof Number                 )  { type = Lexeme.TYPE_NUMBER;      return true; }     // e.g.: USE "PI"     AS "3.14159"

        if( xprEval == null )
        {
            synchronized( this )
            {
                if( xprEval == null )
                    xprEval = UtilSys.getConfig().newXprEval();                                             // Created only when needed
            }
        }

        if( xprEval.isExtendedDataType( value )     )  { type = Lexeme.TYPE_EXTENDED;    return true; }     // e.g.: USE "HOUR"   AS "date():hour()"

        return false;
    }
}