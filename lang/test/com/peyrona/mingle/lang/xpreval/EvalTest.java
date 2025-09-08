
package com.peyrona.mingle.lang.xpreval;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

//------------------------------------------------------------------------//

public class EvalTest
{
    public static void main( String[] args )
    {
        doAfterForTrue();
        doAfterForFalse();

        doWithinForTrue();
        doWithinForFalse();

        doBothForTrue();
        doBothForFalse();

        try
        {
            Thread.sleep( 3500 );
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
        }
        finally   // FIXME: no deberia ser necesario, con doAfterForTrue() no lo es pero con doAfterForFalse() sí lo es
        {
            System.exit( 0 );
        }
    }

    @Test
    public void testArithmetic()
    {
        System.out.println( "Arithmetic examples -----------------------------\n" );

        test( "12", 12f );
        test( "true", true );
        test( "\"string\"", "string" );
        test( "12 + 3",   15f );
        test( "12 - 3",    9f );
        test( "12 * 3",   36f );
        test( "12 / 3",    4f );
        test( "12 % 200", 24f );
        test( ".2 + .3", 0.5f );
        test( "12 ^ 3", 1728f );
        test( "-12 + (2*+4) + 22", 18f );
        test( "(-12 + (+2*4) + 27) / 5", 4.6f );
        test( "\"10\" * 2", 20f );
        test( "-8 + \"+10\" * -2", -28f );
        test( "+10 / 0", Float.NaN );
        test( "1_000_000 == 1000000", true );

        System.out.println();
    }

    @Test
    public void testBitwise()
    {
        System.out.println( "Bitwise examples -----------------------------\n" );

        test( "5  &  3",  1f );
        test( "5  |  3",  7f );
        test( "5  >< 3",  6f );
        test( "~5"     , -6f );
        test( "5  << 2", 20f );
        test( "12 >> 2",  3f );

        System.out.println();
    }

    @Test
    public void testBoolean()
    {
        System.out.println( "Boolean examples -----------------------------\n" );

        test( "true", true );
        test( "false", false );
        test( "true || false", true );
        test( "true && false", false );
        test( "true && ! false", true );
        test( "(var1 && ! var2) || var3", true, new Pair( "var1", true ), new Pair( "var2", false ), new Pair( "var3", true ) );
        test( "var1 && ! (var2 && var3)", true, new Pair( "var1", true ), new Pair( "var2", false ), new Pair( "var3", false ) );

        test( "8 * 2 == 16", true );
        test( "8 * 2 != 16", false );
        test( "! (8 * 2 != 16)", true );
        test( "2 < 22", true );
        test( "(2 < 22) && ! (8 < 2)", true  );
        test( "(2 < 22) || (4 > 5)"  , true  );
        test( "(2 < 22) && (4 > 5)"  , false );
        test( "(2 < 22) |& (4 > 5)"  , true  );             // One of the expressions is true and one of the expressions is false

        test( "\"caco\" <= \"malo\"", true );
        test( "\"caco\" >= \"malo\"", false );
        test( "\"caco\" < \"malo\" && (2 < 22)", true );
        test( "\"caco\" > \"malo\" && (2 < 22)", false );

        test( "\"caco\" == \"caco\"", true );
        test( "\"caco\" == \"CACO\"", true );               // This is true because Une es case-insensitive
        test( "\"caco\":equals( \"CACO\" )", false );       // To make a case-sensitive comparison, 'equals' function has to beused
        test( "equals( \"caco\", \"caco\", \"caco\" )", true );

        test( "! isEmpty( \"\" )", false );
        test( "(((5 | 3) == 7) && ((5 & 3) == 1)) || (2 > 8) && (3 < 5)", true );    // Checks that there are no conflicts mixing logical and bit ops

        // Left and Right laizy boolean evaluation
        // Can not be done by invoking ::test(...) because EvalByRPN does not have laizy eval

        assertEquals( new NAXE().build( "true  || x > 5" ).eval(), Boolean.TRUE  );    // Left lazy OR
        assertEquals( new NAXE().build( "1 > 2 && x > 5" ).eval(), Boolean.FALSE );    // Left lazy AND

        assertEquals( new NAXE().build( "2 > 1 || x > 5" ).eval(), Boolean.TRUE  );    // Right lazy OR
        assertEquals( new NAXE().build( "x > 5 && 1 > 2" ).eval(), Boolean.FALSE );    // Right lazy AND

        assertEquals( new NAXE().build( "1 > 2 || x > 5" ).eval(), null );
        assertEquals( new NAXE().build( "2 > 1 && x > 5" ).eval(), null );

        System.out.println();
    }

    @Test
    public void testString()
    {
        System.out.println( "String operators examples -----------------------------\n" );

        test( "\"caco malo\"", "caco malo" );
        test( "\"caco \" + \"malo\"", "caco malo" );
        test( "\"caco \" + \"malo\" - \"o\"", "cac mal" );
        test( "\"12\" + \"34\"" , "1234" );
        test( "\"12\" + 34"     , 46f );
        test( "\"1234\" - \"3\"", "124" );
        test( "\"1234\" - 3"    , 1231f );
        test( "\"12\" * \"3\""  , 36f );
        test( "\"12\" / \"3\""  , 4f );

        System.out.println();
    }

    @Test
    public void testFunction()
    {
        System.out.println( "Functions examples -----------------------------\n" );

        // Maths ---------------------------------------------

        test( "floor(2.8,2)", 2.0f );
        test( "2.8:floor(2)", 2.0f );
        test( "floor(3.7,2)", 2.0f );
        test( "floor(-2.5,-2)", -2.0f );
        test( "floor(1.58,0.1)", 1.5f );
        test( "FlOoR(0.234,0.01)", 0.22999999f );     // TODO: esto habría que ahcerlo mejor y que salga 0.23 en lugar de lo que sale

        test( "ceiling(2.2,1)", 3.0f );
        test( "2.2:ceiling(1)", 3.0f );
        test( "ceiling(2.5, 1)", 3.0f );
        test( "ceiling(-2.5, -2)", -2.0f );
        test( "ceiling(1.5, 0.1)", 1.5f );
        test( "ceiling(0.234, 0.01)", 0.24f );
        test( "isBetween(1,0,2)", true );
        test( "isBetween(0.0,0.0,2.0)", true );
        test( "isBetween(2.0,0.0,2.0)", true );
        test( "setBetween(3,0,2)", 2.0f );

        test( "mod(10,3)", 1.0f);

        test( "int(\"0b11000\") + 1", 25.0f );
        test( "int(\"0x18ABC\") > 1", true );

        test( "round(22.3)", 22.0f );
        test( "22.3:round()", 22.0f );
        test( "round(2.15,1)", 2.2f );
        test( "round(2.149, 1)", 2.1f );
        test( "round(-1.475, 2)", -1.47f );
        test( "round(626.3, 3)", 626.3f );
        test( "round(626.3, -3)", 999.99994f );    // TODO: esto habría que ahcerlo mejor y que salga 1000 en lugar de lo que sale
        test( "round(1.98,-1)", 0.0f );
        test( "round(-50.55,-2)", -100.0f );

        test( "abs(-3)", 3.0f );
        test( "rand(0,100) >= 0", true );
        test( "min( 4, floor( ceiling( 7.6, 7 ), 7 ) )", 4f );
        test( "7.6:ceiling(7):floor(7):min( 4 )", 4f );
        Locale.setDefault( Locale.US );                          // Because next test CAN BE DIFFERENT DEPENDING ON LOCALE
        test( "format(\"##,###.00\", 12345.67)", "12,345.67" );

        test( "10 + max( 3, 5*2 )", 20f );
        test( "10 + 3:MAX( 5*2 )", 20f );
        test( "10 + max( min( 13, 15 ), 5*2 )", 23f );
        test( "max( 5, 10, 15, 20 )", 20f );
        test( "min( 4, max( 2, min( 7, 9 ) ) )", 4f );
        test( "4:min( 5 )", 4f );
        test( "min( 5, 10, 15, 20 )", 5f );


        // Strings -------------------------------------------

        test( "\"A string\":size()"   , 8 );
        test( "\"A string\":len()"    , 8 );
        test( "char(65)", "A" );
        test( "97:char()", "a" );
        test( "\"A string\":left(3)"  , "A s" );
        test( "\"A string\":right(3)" , "ing" );
        test( "\"A string\":reverse()", "gnirts A" );
        test( "\"A string\":lower()"  , "a string" );
        test( "\"A string\":upper()"  , "A STRING" );
        test( "\"a string\":proper()" , "A string" );
        test( "\"str\":search(\"A string\")", 3 );
        test( "search(\"StR\",\"A string\")", 3 );                                          // search func syntax is the one used by Excel
        test( "search(\"un\",\"En un lugar de la mancha vivía un...\",1)", 4 );
        test( "search(\"un\",\"En un lugar de la mancha vivía un...\",7)", 32 );
        test( "search(\" ?? \",\"En un lugar de la mancha vivía un...\",1)", 3 );
        test( "search(\" ?? \",\"En un lugar de la mancha vivía un...\",7)", 12 );
        test( "search(\" l*\",\"En un lugar de la mancha vivía un...\",1)", 6 );
        test( "search(\" l*\",\"En un lugar de la mancha vivía un...\",9)", 15 );
        test( "\"123\":search(\"A string\")", 0 );
        test( "\"En un lugar de la mancha...\":mid(7,5)", "lugar" );
        test( "\"En un lugar de la mancha...\":mid(13)", "de la mancha..." );
        test( "\"A une passante\":mid(7,99)", "passante" );
        test( "\"A une passante\":mid(80,99)", "" );
        test( "\"A horse, my kingdom for a horse\":mid(\"MY\",\"For\"):trim()", "kingdom" );
        test( "\"A horse, my kingdom for a horse\":mid(\"for\",\"horse\")", " a " );       // Searches for the second horse, the one that is after 'for'
        test( "mid(\"12348\", 3, 3) == 348", true );
        test( "mid(12348, 3, 3) == 348", true );
        test( "\"one , two, three\":substitute(\"o\", \"88\")", "88ne , tw88, three");
        test( "\"one , two, three\":substitute(\"two\", \"dos\")", "one , dos, three");
        test( "\"one , two, three\":substitute(\"/s*,/s*\", \",\")", "one,two,three");
        test( "\"one , two, three\":substitute(\"/s*,/s*\", \",\", 2)", "one , two,three");

        // Miscellaneous -------------------------------------

        test( "put(\"varSaved\", var)", true, new Pair( "var", "MyVarOrDevice" ) );
        test( "get(\"varsaved\")", "MyVarOrDevice" );
     // test( "del(\"varSaved\")", true );       // Can not invoke this because when 1st time it is evaluated by RPN and it is true, but then it is evaluated again by AST, then it is false
        test( "del(\"notSaved\")", false );

        test( "isReachable(\"localhost\", 100 )", true );
        test( "localIPs():len() > 0", true );

        test( "IIF( date():year() < 2000, \"Last\", \"This\" )", "This" );
        test( "(date():year() < 2000):IIF( \"Last\", \"This\" )", "This" );
        test( "IIF( type(12) == \"N\", \"Number\", \"Not number\" )", "Number" );

        test( "12.4:type()", "N" );
        test( "\"12.4\":type()", "N" );
        test( "true:type()", "B" );
        test( "\"true\":type()", "B" );
        test( "\"String\":type()", "S" );
        test( "date():type()", "date" );
        test( "time():type()", "time" );
        test( "list():type()", "list" );
        test( "pair():type()", "pair" );
        test( "type(date())" , "date" );
        test( "type(time())" , "time" );
        test( "type(list())" , "list" );
        test( "type(pair())" , "pair" );

        // Three ways to invoke funcs
        test( "size( left( mid( \"0123456789\", 2,6 ), 4 ) )", 4 );
        test( "\"0123456789\":mid( 2,6 ):left( 4 ):size()"   , 4 );
        test( "size( \"0123456789\":mid( 2,6 ):left( 4 ) )"  , 4 );

        System.out.println();
    }

    @Test
    public void testVariables()
    {
        System.out.println( "Variables examples -----------------------------\n" );

        test( "var + max( 3, 5*2 )", 25f, new Pair( "var", 15f ) );
        test( "var1 + max( var2, 5*2 )", 22f, new Pair( "var1", 10f ), new Pair( "var2", 12f ) );

        System.out.println();
    }

    @Test
    public void testClasses()
    {
        System.out.println( "'time' class examples -----------------------------\n" );

        test( "time() == time()"                                  , true );
        test( "time():hour()   >= 0 && time():hour()  <= 23"      , true );
        test( "time():minute() >= 0 && time():minute()<= 59"      , true );
        test( "time():second() >= 0 && time():second()<= 59"      , true );
        test( "time(\"10:40:10\") == \"10:40:10\":time()"         , true );
        test( "time(\"10:40:10\"):sinceMidnight()"                , 38410);
        test( "time(\"10:40:10\"):move(-20) == time(\"10:39:50\")", true );
        test( "time(  10,40,10  ):move(999) == time(\"10:56:49\")", true );       // Another constructor
        test( "time(\"03:10\") == time(3,10,0)"                   , true );
        test( "time(\"13:10\") >  time(\"13:00\")"                , true );
        test( "time(\"13:10\") <  time(\"13:20\")"                , true );
        test( "time(\"13:10\") +  (60*2)  == time(\"13:12\")"     , true );       // 2 mins in secs
        test( "time(\"13:10\") -  (60*60) == time(\"12:10\")"     , true );       // 1hr in secs
        test( "time(\"13:10\"):isDay(  36.5112,-4.8848,\"Europe/Paris\")", true );
        test( "time(\" 3:10\"):isNight(36.5112,-4.8848,\"Europe/Paris\")", true );
        test( "time():sunrise(36.5112,-4.8848,date(\"2021-04-11\"),\"Europe/Paris\") == time(\"07:53:48\")", true );
        test( "time():sunset( 36.5112,-4.8848,     \"2021-04-11\" ,\"Europe/Paris\") == time(\"20:50:02\")", true );
        test( "time():deserialize( time(\"13:55\"):serialize() ) == time(\"13:55\")", true );

        System.out.println();
        System.out.println( "'date' class examples -----------------------------\n" );

        test( "date() == date()"                                     , true );
        test( "date():day() == "+ LocalDate.now().getDayOfMonth()    , true );
        test( "time( date():day() ):hour() "                         , 0 );
        test( "date(\"2021-04-08\") == \"2021-04-08\":date()"        , true );
        test( "date(\"2021-04-08\"):day() == 8"                      , true );
        test( "date(\"2021-04-08\"):month() == 4"                    , true );
        test( "date(\"2021-04-08\"):year() == 2021"                  , true );
        test( "date(\"2021-04-08\"):weekDay() == 4"                  , true );    // from 1 (Monday) to 7 (Sunday).
        test( "date(\"2021-04-08\"):isLeap()"                        , false );   // from 1 (Monday) to 7 (Sunday).
        test( "date(\"2021-04-08\"):duration( date(\"2021-04-01\") )", -7 );
        test( "date(  2021,04,08  ):duration( date(\"2021-04-14\") )",  6 );      // Another constructor
        test( "date() == date()"                                     , true );
        test( "date() <  date(\"2999-01-01\")"                       , true );
        test( "date() >  date(\"2000-01-01\")"                       , true );
        test( "date(\"2000-01-01\") + 2 == date(\"2000-01-03\")"     , true );
        test( "date(\"2000-01-01\") - 2 == date(\"1999-12-30\")"     , true );
        test( "date():move( 2):day() == "+ LocalDate.now().plusDays( 2).getDayOfMonth(), true );
        test( "date():move(-5):day() == "+ LocalDate.now().minusDays(5).getDayOfMonth(), true );
        test( "date():tostring() == \""+ LocalDate.now().format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) +'\"', true );
        test( "date(2021,04,12.8:floor(2):left(2)):day() == 12", true );
        test( "date(\"2021-04-\"+floor(12.8,12):left(2)):day() == 12", true );
        test( "date():deserialize( date(\"2023-10-23\"):serialize() ) == date(\"2023-10-23\")", true );

        System.out.println();
        System.out.println( "'list' class examples -----------------------------\n" );

        test( "true:list():get(1)", true );
        test( "list(true,4.2):get(1)", true );
        test( "list(true,4.2):get(2) == 4.2", true );
        test( "list(\"hello\",4.2):get(2)", 4.2f );
        test( "list():add(\"hello\"):add(7.5):get(2)", 7.5f );
        test( "list():add(3.5):get(1) == list():add(3.5):last()", true );
        test( "list(true,4.2):index(4.2)", 2 );
        test( "list(true,4.2):add(\"hello\"):size()", 3 );
        test( "list(true,4.2):add(\"hello\"):get(-1)", "hello" );
        test( "list(true,4.2):add(\"hello\",2):get(2)", "hello" );
        test( "list(1,2,3,4):addAll( list(5,6) ) == list(1,2,3,4,5,6)", true );
        test( "list(1,2,5,6):add(list(3,4)):last() == list(3,4)", true );
        test( "list(1,2,5,6):addAll(list(3,4),3) == list(1,2,3,4,5,6)", true );
        test( "list(4,2,1,3 ):sort() == list(1,2,3,4)", true );
        test( "list(1,2,3,4 ):reverse() == list(4,3,2,1)", true );
        test( "list(true,4.2):add(3.5):last() == 3.5", true );
        test( "list(true,4.2):add(\"3.5\"):last() == 3.5", true );
        test( "list(true,4.2):get(-1) == 4.2", true );
        test( "list():split(\"hello,4.2\"):index(4.2)", 2 );
        test( "list():split(\"3|5|7|9\",\"|\"):get(2)", 5f );
        test( "list():split(\"A string, true, 12\"):get(1)", "A string" );
        test( "list():split(\"A string, true, 12\"):get(2)", true );
        test( "list():split(\"A string, true, 12\"):get(3)", 12f );
        test( "list():split(\" one, two   , three \", \"/s*,/s*\"):get(2)", "two");
        test( "list(1,2,3,4):union( list(5,6,7) ) == list(1,2,3,4,5,6,7)", true );
        test( "list(1,2,3,4):intersect( list(2,3,7,9) ) == list(2,3)", true );
        test( "list(1,2,2,3,4,4):uniquefy() == list(1,2,3,4)", true );
        test( "list(1,2,3,4):map(\"x*2\") == list(2,4,6,8)", true );
        test( "list(1,2,3,4):reduce(\"x+y\")", 10f );
        test( "list(\"A\",\"B\",\"C\"):reduce(\"x+y\")", "ABC" );
        test( "list(1,2,3,4):rotate() == list(4,1,2,3)", true );
        test( "list(1,2,3,4):rotate(3) == list(2,3,4,1)", true );
        test( "list(1, list(2,3), pair(\"one\",date()), true):clone() == list(1, list(2,3), pair(\"one\",date()), true)", true);

        System.out.println();
        System.out.println( "'pair' class examples -----------------------------\n" );

        test( "pair():get(\"clima\")", "" );
        test( "pair(\"power\", true, \"red\", 220, \"blue\", 30, \"green\", 94):get(\"power\")", true );
        test( "pair():put(\"name\",\"francisco\"):get(\"name\")", "francisco" );
        test( "pair():split(\"name=francisco,age=63\"):get(\"age\")", 63f );
        test( "pair():split(\"name:francisco;age:63\", \";\", \":\"):get(\"age\")", 63f );
        test( "pair(\"one\", \"uno\", \"two\", \"dos\", \"three\", \"tres\"):put( \"inner\", pair(\"four\", \"cuatro\", \"five\",\"cinco\") ):get(\"inner\"):size()", 2 );
        test( "pair(\"one\", \"uno\", \"two\", \"dos\"):putAll( pair(\"three\",\"tres\",\"four\",\"cuatro\") ) == pair(\"one\",\"uno\",\"two\",\"dos\",\"three\",\"tres\",\"four\",\"cuatro\")", true );
        test( "pair(1, \"one\", 2, \"two\", 3, \"three\"):put(3, \"new\"):get(3)", "new" );
        test( "pair(\"one\",\"uno\", \"two\",\"dos\", \"three\",\"tres\"):keys():sort() == list(\"one\", \"three\", \"two\")", true );
        test( "pair(\"one\",\"uno\", \"two\",\"dos\", \"three\",\"tres\"):values():sort() == list(\"dos\", \"tres\", \"uno\")", true );
        test( "pair(\"one\",1, \"two\",2, \"three\",3):map(\"x+y\") == pair(\"one\",\"one1.0\", \"two\",\"two2.0\", \"three\",\"three3.0\")", true );
        test( "pair(\"one\",1, \"two\",2, \"three\",3):reduce(\"x+y\")", 6f );
        test( "pair(\"one\",1, \"two\",2, \"three\",3):filter(\"y>1\") == pair(\"two\",2, \"three\",3)", true );
        test( "pair(\"one\",1, \"two\",list(2,3), \"three\",true):clone() == pair(\"one\",1, \"two\",list(2,3), \"three\",true)", true );

        // TODO: add pending methods to test pair class

        System.out.println();
    }

    @Test
    public void testFutures()
    {
        IXprEval xprAfterTrue = new NAXE().build( "var == 7 AFTER 3s",
                                                  (value) -> assertEquals( value, 7.0f ),
                                                  null );
                 xprAfterTrue.set( "var", 7 );


    }

    //------------------------------------------------------------------------//

    private void print( String xpr )
    {
        XprPreProc preproc = new XprPreProc( new Lexer( xpr ).getLexemes(), null );

        if( ! preproc.getErrors().isEmpty() )
            throw new MingleException("Errors");

        System.out.println( new EvalByAST( preproc.getAsInfix() ).eval() );
    }

    private void test( String xpr, Object expected, Pair<String,Object>... vars  )
    {
        Map<String,Object> mapVars = new HashMap<>();

        if( (vars != null) && (vars.length > 0) )
        {
            mapVars = new HashMap<>();

            for( Pair<String,Object> pair : vars )
                mapVars.put( pair.getKey(), pair.getValue() );
        }

        try
        {
            Object result;

            XprPreProc preproc = new XprPreProc( new Lexer( xpr ).getLexemes(), null );

            if( ! preproc.getErrors().isEmpty() )
            {
                preproc.getErrors().forEach( (err) -> System.out.println( err ) );
                fail();
            }

            List<XprToken> lstInfix = preproc.getAsInfix();
            EvalByAST      ast      = new EvalByAST( lstInfix );

            if( ast.getErrors().isEmpty() )   // ast includes all errors detected by rpn
            {
                for( Map.Entry<String,Object> entry : mapVars.entrySet() )
                     ast.set( entry.getKey(), entry.getValue() );

                result = ast.eval();

                assertEquals( expected , result );

                String sVars = "";

                if( vars != null && vars.length > 0 )
                {
                    sVars = "   [";

                    for( Pair p : vars )
                        sVars += p.getKey() +"="+ p.getValue() +", ";

                    sVars = UtilStr.removeLast( sVars, 2 ) +']';
                }

                System.out.println( xpr +" --> "+ result + sVars );
            }
            else
            {
                ast.getErrors().forEach( (err) -> System.out.println( err ) );
                fail();
            }
        }
        catch( Exception exc )
        {
            exc.printStackTrace( System.err );
            fail();
            throw exc;
        }
    }

    //------------------------------------------------------------------------//
    // METHODS FOR FUTURES

    private static void doAfterForTrue()
    {
        IXprEval xprAfterTrue = new NAXE().build( "var == 7 AFTER 3000",
                                          (value) -> assertEquals( value, Boolean.TRUE ),
                                          null );

        xprAfterTrue.eval( "var", 7.0f );
    }

    private static void doAfterForFalse()
    {
        IXprEval xprAfterFalse = new NAXE().build( "var == 7 AFTER 3000",
                                                   (value) -> assertEquals( value, Boolean.FALSE ),
                                                   null );

        xprAfterFalse.eval( "var", 7.0f );   // Starts being TRUE

        new Timer().schedule( new TimerTask()
                                {   @Override
                                    public void run() { xprAfterFalse.set( "var", 9.0f ); }    // Becomes FALSE
                                }, 1000 );                                                     // after 1 second
    }

    // FIXME: hacerlos

    private static void doWithinForTrue()
    {

    }

    private static void doWithinForFalse()
    {

    }

    private static void doBothForTrue()
    {

    }

    private static void doBothForFalse()
    {

    }
}