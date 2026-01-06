package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Lexer;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for Expression Evaluator (NAXE/AST).
 * Tests arithmetic, boolean, string, functions, variables, classes, and temporal operators.
 *
 * @author Francisco José Morero Peyrona
 */
public class EvalTest
{
    //------------------------------------------------------------------------//
    // ARITHMETIC TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Arithmetic Operation Tests")
    class ArithmeticTests
    {
        @Test
        @DisplayName("Should evaluate basic numeric literals")
        void testNumericLiterals()
        {
            test( "12", 12f );
            test( ".2 + .3", 0.5f );
            test( "1_000_000 == 1000000", true );
        }

        @Test
        @DisplayName("Should evaluate basic arithmetic operations")
        void testBasicArithmetic()
        {
            test( "12 + 3", 15f );
            test( "12 - 3", 9f );
            test( "12 * 3", 36f );
            test( "12 / 3", 4f );
            test( "12 % 200", 24f );
            test( "12 ^ 3", 1728f );
        }

        @Test
        @DisplayName("Should handle unary operators")
        void testUnaryOperators()
        {
            test( "-12 + (2*+4) + 22", 18f );
            test( "(-12 + (+2*4) + 27) / 5", 4.6f );
        }

        @Test
        @DisplayName("Should handle string to number conversion")
        void testStringToNumberConversion()
        {
            test( "\"10\" * 2", 20f );
            test( "-8 + \"+10\" * -2", -28f );
        }

        @Test
        @DisplayName("Should handle division by zero")
        void testDivisionByZero()
        {
            test( "+10 / 0", Float.NaN );
        }
    }

    //------------------------------------------------------------------------//
    // BITWISE TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Bitwise Operation Tests")
    class BitwiseTests
    {
        @Test
        @DisplayName("Should evaluate bitwise AND")
        void testBitwiseAnd()
        {
            test( "5 & 3", 1f );
        }

        @Test
        @DisplayName("Should evaluate bitwise OR")
        void testBitwiseOr()
        {
            test( "5 | 3", 7f );
        }

        @Test
        @DisplayName("Should evaluate bitwise XOR")
        void testBitwiseXor()
        {
            test( "5 >< 3", 6f );
        }

        @Test
        @DisplayName("Should evaluate bitwise NOT")
        void testBitwiseNot()
        {
            test( "~5", -6f );
        }

        @Test
        @DisplayName("Should evaluate left shift")
        void testLeftShift()
        {
            test( "5 << 2", 20f );
        }

        @Test
        @DisplayName("Should evaluate right shift")
        void testRightShift()
        {
            test( "12 >> 2", 3f );
        }
    }

    //------------------------------------------------------------------------//
    // BOOLEAN TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Boolean Operation Tests")
    class BooleanTests
    {
        @Test
        @DisplayName("Should evaluate boolean literals")
        void testBooleanLiterals()
        {
            test( "true", true );
            test( "false", false );
        }

        @Test
        @DisplayName("Should evaluate logical operators")
        void testLogicalOperators()
        {
            test( "true || false", true );
            test( "true && false", false );
            test( "true && ! false", true );
        }

        @Test
        @DisplayName("Should evaluate logical expressions with variables")
        void testLogicalWithVariables()
        {
            test( "(var1 && ! var2) || var3", true,
                  new Pair( "var1", true ),
                  new Pair( "var2", false ),
                  new Pair( "var3", true ) );

            test( "var1 && ! (var2 && var3)", true,
                  new Pair( "var1", true ),
                  new Pair( "var2", false ),
                  new Pair( "var3", false ) );
        }

        @Test
        @DisplayName("Should evaluate comparison operators")
        void testComparisonOperators()
        {
            test( "8 * 2 == 16", true );
            test( "8 * 2 != 16", false );
            test( "! (8 * 2 != 16)", true );
            test( "2 < 22", true );
        }

        @Test
        @DisplayName("Should evaluate complex boolean expressions")
        void testComplexBooleanExpressions()
        {
            test( "(2 < 22) && ! (8 < 2)", true );
            test( "(2 < 22) || (4 > 5)", true );
            test( "(2 < 22) && (4 > 5)", false );
            test( "(2 < 22) |& (4 > 5)", true );  // One true, one false
        }

        @Test
        @DisplayName("Should evaluate string comparisons")
        void testStringComparisons()
        {
            test( "\"caco\" <= \"malo\"", true );
            test( "\"caco\" >= \"malo\"", false );
            test( "\"caco\" < \"malo\" && (2 < 22)", true );
            test( "\"caco\" > \"malo\" && (2 < 22)", false );
        }

        @Test
        @DisplayName("Should evaluate string equality")
        void testStringEquality()
        {
            test( "\"caco\" == \"caco\"", true );
            test( "\"caco\" == \"CACO\"", true );  // Case-insensitive by default
            test( "\"caco\":equals( \"CACO\" )", false );  // Case-sensitive
            test( "equals( \"caco\", \"caco\", \"caco\" )", true );
        }

        @Test
        @DisplayName("Should evaluate isEmpty function")
        void testIsEmpty()
        {
            test( "! isEmpty( \"\" )", false );
        }

        @Test
        @DisplayName("Should handle mixed logical and bitwise operations")
        void testMixedLogicalBitwise()
        {
            test( "(((5 | 3) == 7) && ((5 & 3) == 1)) || (2 > 8) && (3 < 5)", true );
        }

        @Test
        @DisplayName("Should evaluate ALL quantifier")
        void testAllQuantifier()
        {
            Function<String,String[]> fn = (t) -> new String[] {"win1", "win2", "win3"};
            IXprEval xpr;

            // All true
            xpr = new NAXE().build( "ALL windows == TRUE", null, fn );
            xpr.set( "win1", true );
            xpr.set( "win2", true );
            xpr.set( "win3", true );
            assertEquals( Boolean.TRUE, xpr.eval() );

            // Not all true
            xpr = new NAXE().build( "ALL windows == TRUE", null, fn );
            xpr.set( "win1", true );
            xpr.set( "win2", false );
            xpr.set( "win3", true );
            assertEquals( Boolean.FALSE, xpr.eval() );
        }

        @Test
        @DisplayName("Should evaluate ANY quantifier")
        void testAnyQuantifier()
        {
            Function<String,String[]> fn = (t) -> new String[] {"win1", "win2", "win3"};
            IXprEval xpr;

            // Any true
            xpr = new NAXE().build( "ANY windows == TRUE", null, fn );
            xpr.set( "win1", false );
            xpr.set( "win2", false );
            xpr.set( "win3", true );
            assertEquals( Boolean.TRUE, xpr.eval() );

            // None true
            xpr = new NAXE().build( "ANY windows == FALSE", null, fn );
            xpr.set( "win1", true );
            xpr.set( "win2", true );
            xpr.set( "win3", true );
            assertEquals( Boolean.FALSE, xpr.eval() );
        }

        @Test
        @DisplayName("Should evaluate complex quantifier expressions")
        void testComplexQuantifiers()
        {
            Function<String,String[]> fn = (t) ->
                t.equals("doors") ? new String[] {"door1", "door2"} : new String[] {"win1", "win2"};

            IXprEval xpr = new NAXE().build( "(! ALL doors == TRUE) && (ANY window == FALSE)", null, fn );
            xpr.set( "door1", false );
            xpr.set( "door2", true );
            xpr.set( "win1", true );
            xpr.set( "win2", false );
            assertEquals( Boolean.TRUE, xpr.eval() );
        }

        @Test
        @DisplayName("Should support lazy boolean evaluation")
        void testLazyEvaluation()
        {
            // Left lazy OR
            assertEquals( Boolean.TRUE, new NAXE().build( "true || x > 5" ).eval() );

            // Left lazy AND
            assertEquals( Boolean.FALSE, new NAXE().build( "1 > 2 && x > 5" ).eval() );

            // Right lazy OR
            assertEquals( Boolean.TRUE, new NAXE().build( "2 > 1 || x > 5" ).eval() );

            // Right lazy AND
            assertEquals( Boolean.FALSE, new NAXE().build( "x > 5 && 1 > 2" ).eval() );

            // Undefined variable results in null
            assertNull( new NAXE().build( "1 > 2 || x > 5" ).eval() );
            assertNull( new NAXE().build( "2 > 1 && x > 5" ).eval() );
        }

        @Test
        @DisplayName("Should evaluate truthy/falsy numbers")
        void testTruthyFalsyNumbers()
        {
            // Zero is falsy
            test( "0 && true", false );
            test( "!0", true );
            test( "0.0 && true", false );

            // Non-zero is truthy
            test( "1 && true", true );
            test( "!1", false );
            test( "-1 || false", true );
            test( "42 && true", true );
            test( "0.001 && true", true );

            IXprEval xpr = new NAXE().build( "variable", null, null );
                     xpr.set( "variable", 1.0f );
            assertEquals( Boolean.TRUE, xpr.eval() );
                     xpr.set( "variable", 0.0f );
            assertEquals( Boolean.FALSE, xpr.eval() );
        }

        @Test
        @DisplayName("Should evaluate truthy/falsy strings")
        void testTruthyFalsyStrings()
        {
            // Empty string is falsy
            test( "\"\" && true", false );
            test( "!\"\"", true );

            // Non-empty string is truthy
            test( "\"hello\" && true", true );
            test( "!\"hello\"", false );
            test( "\" \" || false", true );  // Whitespace is non-empty

            IXprEval xpr = new NAXE().build( "variable", null, null );
                     xpr.set( "variable", "ABC" );
            assertEquals( Boolean.TRUE, xpr.eval() );
                     xpr.set( "variable", "" );
            assertEquals( Boolean.FALSE, xpr.eval() );
        }

        @Test
        @DisplayName("Should evaluate truthy/falsy lists")
        void testTruthyFalsyLists()
        {
            // Empty list is falsy
            test( "list() && true", false );
            test( "!list()", true );

            // Non-empty list is truthy
            test( "list(1,2,3) && true", true );
            test( "!list(1)", false );

            IXprEval xpr = new NAXE().build( "variable", null, null );
                     xpr.set( "variable", new list(1,2,3) );
            assertEquals( Boolean.TRUE, xpr.eval() );
                     xpr.set( "variable", new list() );
            assertEquals( Boolean.FALSE, xpr.eval() );
        }

        @Test
        @DisplayName("Should evaluate truthy/falsy pairs")
        void testTruthyFalsyPairs()
        {
            // Empty pair is falsy
            test( "pair() && true", false );
            test( "!pair()", true );

            // Non-empty pair is truthy
            test( "pair(\"a\",1) && true", true );
            test( "!pair(\"key\",\"value\")", false );

            IXprEval xpr = new NAXE().build( "variable", null, null );
                     xpr.set( "variable", new pair("one",1,"two",2) );
            assertEquals( Boolean.TRUE, xpr.eval() );
                     xpr.set( "variable", new pair() );
            assertEquals( Boolean.FALSE, xpr.eval() );
        }

        @Test
        @DisplayName("Should short-circuit with truthy/falsy values")
        void testTruthyFalsyShortCircuit()
        {
            // AND short-circuits on falsy expressions (using comparisons that return falsy results)
            assertEquals( Boolean.FALSE, new NAXE().build( "(1-1) && true" ).eval() );     // 0 is falsy
            assertEquals( Boolean.FALSE, new NAXE().build( "list():isEmpty() && false" ).eval() );  // true && false = false

            // OR short-circuits on truthy expressions
            assertEquals( Boolean.TRUE, new NAXE().build( "(1+1) || false" ).eval() );    // 2 is truthy
            assertEquals( Boolean.TRUE, new NAXE().build( "list(1):size() || false" ).eval() );  // 1 is truthy

            // Verify falsy values don't short-circuit OR (allows evaluation to continue)
            test( "0 || true", true );
            test( "\"\" || true", true );
            test( "list() || true", true );

            // Verify truthy values don't short-circuit AND (allows evaluation to continue)
            test( "1 && false", false );
            test( "\"x\" && false", false );
            test( "list(1) && false", false );
        }

        @Test
        @DisplayName("Should evaluate XOR with truthy/falsy values")
        void testTruthyFalsyXOR()
        {
            test( "1 |& 0", true );       // truthy XOR falsy = true
            test( "1 |& 1", false );      // truthy XOR truthy = false
            test( "0 |& 0", false );      // falsy XOR falsy = false
            test( "\"hello\" |& \"\"", true );  // non-empty XOR empty = true
        }

        @Test
        @DisplayName("XOR must evaluate both sides (cannot short-circuit)")
        void testXorNoShortCircuit()
        {
            // XOR requires both operands - undefined variable should result in null
            assertNull( new NAXE().build( "true |& x" ).eval() );   // must eval x
            assertNull( new NAXE().build( "x |& false" ).eval() );  // must eval x
            assertNull( new NAXE().build( "false |& x" ).eval() );  // must eval x (can't short-circuit even when left is falsy)
        }
    }

    //------------------------------------------------------------------------//
    // STRING TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("String Operation Tests")
    class StringTests
    {
        @Test
        @DisplayName("Should evaluate string literals")
        void testStringLiterals()
        {
            test( "\"caco malo\"", "caco malo" );
        }

        @Test
        @DisplayName("Should concatenate strings")
        void testStringConcatenation()
        {
            test( "\"caco \" + \"malo\"", "caco malo" );
            test( "\"12\" + \"34\"", "1234" );
        }

        @Test
        @DisplayName("Should handle string subtraction")
        void testStringSubtraction()
        {
            test( "\"caco \" + \"malo\" - \"o\"", "cac mal" );
            test( "\"1234\" - \"3\"", "124" );
        }

        @Test
        @DisplayName("Should handle mixed string and number operations")
        void testMixedStringNumber()
        {
            test( "\"12\" + 34", 46f );
            test( "\"1234\" - 3", 1231f );
            test( "\"12\" * \"3\"", 36f );
            test( "\"12\" / \"3\"", 4f );
        }
    }

    //------------------------------------------------------------------------//
    // MATH FUNCTION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Math Function Tests")
    class MathFunctionTests
    {
        @Test
        @DisplayName("Should evaluate floor function")
        void testFloor()
        {
            test( "floor(2.8,2)", 2.0f );
            test( "2.8:floor(2)", 2.0f );
            test( "floor(3.7, 2)", 2.0f );
            test( "floor(-2.5, -2)", -2.0f );
            test( "floor(1.58, 0.1)", 1.5f );
        }

        @Test
        @DisplayName("Should evaluate ceiling function")
        void testCeiling()
        {
            test( "ceiling(2.2,1)", 3.0f );
            test( "2.2:ceiling(1)", 3.0f );
            test( "ceiling(2.5, 1)", 3.0f );
            test( "ceiling(-2.5, -2)", -2.0f );
            test( "ceiling(1.5, 0.1)", 1.5f );
            test( "ceiling(0.234, 0.01)", 0.24f );
        }

        @Test
        @DisplayName("Should evaluate isBetween function")
        void testIsBetween()
        {
            test( "isBetween(1,0,2)", true );
            test( "isBetween(0.0,0.0,2.0)", true );
            test( "isBetween(2.0,0.0,2.0)", true );
        }

        @Test
        @DisplayName("Should evaluate setBetween function")
        void testSetBetween()
        {
            test( "setBetween(3,0,2)", 2.0f );
        }

        @Test
        @DisplayName("Should evaluate mod function")
        void testMod()
        {
            test( "mod(10,3)", 1.0f );
        }

        @Test
        @DisplayName("Should evaluate int function with different bases")
        void testInt()
        {
            test( "int(\"0b11000\") + 1", 25.0f );
            test( "int(\"0x18ABC\") > 1", true );
        }

        @Test
        @DisplayName("Should evaluate round function")
        void testRound()
        {
            test( "round(22.3)", 22.0f );
            test( "22.3:round()", 22.0f );
            test( "round(2.15, 1)", 2.2f );
            test( "round(2.149, 1)", 2.1f );
            test( "round(-1.475, 2)", -1.47f );
            test( "round(626.3, 3)", 626.3f );
            test( "round(1.98,-1)", 0.0f );
            test( "round(-50.55,-2)", -100.0f );
        }

        @Test
        @DisplayName("Should evaluate abs function")
        void testAbs()
        {
            test( "abs(-3)", 3.0f );
        }

        @Test
        @DisplayName("Should evaluate rand function")
        void testRand()
        {
            test( "rand(0,100) >= 0", true );
        }

        @Test
        @DisplayName("Should evaluate min and max functions")
        void testMinMax()
        {
            test( "min( 4, floor( ceiling( 7.6, 7 ), 7 ) )", 4f );
            test( "7.6:ceiling(7):floor(7):min( 4 )", 4f );
            test( "10 + max( 3, 5*2 )", 20f );
            test( "10 + 3:MAX( 5*2 )", 20f );
            test( "10 + max( min( 13, 15 ), 5*2 )", 23f );
            test( "max( 5, 10, 15, 20 )", 20f );
            test( "min( 4, max( 2, min( 7, 9 ) ) )", 4f );
            test( "4:min( 5 )", 4f );
            test( "min( 5, 10, 15, 20 )", 5f );
        }

        @Test
        @DisplayName("Should evaluate format function")
        void testFormat()
        {
            Locale.setDefault( Locale.US );
            test( "format(\"##,###.00\", 12345.67)", "12,345.67" );
        }
    }

    //------------------------------------------------------------------------//
    // STRING FUNCTION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("String Function Tests")
    class StringFunctionTests
    {
        @Test
        @DisplayName("Should evaluate size and len functions")
        void testSizeLen()
        {
            test( "\"A string\":size()", 8 );
            test( "\"A string\":len()", 8 );
        }

        @Test
        @DisplayName("Should evaluate char function")
        void testChar()
        {
            test( "char(65)", "A" );
            test( "97:char()", "a" );
        }

        @Test
        @DisplayName("Should evaluate left and right functions")
        void testLeftRight()
        {
            test( "\"A string\":left(3)", "A s" );
            test( "\"A string\":right(3)", "ing" );
        }

        @Test
        @DisplayName("Should evaluate reverse function")
        void testReverse()
        {
            test( "\"A string\":reverse()", "gnirts A" );
        }

        @Test
        @DisplayName("Should evaluate case conversion functions")
        void testCaseConversion()
        {
            test( "\"A string\":lower()", "a string" );
            test( "\"A string\":upper()", "A STRING" );
            test( "\"a string\":proper()", "A string" );
        }

        @Test
        @DisplayName("Should evaluate search function")
        void testSearch()
        {
            test( "\"str\":search(\"A string\")", 3 );
            test( "search(\"StR\",\"A string\")", 3 );
            test( "search(\"un\",\"En un lugar de la mancha vivía un...\",1)", 4 );
            test( "search(\"un\",\"En un lugar de la mancha vivía un...\",7)", 32 );
            test( "search(\" ?? \",\"En un lugar de la mancha vivía un...\",1)", 3 );
            test( "search(\" ?? \",\"En un lugar de la mancha vivía un...\",7)", 12 );
            test( "search(\" l*\",\"En un lugar de la mancha vivía un...\",1)", 6 );
            test( "search(\" l*\",\"En un lugar de la mancha vivía un...\",9)", 15 );
            test( "\"123\":search(\"A string\")", 0 );
        }

        @Test
        @DisplayName("Should evaluate mid function")
        void testMid()
        {
            test( "\"En un lugar de la mancha...\":mid(7,5)", "lugar" );
            test( "\"En un lugar de la mancha...\":mid(13)", "de la mancha..." );
            test( "\"A une passante\":mid(7,99)", "passante" );
            test( "\"A une passante\":mid(80,99)", "" );
            test( "\"A horse, my kingdom for a horse\":mid(\"MY\",\"For\"):trim()", "kingdom" );
            test( "\"A horse, my kingdom for a horse\":mid(\"for\",\"horse\")", " a " );
            test( "mid(\"12348\", 3, 3) == 348", true );
            test( "mid(12348, 3, 3) == 348", true );
        }

        @Test
        @DisplayName("Should evaluate substitute function")
        void testSubstitute()
        {
            test( "\"one , two, three\":substitute(\"o\", \"88\")", "88ne , tw88, three" );
            test( "\"one , two, three\":substitute(\"two\", \"dos\")", "one , dos, three" );
            test( "\"one , two, three\":substitute(\"/s*,/s*\", \",\")", "one,two,three" );
            test( "\"one , two, three\":substitute(\"/s*,/s*\", \",\", 2)", "one , two,three" );
        }
    }

    //------------------------------------------------------------------------//
    // MISCELLANEOUS FUNCTION TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Miscellaneous Function Tests")
    class MiscFunctionTests
    {
        @Test
        @DisplayName("Should evaluate put, get, and del functions")
        void testPutGetDel()
        {
            test( "put(\"varSaved\", var)", true, new Pair( "var", "MyVarOrDevice" ) );
            test( "get(\"varsaved\")", "MyVarOrDevice" );
            test( "del(\"varSaved\")", true );
            test( "del(\"notSaved\")", false );
        }

        @Test
        @DisplayName("Should evaluate network functions")
        void testNetworkFunctions()
        {
            test( "isReachable(\"localhost\", 100 )", true );
            test( "localIPs():len() > 0", true );
        }

        @Test
        @DisplayName("Should evaluate IIF function")
        void testIIF()
        {
            test( "IIF( date():year() < 2000, \"Last\", \"This\" )", "This" );
            test( "(date():year() < 2000):IIF( \"Last\", \"This\" )", "This" );
            test( "IIF( type(12) == \"N\", \"Number\", \"Not number\" )", "Number" );
        }

        @Test
        @DisplayName("Should evaluate type function")
        void testType()
        {
            test( "12.4:type()", "N" );
            test( "\"12.4\":type()", "N" );
            test( "true:type()", "B" );
            test( "\"true\":type()", "B" );
            test( "\"String\":type()", "S" );
            test( "date():type()", "date" );
            test( "time():type()", "time" );
            test( "list():type()", "list" );
            test( "pair():type()", "pair" );
            test( "type(date())", "date" );
            test( "type(time())", "time" );
            test( "type(list())", "list" );
            test( "type(pair())", "pair" );
        }

        @Test
        @DisplayName("Should support multiple function invocation styles")
        void testMultipleFunctionStyles()
        {
            test( "size( left( mid( \"0123456789\", 2,6 ), 4 ) )", 4 );
            test( "\"0123456789\":mid( 2,6 ):left( 4 ):size()", 4 );
            test( "size( \"0123456789\":mid( 2,6 ):left( 4 ) )", 4 );
        }
    }

    //------------------------------------------------------------------------//
    // VARIABLE TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Variable Tests")
    class VariableTests
    {
        @Test
        @DisplayName("Should evaluate expressions with single variable")
        void testSingleVariable()
        {
            test( "var + max( 3, 5*2 )", 25f, new Pair( "var", 15f ) );
        }

        @Test
        @DisplayName("Should evaluate expressions with multiple variables")
        void testMultipleVariables()
        {
            test( "var1 + max( var2, 5*2 )", 22f,
                  new Pair( "var1", 10f ),
                  new Pair( "var2", 12f ) );
        }
    }

    //------------------------------------------------------------------------//
    // TIME CLASS TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Time Class Tests")
    class TimeClassTests
    {
        @Test
        @DisplayName("Should create and compare time objects")
        void testTimeCreation()
        {
            test( "time() == time()", true );
            test( "time(\"10:40:10\") == \"10:40:10\":time()", true );
            test( "time(\"03:10\") == time(3,10,0)", true );
        }

        @Test
        @DisplayName("Should extract time components")
        void testTimeComponents()
        {
            test( "time():hour() >= 0 && time():hour() <= 23", true );
            test( "time():minute() >= 0 && time():minute()<= 59", true );
            test( "time():second() >= 0 && time():second()<= 59", true );
        }

        @Test
        @DisplayName("Should calculate time since midnight")
        void testSinceMidnight()
        {
            test( "time(\"10:40:10\"):sinceMidnight()", 38410 );
        }

        @Test
        @DisplayName("Should move time forward and backward")
        void testTimeMove()
        {
            test( "time(\"10:40:10\"):move(-20) == time(\"10:39:50\")", true );
            test( "time(10,40,10):move(999) == time(\"10:56:49\")", true );
        }

        @Test
        @DisplayName("Should compare time objects")
        void testTimeComparison()
        {
            test( "time(\"13:10\") > time(\"13:00\")", true );
            test( "time(\"13:10\") < time(\"13:20\")", true );
        }

        @Test
        @DisplayName("Should perform time arithmetic")
        void testTimeArithmetic()
        {
            test( "time(\"13:10\") + (60*2) == time(\"13:12\")", true );
            test( "time(\"13:10\") - (60*60) == time(\"12:10\")", true );
        }

        @Test
        @DisplayName("Should check day/night status")
        void testDayNight()
        {
            test( "time(\"13:10\"):isDay(36.5112,-4.8848,\"Europe/Paris\")", true );
            test( "time(\" 3:10\"):isNight(36.5112,-4.8848,\"Europe/Paris\")", true );
        }

        @Test
        @DisplayName("Should calculate sunrise and sunset")
        void testSunriseSunset()
        {
            test( "time():sunrise(36.5112,-4.8848,date(\"2021-04-11\"),\"Europe/Paris\") == time(\"07:53:48\")", true );
            test( "time():sunset( 36.5112,-4.8848,     \"2021-04-11\" ,\"Europe/Paris\") == time(\"20:50:02\")", true );
        }

        @Test
        @DisplayName("Should serialize and deserialize time")
        void testTimeSerialization()
        {
            test( "time():deserialize( time(\"13:55\"):serialize() ) == time(\"13:55\")", true );
        }
    }

    //------------------------------------------------------------------------//
    // DATE CLASS TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Date Class Tests")
    class DateClassTests
    {
        @Test
        @DisplayName("Should create and compare date objects")
        void testDateCreation()
        {
            test( "date() == date()", true );
            test( "date(\"2021-04-08\") == \"2021-04-08\":date()", true );
            test( "date(\"2021-04-08\") == date(2021,04,08)", true );
        }

        @Test
        @DisplayName("Should extract date components")
        void testDateComponents()
        {
            test( "date():day() == " + LocalDate.now().getDayOfMonth(), true );
            test( "time( date():day() ):hour()", 0 );
            test( "date(\"2021-04-08\"):day() == 8", true );
            test( "date(\"2021-04-08\"):month() == 4", true );
            test( "date(\"2021-04-08\"):year() == 2021", true );
            test( "date(\"2021-04-08\"):weekDay() == 4", true );
        }

        @Test
        @DisplayName("Should check leap year")
        void testLeapYear()
        {
            test( "date(\"2021-04-08\"):isLeap()", false );
        }

        @Test
        @DisplayName("Should calculate duration between dates")
        void testDateDuration()
        {
            test( "date(\"2021-04-08\"):duration( date(\"2021-04-01\") )", -7 );
            test( "date(2021,04,08):duration( date(\"2021-04-14\") )", 6 );
        }

        @Test
        @DisplayName("Should compare date objects")
        void testDateComparison()
        {
            test( "date() < date(\"2999-01-01\")", true );
            test( "date() > date(\"2000-01-01\")", true );
        }

        @Test
        @DisplayName("Should perform date arithmetic")
        void testDateArithmetic()
        {
            test( "date(\"2000-01-01\") + 2 == date(\"2000-01-03\")", true );
            test( "date(\"2000-01-01\") - 2 == date(\"1999-12-30\")", true );
        }

        @Test
        @DisplayName("Should move date forward and backward")
        void testDateMove()
        {
            test( "date():move( 2):day() == " + LocalDate.now().plusDays(2).getDayOfMonth(), true );
            test( "date():move(-5):day() == " + LocalDate.now().minusDays(5).getDayOfMonth(), true );
        }

        @Test
        @DisplayName("Should convert date to string")
        void testDateToString()
        {
            test( "date():tostring() == \"" + LocalDate.now().format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) + "\"", true );
        }

        @Test
        @DisplayName("Should handle complex date expressions")
        void testComplexDateExpressions()
        {
            test( "date(2021,04,12.8:floor(2):left(2)):day() == 12", true );
            test( "date(\"2021-04-\"+floor(12.8,12):left(2)):day() == 12", true );
        }

        @Test
        @DisplayName("Should serialize and deserialize date")
        void testDateSerialization()
        {
            test( "date():deserialize( date(\"2023-10-23\"):serialize() ) == date(\"2023-10-23\")", true );
        }
    }

    //------------------------------------------------------------------------//
    // LIST CLASS TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("List Class Tests")
    class ListClassTests
    {
        @Test
        @DisplayName("Should create list and get elements")
        void testListCreation()
        {
            test( "true:list():get(1)", true );
            test( "list(true,4.2):get(1)", true );
            test( "list(true,4.2):get(2) == 4.2", true );
            test( "list(\"hello\",4.2):get(2)", 4.2f );
        }

        @Test
        @DisplayName("Should add elements to list")
        void testListAdd()
        {
            test( "list():add(\"hello\"):add(7.5):get(2)", 7.5f );
            test( "list():add(3.5):get(1) == list():add(3.5):last()", true );
            test( "list(true,4.2):add(\"hello\"):size()", 3 );
            test( "list(true,4.2):add(\"hello\"):get(-1)", "hello" );
            test( "list(true,4.2):add(\"hello\",2):get(2)", "hello" );
        }

        @Test
        @DisplayName("Should add all elements from another list")
        void testListAddAll()
        {
            test( "list(1,2,3,4):addAll( list(5,6) ) == list(1,2,3,4,5,6)", true );
            test( "list(1,2,5,6):add(list(3,4)):last() == list(3,4)", true );
            test( "list(1,2,5,6):addAll(list(3,4),3) == list(1,2,3,4,5,6)", true );
        }

        @Test
        @DisplayName("Should find index of element")
        void testListIndex()
        {
            test( "list(true,4.2):index(4.2)", 2 );
        }

        @Test
        @DisplayName("Should get last element")
        void testListLast()
        {
            test( "list(true,4.2):add(3.5):last() == 3.5", true );
            test( "list(true,4.2):add(\"3.5\"):last() == 3.5", true );
            test( "list(true,4.2):get(-1) == 4.2", true );
        }

        @Test
        @DisplayName("Should split string into list")
        void testListSplit()
        {
            test( "list():split(\"hello,4.2\"):index(4.2)", 2 );
            test( "list():split(\"3|5|7|9\",\"|\"):get(2)", 5f );
            test( "list():split(\"A string, true, 12\"):get(1)", "A string" );
            test( "list():split(\"A string, true, 12\"):get(2)", true );
            test( "list():split(\"A string, true, 12\"):get(3)", 12f );
            test( "list():split(\" one, two   , three \", \"/s*,/s*\"):get(2)", "two" );
        }

        @Test
        @DisplayName("Should sort and reverse list")
        void testListSortReverse()
        {
            test( "list(4,2,1,3 ):sort() == list(1,2,3,4)", true );
            test( "list(1,2,3,4 ):reverse() == list(4,3,2,1)", true );
        }

        @Test
        @DisplayName("Should perform set operations")
        void testListSetOperations()
        {
            test( "list(1,2,3,4):union( list(5,6,7) ) == list(1,2,3,4,5,6,7)", true );
            test( "list(1,2,3,4):intersect( list(2,3,7,9) ) == list(2,3)", true );
            test( "list(1,2,2,3,4,4):uniquefy() == list(1,2,3,4)", true );
        }

        @Test
        @DisplayName("Should map and reduce list")
        void testListMapReduce()
        {
            test( "list(1,2,3,4):map(\"x*2\") == list(2,4,6,8)", true );
            test( "list(1,2,3,4):reduce(\"x+y\")", 10f );
            test( "list(\"A\",\"B\",\"C\"):reduce(\"x+y\")", "ABC" );
        }

        @Test
        @DisplayName("Should rotate list")
        void testListRotate()
        {
            test( "list(1,2,3,4):rotate() == list(4,1,2,3)", true );
            test( "list(1,2,3,4):rotate(3) == list(2,3,4,1)", true );
        }

        @Test
        @DisplayName("Should clone list")
        void testListClone()
        {
            test( "list(1, list(2,3), pair(\"one\",date()), true):clone() == list(1, list(2,3), pair(\"one\",date()), true)", true );
        }
    }

    //------------------------------------------------------------------------//
    // PAIR CLASS TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Pair Class Tests")
    class PairClassTests
    {
        @Test
        @DisplayName("Should get value by key")
        void testPairGet()
        {
            test( "pair():get(\"clima\")", "" );
            test( "pair(\"power\", true, \"red\", 220, \"blue\", 30, \"green\", 94):get(\"power\")", true );
        }

        @Test
        @DisplayName("Should put key-value pairs")
        void testPairPut()
        {
            test( "pair():put(\"name\",\"francisco\"):get(\"name\")", "francisco" );
            test( "pair(1, \"one\", 2, \"two\", 3, \"three\"):put(3, \"new\"):get(3)", "new" );
        }

        @Test
        @DisplayName("Should split string into pair")
        void testPairSplit()
        {
            test( "pair():split(\"name=francisco,age=63\"):get(\"age\")", 63f );
            test( "pair():split(\"name:francisco;age:63\", \";\", \":\"):get(\"age\")", 63f );
        }

        @Test
        @DisplayName("Should handle nested pairs")
        void testNestedPairs()
        {
            test( "pair(\"one\", \"uno\", \"two\", \"dos\", \"three\", \"tres\"):put( \"inner\", pair(\"four\", \"cuatro\", \"five\",\"cinco\") ):get(\"inner\"):size()", 2 );
        }

        @Test
        @DisplayName("Should put all from another pair")
        void testPairPutAll()
        {
            test( "pair(\"one\", \"uno\", \"two\", \"dos\"):putAll( pair(\"three\",\"tres\",\"four\",\"cuatro\") ) == pair(\"one\",\"uno\",\"two\",\"dos\",\"three\",\"tres\",\"four\",\"cuatro\")", true );
        }

        @Test
        @DisplayName("Should get keys and values")
        void testPairKeysValues()
        {
            test( "pair(\"one\",\"uno\", \"two\",\"dos\", \"three\",\"tres\"):keys():sort() == list(\"one\", \"three\", \"two\")", true );
            test( "pair(\"one\",\"uno\", \"two\",\"dos\", \"three\",\"tres\"):values():sort() == list(\"dos\", \"tres\", \"uno\")", true );
        }

        @Test
        @DisplayName("Should map pair")
        void testPairMap()
        {
            test( "pair(\"one\",1, \"two\",2, \"three\",3):map(\"x+y\") == pair(\"one\",\"one1.0\", \"two\",\"two2.0\", \"three\",\"three3.0\")", true );
        }

        @Test
        @DisplayName("Should reduce pair")
        void testPairReduce()
        {
            test( "pair(\"one\",1, \"two\",2, \"three\",3):reduce(\"x+y\")", 6f );
        }

        @Test
        @DisplayName("Should filter pair")
        void testPairFilter()
        {
            test( "pair(\"one\",1, \"two\",2, \"three\",3):filter(\"y>1\") == pair(\"two\",2, \"three\",3)", true );
        }

        @Test
        @DisplayName("Should clone pair")
        void testPairClone()
        {
            test( "pair(\"one\",1, \"two\",list(2,3), \"three\",true):clone() == pair(\"one\",1, \"two\",list(2,3), \"three\",true)", true );
        }

        @Test
        @DisplayName("Should check size and emptiness")
        void testSizeAndEmpty()
        {
            test( "pair():size()", 0 );
            test( "pair():len()", 0 );
            test( "pair():isEmpty()", true );
            test( "pair(\"k\", \"v\"):size()", 1 );
            test( "pair(\"k\", \"v\"):isEmpty()", false );
            test( "pair(\"k\", \"v\"):empty():isEmpty()", true );
        }

        @Test
        @DisplayName("Should get with default value")
        void testGetWithDefault()
        {
            test( "pair(\"k\", \"v\"):get(\"k\", \"default\")", "v" );
            test( "pair(\"k\", \"v\"):get(\"missing\", \"default\")", "default" );
        }

        @Test
        @DisplayName("Should check for keys and values")
        void testHasKeyAndValue()
        {
            test( "pair(\"k\", \"v\"):hasKey(\"k\")", true );
            test( "pair(\"k\", \"v\"):hasKey(\"missing\")", false );
            test( "pair(\"k\", \"v\"):hasValue(\"v\")", true );
            test( "pair(\"k\", \"v\"):hasValue(\"missing\")", false );
        }

        @Test
        @DisplayName("Should delete key")
        void testDel()
        {
             test( "pair(\"k1\", \"v1\", \"k2\", \"v2\"):del(\"k1\"):hasKey(\"k1\")", false );
             test( "pair(\"k1\", \"v1\", \"k2\", \"v2\"):del(\"k1\"):size()", 1 );
        }

        @Test
        @DisplayName("Should intersect pairs")
        void testIntersect()
        {
            test( "pair(\"a\", 1, \"b\", 2):intersect(pair(\"b\", 3, \"c\", 4)) == pair(\"b\", 2)", true );
        }

        @Test
        @DisplayName("Should union pairs")
        void testUnion()
        {
            test( "pair(\"a\", 1):union(pair(\"b\", 2)) == pair(\"a\", 1, \"b\", 2)", true );
        }

        @Test
        @DisplayName("Should serialize and deserialize pair")
        void testPairSerialization()
        {
            test( "pair():deserialize( pair(\"k\", \"v\"):serialize() ) == pair(\"k\", \"v\")", true );
        }

        @Test
        @DisplayName("Should convert to string")
        void testToString()
        {
             test( "pair(\"a\", 1):toString()", "\"a\"=1" );
        }
    }

    //------------------------------------------------------------------------//
    // TEMPORAL OPERATOR TESTS (AFTER/WITHIN)
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Temporal Operator Tests")
    class TemporalOperatorTests
    {
        @Test
        @DisplayName("Should evaluate AFTER when expression stays true")
        void testAfterForTrue()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            Timer t1 = new Timer();
            Timer t2 = new Timer();
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "var == 7 AFTER 3000",
                                             (value) -> {
                                                 t1.cancel();
                                                 t2.cancel();
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.TRUE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var", 7.0f );

            t1.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var", 7.0f ); }
            }, 1000 );

            t2.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var", 7.0f ); }
            }, 2000 );

            sleep( 3500 );
            assertTrue( passed.get(), "AFTER when TRUE should have executed" );
        }

        @Test
        @DisplayName("Should evaluate AFTER when expression becomes false")
        void testAfterForFalse()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            Timer timer = new Timer();
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "var == 7 AFTER 3000",
                                             (value) -> {
                                                 timer.cancel();
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.FALSE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var", 7.0f );

            timer.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var", 9.0f ); }
            }, 1000 );

            sleep( 3500 );
            assertTrue( passed.get(), "AFTER when FALSE should have executed" );
        }

        @Test
        @DisplayName("Should evaluate AFTER when variable never initialized")
        void testAfterForNotInit()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "var == 7 AFTER 3000",
                                             (value) -> {
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.FALSE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval();

            sleep( 3500 );
            assertTrue( passed.get(), "AFTER when not initialized should have executed" );
        }

        @Test
        @DisplayName("Should evaluate WITHIN when expression stays true")
        void testWithinForTrue()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "var == 7 WITHIN 3000",
                                             (value) -> {
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.TRUE, value );
                                                 assertTrue( UtilUnit.isBetween( 2850, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var", 7.0f );

            sleep( 3500 );
            assertTrue( passed.get(), "WITHIN when TRUE should have executed" );
        }

        @Test
        @DisplayName("Should evaluate WITHIN when expression becomes false")
        void testWithinForFalse()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            Timer timer = new Timer();
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "var == 7 WITHIN 3000",
                                             (value) -> {
                                                 timer.cancel();
                                                 long time = System.currentTimeMillis() - now;
                                                 assertTrue( UtilUnit.isBetween( 1000, time, 1150 ) );
                                                 assertEquals( Boolean.FALSE, value );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var", 7.0f );

            timer.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var", 2.0f ); }
            }, 1000 );

            sleep( 1500 );
            assertTrue( passed.get(), "WITHIN when FALSE should have executed" );
        }

        @Test
        @DisplayName("Should evaluate WITHIN when variable never initialized")
        void testWithinForNotInit()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "var == 7 WITHIN 3000",
                                             (value) -> {
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.FALSE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval();

            sleep( 3500 );
            assertTrue( passed.get(), "WITHIN when not initialized should have executed" );
        }

        @Test
        @DisplayName("Should evaluate combined AFTER and WITHIN with AND")
        void testBothWithAnd()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "(var1 == 5 AFTER 1000) && (var2 == 7 WITHIN 3000)",
                                             (value) -> {
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.TRUE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3250 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var1", 5.0f );
            xpr.eval( "var2", 7.0f );

            sleep( 3500 );
            assertTrue( passed.get(), "AFTER and WITHIN with AND should have executed" );
        }

        @Test
        @DisplayName("Should evaluate combined AFTER and WITHIN with OR")
        void testBothWithOr()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            Timer timer = new Timer();
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "(var1 == 5 AFTER 1000) || (var2 == 2 WITHIN 3000)",
                                             (value) -> {
                                                 timer.cancel();
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.TRUE, value );
                                                 assertTrue( UtilUnit.isBetween( 1000, time, 1150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var1", 5.0f );

            timer.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var2", 7.0f ); }
            }, 4000 );

            sleep( 1500 );
            assertTrue( passed.get(), "AFTER and WITHIN with OR should have executed" );
        }

        @Test
        @DisplayName("Should evaluate two AFTER operators")
        void testTwoAfters()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            Timer timer = new Timer();
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "(var1 == 5 AFTER 3000) || (var2 == 2 AFTER 2000)",
                                             (value) -> {
                                                 timer.cancel();
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.TRUE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var1", 5.0f );

            timer.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var2", 7.0f ); }
            }, 500 );

            sleep( 3500 );
            assertTrue( passed.get(), "Two AFTERs should have executed" );
        }

        @Test
        @DisplayName("Should evaluate two WITHIN operators")
        void testTwoWithins()
        {
            AtomicBoolean passed = new AtomicBoolean( false );
            Timer timer = new Timer();
            long now = System.currentTimeMillis();

            IXprEval xpr = new NAXE().build( "(var1 == 5 WITHIN 3000) || (var2 == 2 WITHIN 2000)",
                                             (value) -> {
                                                 timer.cancel();
                                                 long time = System.currentTimeMillis() - now;
                                                 assertEquals( Boolean.TRUE, value );
                                                 assertTrue( UtilUnit.isBetween( 3000, time, 3150 ) );
                                                 passed.set( true );
                                             },
                                             null );

            xpr.eval( "var1", 5.0f );

            timer.schedule( new TimerTask()
            {
                @Override
                public void run() { xpr.set( "var2", 7.0f ); }
            }, 4000 );

            sleep( 3500 );
            assertTrue( passed.get(), "Two WITHINs should have executed" );
        }
    }

    //------------------------------------------------------------------------//
    // HELPER METHODS
    //------------------------------------------------------------------------//

    /**
     * Tests an expression and compares the result with the expected value.
     *
     * @param xpr      Expression to evaluate
     * @param expected Expected result
     * @param vars     Optional variable bindings
     */
    private void test( String xpr, Object expected, Pair<String,Object>... vars )
    {
        Map<String,Object> mapVars = new HashMap<>();

        if( (vars != null) && (vars.length > 0) )
        {
            for( Pair<String,Object> pair : vars )
                mapVars.put( pair.getKey(), pair.getValue() );
        }

        try
        {
            XprPreProc preproc = new XprPreProc( new Lexer( xpr ).getLexemes(), null );

            if( ! preproc.getErrors().isEmpty() )
            {
                preproc.getErrors().forEach( System.out::println );
                fail( "Expression preprocessing failed: " + xpr );
            }

            List<XprToken> lstInfix = preproc.getAsInfix();
            EvalByAST ast = new EvalByAST( lstInfix, null );

            if( ! ast.getErrors().isEmpty() )
            {
                ast.getErrors().forEach( System.out::println );
                fail( "Expression evaluation failed: " + xpr );
            }

            for( Map.Entry<String,Object> entry : mapVars.entrySet() )
                ast.set( entry.getKey(), entry.getValue() );

            Object result = ast.eval();

            assertEquals( expected, result, "Expression: " + xpr );
        }
        catch( Exception exc )
        {
            fail( "Exception while testing '" + xpr + "': " + exc.getMessage(), exc );
        }
    }

    private void sleep( int millis )
    {
        try
        {
            Thread.sleep( millis );
        }
        catch( InterruptedException ex )
        {
            // Nothing to do
        }
    }

    //------------------------------------------------------------------------//
    // MAIN METHOD FOR STANDALONE TESTING
    //------------------------------------------------------------------------//

    /**
     * Main method for running tests without JUnit runner.
     * Provides basic test execution and reporting.
     */
    public static void main( String[] args )
    {
        System.out.println( "==================================================" );
        System.out.println( "      EXPRESSION EVALUATOR TEST SUITE             " );
        System.out.println( "==================================================" );
        System.out.println();

        EvalTest test = new EvalTest();
        int passed = 0;
        int failed = 0;
        int total = 0;

        // Arithmetic tests
        System.out.println( "--- Arithmetic Tests ---" );
        total++; if( runTest( "Numeric literals", () -> test.new ArithmeticTests().testNumericLiterals() ) ) passed++; else failed++;
        total++; if( runTest( "Basic arithmetic", () -> test.new ArithmeticTests().testBasicArithmetic() ) ) passed++; else failed++;
        total++; if( runTest( "Unary operators", () -> test.new ArithmeticTests().testUnaryOperators() ) ) passed++; else failed++;
        total++; if( runTest( "String to number", () -> test.new ArithmeticTests().testStringToNumberConversion() ) ) passed++; else failed++;
        total++; if( runTest( "Division by zero", () -> test.new ArithmeticTests().testDivisionByZero() ) ) passed++; else failed++;
        System.out.println();

        // Bitwise tests
        System.out.println( "--- Bitwise Tests ---" );
        total++; if( runTest( "Bitwise AND", () -> test.new BitwiseTests().testBitwiseAnd() ) ) passed++; else failed++;
        total++; if( runTest( "Bitwise OR", () -> test.new BitwiseTests().testBitwiseOr() ) ) passed++; else failed++;
        total++; if( runTest( "Bitwise XOR", () -> test.new BitwiseTests().testBitwiseXor() ) ) passed++; else failed++;
        total++; if( runTest( "Bitwise NOT", () -> test.new BitwiseTests().testBitwiseNot() ) ) passed++; else failed++;
        total++; if( runTest( "Shift left", () -> test.new BitwiseTests().testLeftShift() ) ) passed++; else failed++;
        total++; if( runTest( "Shift right", () -> test.new BitwiseTests().testRightShift() ) ) passed++; else failed++;
        System.out.println();

        // Boolean tests
        System.out.println( "--- Boolean Tests ---" );
        total++; if( runTest( "Boolean literals", () -> test.new BooleanTests().testBooleanLiterals() ) ) passed++; else failed++;
        total++; if( runTest( "Logical operators", () -> test.new BooleanTests().testLogicalOperators() ) ) passed++; else failed++;
        total++; if( runTest( "Logical with variables", () -> test.new BooleanTests().testLogicalWithVariables() ) ) passed++; else failed++;
        total++; if( runTest( "Comparison operators", () -> test.new BooleanTests().testComparisonOperators() ) ) passed++; else failed++;
        total++; if( runTest( "Complex boolean expressions", () -> test.new BooleanTests().testComplexBooleanExpressions() ) ) passed++; else failed++;
        total++; if( runTest( "String comparisons", () -> test.new BooleanTests().testStringComparisons() ) ) passed++; else failed++;
        total++; if( runTest( "String equality", () -> test.new BooleanTests().testStringEquality() ) ) passed++; else failed++;
        total++; if( runTest( "IsEmpty function", () -> test.new BooleanTests().testIsEmpty() ) ) passed++; else failed++;
        total++; if( runTest( "Mixed logical bitwise", () -> test.new BooleanTests().testMixedLogicalBitwise() ) ) passed++; else failed++;
        total++; if( runTest( "ALL quantifier", () -> test.new BooleanTests().testAllQuantifier() ) ) passed++; else failed++;
        total++; if( runTest( "ANY quantifier", () -> test.new BooleanTests().testAnyQuantifier() ) ) passed++; else failed++;
        total++; if( runTest( "Complex quantifiers", () -> test.new BooleanTests().testComplexQuantifiers() ) ) passed++; else failed++;
        total++; if( runTest( "Lazy evaluation", () -> test.new BooleanTests().testLazyEvaluation() ) ) passed++; else failed++;
        total++; if( runTest( "Truthy/Falsy numbers", () -> test.new BooleanTests().testTruthyFalsyNumbers() ) ) passed++; else failed++;
        total++; if( runTest( "Truthy/Falsy strings", () -> test.new BooleanTests().testTruthyFalsyStrings() ) ) passed++; else failed++;
        total++; if( runTest( "Truthy/Falsy lists", () -> test.new BooleanTests().testTruthyFalsyLists() ) ) passed++; else failed++;
        total++; if( runTest( "Truthy/Falsy pairs", () -> test.new BooleanTests().testTruthyFalsyPairs() ) ) passed++; else failed++;
        total++; if( runTest( "Truthy/Falsy short-circuit", () -> test.new BooleanTests().testTruthyFalsyShortCircuit() ) ) passed++; else failed++;
        total++; if( runTest( "Truthy/Falsy XOR", () -> test.new BooleanTests().testTruthyFalsyXOR() ) ) passed++; else failed++;
        System.out.println();

        // String tests
        System.out.println( "--- String Tests ---" );
        total++; if( runTest( "String literals", () -> test.new StringTests().testStringLiterals() ) ) passed++; else failed++;
        total++; if( runTest( "String concatenation", () -> test.new StringTests().testStringConcatenation() ) ) passed++; else failed++;
        total++; if( runTest( "String subtraction", () -> test.new StringTests().testStringSubtraction() ) ) passed++; else failed++;
        total++; if( runTest( "Mixed string/number", () -> test.new StringTests().testMixedStringNumber() ) ) passed++; else failed++;
        System.out.println();

        // Math function tests
        System.out.println( "--- Math Function Tests ---" );
        total++; if( runTest( "Floor function", () -> test.new MathFunctionTests().testFloor() ) ) passed++; else failed++;
        total++; if( runTest( "Ceiling function", () -> test.new MathFunctionTests().testCeiling() ) ) passed++; else failed++;
        total++; if( runTest( "IsBetween function", () -> test.new MathFunctionTests().testIsBetween() ) ) passed++; else failed++;
        total++; if( runTest( "SetBetween function", () -> test.new MathFunctionTests().testSetBetween() ) ) passed++; else failed++;
        total++; if( runTest( "Mod function", () -> test.new MathFunctionTests().testMod() ) ) passed++; else failed++;
        total++; if( runTest( "Int function", () -> test.new MathFunctionTests().testInt() ) ) passed++; else failed++;
        total++; if( runTest( "Round function", () -> test.new MathFunctionTests().testRound() ) ) passed++; else failed++;
        total++; if( runTest( "Abs function", () -> test.new MathFunctionTests().testAbs() ) ) passed++; else failed++;
        total++; if( runTest( "Rand function", () -> test.new MathFunctionTests().testRand() ) ) passed++; else failed++;
        total++; if( runTest( "Min/Max functions", () -> test.new MathFunctionTests().testMinMax() ) ) passed++; else failed++;
        System.out.println();

        // String function tests
        System.out.println( "--- String Function Tests ---" );
        total++; if( runTest( "Size/Len functions", () -> test.new StringFunctionTests().testSizeLen() ) ) passed++; else failed++;
        total++; if( runTest( "Char function", () -> test.new StringFunctionTests().testChar() ) ) passed++; else failed++;
        total++; if( runTest( "Left/Right functions", () -> test.new StringFunctionTests().testLeftRight() ) ) passed++; else failed++;
        total++; if( runTest( "Reverse function", () -> test.new StringFunctionTests().testReverse() ) ) passed++; else failed++;
        total++; if( runTest( "Case conversion", () -> test.new StringFunctionTests().testCaseConversion() ) ) passed++; else failed++;
        total++; if( runTest( "Search function", () -> test.new StringFunctionTests().testSearch() ) ) passed++; else failed++;
        total++; if( runTest( "Mid function", () -> test.new StringFunctionTests().testMid() ) ) passed++; else failed++;
        total++; if( runTest( "Substitute function", () -> test.new StringFunctionTests().testSubstitute() ) ) passed++; else failed++;
        System.out.println();

        // Miscellaneous function tests
        System.out.println( "--- Miscellaneous Function Tests ---" );
        total++; if( runTest( "Put/Get/Del functions", () -> test.new MiscFunctionTests().testPutGetDel() ) ) passed++; else failed++;
        total++; if( runTest( "Network functions", () -> test.new MiscFunctionTests().testNetworkFunctions() ) ) passed++; else failed++;
        total++; if( runTest( "IIF function", () -> test.new MiscFunctionTests().testIIF() ) ) passed++; else failed++;
        total++; if( runTest( "Type function", () -> test.new MiscFunctionTests().testType() ) ) passed++; else failed++;
        total++; if( runTest( "Multiple function styles", () -> test.new MiscFunctionTests().testMultipleFunctionStyles() ) ) passed++; else failed++;
        System.out.println();

        // Variable tests
        System.out.println( "--- Variable Tests ---" );
        total++; if( runTest( "Single variable", () -> test.new VariableTests().testSingleVariable() ) ) passed++; else failed++;
        total++; if( runTest( "Multiple variables", () -> test.new VariableTests().testMultipleVariables() ) ) passed++; else failed++;
        System.out.println();

        // Time class tests
        System.out.println( "--- Time Class Tests ---" );
        total++; if( runTest( "Time creation", () -> test.new TimeClassTests().testTimeCreation() ) ) passed++; else failed++;
        total++; if( runTest( "Time components", () -> test.new TimeClassTests().testTimeComponents() ) ) passed++; else failed++;
        total++; if( runTest( "Since midnight", () -> test.new TimeClassTests().testSinceMidnight() ) ) passed++; else failed++;
        total++; if( runTest( "Time move", () -> test.new TimeClassTests().testTimeMove() ) ) passed++; else failed++;
        total++; if( runTest( "Time comparison", () -> test.new TimeClassTests().testTimeComparison() ) ) passed++; else failed++;
        total++; if( runTest( "Time arithmetic", () -> test.new TimeClassTests().testTimeArithmetic() ) ) passed++; else failed++;
        total++; if( runTest( "Day/Night status", () -> test.new TimeClassTests().testDayNight() ) ) passed++; else failed++;
        total++; if( runTest( "Sunrise/Sunset", () -> test.new TimeClassTests().testSunriseSunset() ) ) passed++; else failed++;
        total++; if( runTest( "Time serialization", () -> test.new TimeClassTests().testTimeSerialization() ) ) passed++; else failed++;
        System.out.println();

        // Date class tests
        System.out.println( "--- Date Class Tests ---" );
        total++; if( runTest( "Date creation", () -> test.new DateClassTests().testDateCreation() ) ) passed++; else failed++;
        total++; if( runTest( "Date components", () -> test.new DateClassTests().testDateComponents() ) ) passed++; else failed++;
        total++; if( runTest( "Leap year", () -> test.new DateClassTests().testLeapYear() ) ) passed++; else failed++;
        total++; if( runTest( "Date duration", () -> test.new DateClassTests().testDateDuration() ) ) passed++; else failed++;
        total++; if( runTest( "Date comparison", () -> test.new DateClassTests().testDateComparison() ) ) passed++; else failed++;
        total++; if( runTest( "Date arithmetic", () -> test.new DateClassTests().testDateArithmetic() ) ) passed++; else failed++;
        total++; if( runTest( "Date move", () -> test.new DateClassTests().testDateMove() ) ) passed++; else failed++;
        total++; if( runTest( "Date to string", () -> test.new DateClassTests().testDateToString() ) ) passed++; else failed++;
        total++; if( runTest( "Complex date expressions", () -> test.new DateClassTests().testComplexDateExpressions() ) ) passed++; else failed++;
        total++; if( runTest( "Date serialization", () -> test.new DateClassTests().testDateSerialization() ) ) passed++; else failed++;
        System.out.println();

        // List class tests
        System.out.println( "--- List Class Tests ---" );
        total++; if( runTest( "List creation", () -> test.new ListClassTests().testListCreation() ) ) passed++; else failed++;
        total++; if( runTest( "List add", () -> test.new ListClassTests().testListAdd() ) ) passed++; else failed++;
        total++; if( runTest( "List add all", () -> test.new ListClassTests().testListAddAll() ) ) passed++; else failed++;
        total++; if( runTest( "List index", () -> test.new ListClassTests().testListIndex() ) ) passed++; else failed++;
        total++; if( runTest( "List last", () -> test.new ListClassTests().testListLast() ) ) passed++; else failed++;
        total++; if( runTest( "List split", () -> test.new ListClassTests().testListSplit() ) ) passed++; else failed++;
        total++; if( runTest( "List sort/reverse", () -> test.new ListClassTests().testListSortReverse() ) ) passed++; else failed++;
        total++; if( runTest( "List set operations", () -> test.new ListClassTests().testListSetOperations() ) ) passed++; else failed++;
        total++; if( runTest( "List map/reduce", () -> test.new ListClassTests().testListMapReduce() ) ) passed++; else failed++;
        total++; if( runTest( "List rotate", () -> test.new ListClassTests().testListRotate() ) ) passed++; else failed++;
        total++; if( runTest( "List clone", () -> test.new ListClassTests().testListClone() ) ) passed++; else failed++;
        System.out.println();

        // Pair class tests
        System.out.println( "--- Pair Class Tests ---" );
        total++; if( runTest( "Pair get", () -> test.new PairClassTests().testPairGet() ) ) passed++; else failed++;
        total++; if( runTest( "Pair put", () -> test.new PairClassTests().testPairPut() ) ) passed++; else failed++;
        total++; if( runTest( "Pair split", () -> test.new PairClassTests().testPairSplit() ) ) passed++; else failed++;
        total++; if( runTest( "Nested pairs", () -> test.new PairClassTests().testNestedPairs() ) ) passed++; else failed++;
        total++; if( runTest( "Pair put all", () -> test.new PairClassTests().testPairPutAll() ) ) passed++; else failed++;
        total++; if( runTest( "Pair keys/values", () -> test.new PairClassTests().testPairKeysValues() ) ) passed++; else failed++;
        total++; if( runTest( "Pair map", () -> test.new PairClassTests().testPairMap() ) ) passed++; else failed++;
        total++; if( runTest( "Pair reduce", () -> test.new PairClassTests().testPairReduce() ) ) passed++; else failed++;
        total++; if( runTest( "Pair filter", () -> test.new PairClassTests().testPairFilter() ) ) passed++; else failed++;
        total++; if( runTest( "Pair clone", () -> test.new PairClassTests().testPairClone() ) ) passed++; else failed++;
        total++; if( runTest( "Pair size/empty", () -> test.new PairClassTests().testSizeAndEmpty() ) ) passed++; else failed++;
        total++; if( runTest( "Pair get default", () -> test.new PairClassTests().testGetWithDefault() ) ) passed++; else failed++;
        total++; if( runTest( "Pair has key/value", () -> test.new PairClassTests().testHasKeyAndValue() ) ) passed++; else failed++;
        total++; if( runTest( "Pair del", () -> test.new PairClassTests().testDel() ) ) passed++; else failed++;
        total++; if( runTest( "Pair intersect", () -> test.new PairClassTests().testIntersect() ) ) passed++; else failed++;
        total++; if( runTest( "Pair union", () -> test.new PairClassTests().testUnion() ) ) passed++; else failed++;
        total++; if( runTest( "Pair serialization", () -> test.new PairClassTests().testPairSerialization() ) ) passed++; else failed++;
        total++; if( runTest( "Pair toString", () -> test.new PairClassTests().testToString() ) ) passed++; else failed++;
        System.out.println();

        // Temporal operator tests
        System.out.println( "--- Temporal Operator Tests ---" );
        total++; if( runTest( "AFTER for true", () -> test.new TemporalOperatorTests().testAfterForTrue() ) ) passed++; else failed++;
        total++; if( runTest( "AFTER for false", () -> test.new TemporalOperatorTests().testAfterForFalse() ) ) passed++; else failed++;
        total++; if( runTest( "AFTER for not init", () -> test.new TemporalOperatorTests().testAfterForNotInit() ) ) passed++; else failed++;
        total++; if( runTest( "WITHIN for true", () -> test.new TemporalOperatorTests().testWithinForTrue() ) ) passed++; else failed++;
        total++; if( runTest( "WITHIN for false", () -> test.new TemporalOperatorTests().testWithinForFalse() ) ) passed++; else failed++;
        total++; if( runTest( "WITHIN for not init", () -> test.new TemporalOperatorTests().testWithinForNotInit() ) ) passed++; else failed++;
        total++; if( runTest( "Both with AND", () -> test.new TemporalOperatorTests().testBothWithAnd() ) ) passed++; else failed++;
        total++; if( runTest( "Both with OR", () -> test.new TemporalOperatorTests().testBothWithOr() ) ) passed++; else failed++;
        total++; if( runTest( "Two AFTERs", () -> test.new TemporalOperatorTests().testTwoAfters() ) ) passed++; else failed++;
        total++; if( runTest( "Two WITHINs", () -> test.new TemporalOperatorTests().testTwoWithins() ) ) passed++; else failed++;
        System.out.println();

        // Summary
        System.out.println( "==================================================" );
        System.out.println( "                   TEST SUMMARY                    " );
        System.out.println( "==================================================" );
        System.out.println( "Total Tests:  " + total );
        System.out.println( "Passed:       " + passed + " (" + (total > 0 ? passed * 100 / total : 0) + "%)" );
        System.out.println( "Failed:       " + failed + " (" + (total > 0 ? failed * 100 / total : 0) + "%)" );
        System.out.println( "==================================================" );

        if( failed == 0 )
        {
            System.out.println( "\n✓ ALL TESTS PASSED!" );
            System.exit( 0 );
        }
        else
        {
            System.out.println( "\n✗ SOME TESTS FAILED!" );
            System.exit( 1 );
        }
    }

    /**
     * Runs a single test and returns true if it passes.
     */
    private static boolean runTest( String name, Runnable test )
    {
        try
        {
            test.run();
            System.out.println( "  ✓ " + name );
            return true;
        }
        catch( AssertionError e )
        {
            System.out.println( "  ✗ " + name + ": " + e.getMessage() );
            return false;
        }
        catch( Exception e )
        {
            System.out.println( "  ✗ " + name + ": " + e.getClass().getSimpleName() + " - " + e.getMessage() );
            return false;
        }
    }
}