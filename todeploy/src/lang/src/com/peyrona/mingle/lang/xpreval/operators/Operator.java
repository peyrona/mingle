
package com.peyrona.mingle.lang.xpreval.operators;

import com.peyrona.mingle.lang.japi.UtilStr;

/**
 * This class instances represent operators: Arithmetic, Relational and Conditional.
 * <p>
 * In Une, all operators has same precedence, therefore, this class do not need
 * 'Associativity' flag.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 * @param <T> Returned type.
 */
public abstract class Operator<T>
{
    /**
     * Send operator precedence: ':'
     */
    static final short PRECEDENCE_SEND = 80;

    /**
     * Unary operator precedence: '!' prefix
     */
    static final short PRECEDENCE_UNARY = 60;

    /**
     * Not operator precedence: !
     */
    static final short PRECEDENCE_NOT = PRECEDENCE_UNARY;

    /**
     * Power operator precedence: ^
     */
    static final short PRECEDENCE_POWER = 40;

    /**
     * Multiplicative operators precedence: *,/,%
     */
    static final short PRECEDENCE_MULTIPLICATIVE = 30;

    /**
     * Additive operators precedence: + and -
     */
    static final short PRECEDENCE_ADDITIVE = 20;

    /**
     * Comparative operators bitwise shift: & , | , >< , ~ , << , >>
     */
    static final short PRECEDENCE_BITWISE = 15;

    /**
     * Comparative operators precedence: <,>,<=,>=
     */
    static final short PRECEDENCE_COMPARISON = 10;

    /**
     * Equality operators precedence: =, ==, !=. <>
     */
    static final short PRECEDENCE_EQUALITY = 7;

    /**
     * And operator precedence: &&
     */
    static final short PRECEDENCE_AND = 4;

    /**
     * Or operator precedence: ||
     */
    static final short PRECEDENCE_OR = 2;

    /**
     * Assignment operator precedence: =
     */
    static final short PRECEDENCE_ASSIGNMENT = 0;

    //------------------------------------------------------------------------//

    public final short   precedence;
    public final boolean isLeftAssoc;

    //------------------------------------------------------------------------//

    /**
     * Constructor.
     *
     * @param precedence
     */
    protected Operator( short precedence )
    {
        this( precedence, true );
    }

    protected Operator( short precedence, boolean isLeftAssoc )
    {
        this.precedence  = precedence;
        this.isLeftAssoc = isLeftAssoc;   // In Une, the '!' op is the only one that is right associative.
    }                                     // (the '-' (complement op) is solved in a different way).

    //------------------------------------------------------------------------//

    /**
	 * Lazily evaluates this function.
	 *
     * @param <T>
	 * @param args The accepted parameters.
	 * @return The lazy result of this function.
	 */
	abstract <T> T eval( Object... args );

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }
}