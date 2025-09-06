
package com.peyrona.mingle.controllers.daikin.emura;

/**
 * Todas las 'enum' utilizadas por Emura.
 *
 * La librería de código Java y el código HTML originales están aquí:
 *    + https://bitbucket.org/JonathanGiles/jdaikin/
 *    + https://github.com/ael-code/daikin-control/blob/master/web_gui/api.php
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class Const
{
    static enum What
    {
        Power             ( "pow"    ),
        Mode              ( "mode"   ),
        Fan               ( "f_rate" ),
        Wings             ( "f_dir"  ),
        TargetHumidity    ( "shum"   ),
        TargetTemperature ( "stemp"  ),
        InsideTemperature ( "htemp"  ),
        OutsideTemperature( "otemp"  );

        final String key;

        private What( String key ) { this.key = key; }
    };

    static enum Power
    {
        Off( "0" ),
        On ( "1" );

        final String key;

        private Power( String key ) { this.key = key; }

        static Power key2enum( String s )
        {
            if( "0".equals( s ) ) return Off;
            if( "1".equals( s ) ) return On;

            throw new AssertionError();
        }

        static String enum2key( boolean on )
        {
            return ( on ? On.key : Off.key );
        }
    };

    static enum Mode
    {
        Auto( "0" ),
        Dry ( "2" ),
        Cool( "3" ),
        Heat( "4" ),
        Fan ( "6" );

        final String key;

        private Mode( String key ) { this.key = key; }

        static Mode key2enum( String s )
        {
            if( "0".equals( s ) ) return Auto;
            if( "2".equals( s ) ) return Dry;
            if( "3".equals( s ) ) return Cool;
            if( "4".equals( s ) ) return Heat;
            if( "6".equals( s ) ) return Fan;

            throw new AssertionError();
        }

        static String enum2key( String s )
        {
            s = s.trim().toLowerCase();

            if( "auto".equals( s ) ) return "0";
            if( "dry".equals(  s ) ) return "2";
            if( "cool".equals( s ) ) return "3";
            if( "heat".equals( s ) ) return "4";
            if( "fan".equals(  s ) ) return "6";

            throw new AssertionError();
        }
    };

    static enum Fan
    {
        Auto   ( "A" ),
        Off    ( "B" ),
        Minimum( "3" ),
        Low    ( "4" ),
        Medium ( "5" ),
        High   ( "6" ),
        Maximum( "7" );

        final String key;

        private Fan( String key ) { this.key = key; }

        static Fan key2enum( String s )
        {
            if( "A".equals( s ) ) return Auto;
            if( "B".equals( s ) ) return Off;
            if( "3".equals( s ) ) return Minimum;
            if( "4".equals( s ) ) return Low;
            if( "5".equals( s ) ) return Medium;
            if( "6".equals( s ) ) return High;
            if( "7".equals( s ) ) return Maximum;

            throw new AssertionError();
        }

        static String enum2key( String s )
        {
            s = s.trim().toLowerCase();

            if( "auto".equals(    s ) ) return "A";
            if( "off".equals(     s ) ) return "B";
            if( "minimum".equals( s ) ) return "3";
            if( "low".equals(     s ) ) return "4";
            if( "medium".equals(  s ) ) return "5";
            if( "high".equals(    s ) ) return "6";
            if( "maximum".equals( s ) ) return "7";

            throw new AssertionError();
        }
    };

    static enum Wings
    {
        None      ( "0" ),
        Vertical  ( "1" ),
        Horizontal( "2" ),
        Both      ( "3" );

        final String key;

        private Wings( String key ) { this.key = key; }

        static Wings key2enum( String s )
        {
            if( "0".equals( s ) ) return None;
            if( "1".equals( s ) ) return Vertical;
            if( "2".equals( s ) ) return Horizontal;
            if( "3".equals( s ) ) return Both;

            throw new AssertionError();
        }

        static String enum2key( String s )
        {
            s = s.trim().toLowerCase();

            if( "none".equals(       s ) ) return "0";
            if( "vertical".equals(   s ) ) return "1";
            if( "horizontal".equals( s ) ) return "2";
            if( "both".equals(       s ) ) return "3";

            throw new AssertionError();
        }
    };

    //----------------------------------------------------------------------------//

    private Const()
    {
        // Impide la creación de instancias de esta clase.
    }
}