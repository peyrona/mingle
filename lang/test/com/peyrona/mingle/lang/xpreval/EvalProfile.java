/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V.1 using RPN
 * Simple Expression Speed Test
 * -----------------------------
 * 500,000 operations in 133 millis
 * In other words: 3,759 ops/millis
 *
 * Complex Expression Speed Test
 * -----------------------------
 * 500,000 operations in 978 millis
 * In other words: 511 ops/millis
 *
 *=====================================
 *
 * V.2 using RPN
 * Simple Expression Speed Test
 * -----------------------------
 * 500,000 operations in 83 millis
 * In other words: 6,000 ops/millis
 *
 * Complex Expression Speed Test
 * -----------------------------
 * Same as V.1
 *
 *=====================================
 *
 * V.2 using AST
 * Simple Expression Speed Test
 * =============================
 * 500,000 operations in 15 millis
 * In other words: 33,333 ops/millis
 *
 * Complex Expression Speed Test
 * =============================
 * 500,000 operations in 595 millis
 * In other words: 840 ops/millis
 *
 *=====================================
 *
 * V.3 using AST
 * Simple Expression Speed Test
 * =============================
 * 500,000 operations in 12 millis
 * In other words: 41,666 ops/millis
 *
 * Medium complexity Expression Speed Test
 * =======================================
 * 500,000 operations in 94 millis
 * In other words: 5,319 ops/millis
 *
 * Complex Expression Speed Test
 * =============================
 * 500,000 operations in 372 millis
 * In other words: 1,344 ops/millis
 *
 * @author francisco
 */
public class EvalProfile
{
    /**
     * This method is not executed by JUnit: it has to be executed manually.
     * This is a speed test.
     *
     * @param as
     */
    public static void main( String[] as )
    {
        Map<String,Pair<String,Object>> mapXprAndVars = new HashMap<>();

        mapXprAndVars.put( "12 + 5"              , null );
        mapXprAndVars.put( "12 + (5 * 3)"        , null );
        mapXprAndVars.put( "(4*5):floor()"       , null );
        mapXprAndVars.put( "(4*5):floor() + aVar", new Pair( "aVar", 5 ) );
        mapXprAndVars.put( "date(\"2021-04-10\"):move(aVar):day() == 12", new Pair( "aVar", 6 ) );
        mapXprAndVars.put( "aVar == 6 AFTER  4000", new Pair( "aVar", 6 ) );
        mapXprAndVars.put( "aVar == 6 WITHIN 4000", new Pair( "aVar", 6 ) );
        mapXprAndVars.put( "(aVar == 6 AFTER 4000) && (aVar == 6 WITHIN 4000)", new Pair( "aVar", 6 ) );

        for( Map.Entry<String,Pair<String,Object>> entry : mapXprAndVars.entrySet() )
        {
            String xpr = entry.getKey();
            String var = (entry.getValue() == null) ? null : entry.getKey();
            Object val = (entry.getValue() == null) ? null : entry.getValue();

            Lexer          lexer;
            List<XprToken> lstInfix  = null;
            EvalByAST      evaluator = null;

            long start, end, itera = 500000;

            System.out.println( xpr );

            // Parsing (paring and AST creation) -----------------------------------

            for( int n = 0; n < (itera/100); n++ )    // JIT warm-up
            {
                lexer    = new Lexer( xpr );
                lstInfix = (new XprPreProc( lexer.getLexemes(), null )).getAsInfix();
            }

            start = System.currentTimeMillis();       // Parsing

            for( int n = 0; n < itera; n++ )
            {
                lexer    = new Lexer( xpr );
                lstInfix = (new XprPreProc( lexer.getLexemes(), null )).getAsInfix();
            }

            end = System.currentTimeMillis();

            System.out.println( new DecimalFormat("###,###").format( itera ) +" parseings in "+ new DecimalFormat("###,###").format(end - start) + " millis");
            System.out.println( "In other words: "+ new DecimalFormat("###,###.###").format( (itera / (end - start)) ) + " ops/millis");

            // Compilation (paring and AST creation) -----------------------------------

            for( int n = 0; n < (itera/100); n++ )    // JIT warm-up
                evaluator = new EvalByAST( lstInfix, null );

            start = System.currentTimeMillis();       // The real one

            for( int n = 0; n < itera; n++ )
                evaluator = new EvalByAST( lstInfix, null );

            end = System.currentTimeMillis();

            System.out.println( new DecimalFormat("###,###").format( itera ) +" compilations in "+ new DecimalFormat("###,###").format(end - start) + " millis");
            System.out.println( "In other words: "+ new DecimalFormat("###,###.###").format( (itera / (end - start)) ) + " ops/millis");

            // Evauation of the expression ---------------------------------------------

            if( xpr.contains( "AFTER" ) || xpr.contains( "WITHIN" ) )
            {
                System.out.println( "Expression not able to be evaluated" );
                continue;
            }

            for( int n = 0; n < (itera/100); n++ )    // JIT warm-up
                evaluator.eval();

            if( var != null )
                evaluator.set( var, val );

            start = System.currentTimeMillis();       // The real one

            for( int n = 0; n < itera; n++ )
                evaluator.eval();

            end = System.currentTimeMillis();

            System.out.println( new DecimalFormat("###,###").format( itera ) +" operations in "+ new DecimalFormat("###,###").format(end - start) + " millis");
            System.out.println( "In other words: "+ new DecimalFormat("###,###.###").format( (itera / (end - start)) ) + " ops/millis");

            System.out.println( "\n-----------------------------------------------------\n" );
        }
    }
}
