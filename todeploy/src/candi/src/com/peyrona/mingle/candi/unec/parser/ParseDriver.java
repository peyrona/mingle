
package com.peyrona.mingle.candi.unec.parser;

import com.peyrona.mingle.candi.unec.transpiler.UnecTools;
import com.peyrona.mingle.lang.japi.CommandSerializer;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This class represents a transpiled DRIVER command.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ParseDriver extends ParseBase
{
    public  final        String                script;          // Script name (that holds the driver)
    private final        Set<DriverConfigItem> config;          // { <name1>:<dataType1>, ... , <nameN>:<dataTypeN> }
    private final static String                sKEY = "DRIVER";

    //------------------------------------------------------------------------//
    // STATIC INTERFACE

    public static boolean is( String source )
    {
        return UtilStr.startsWith( source, sKEY );
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public ParseDriver( List<Lexeme> lstToken )
    {
        super( lstToken, "driver", "script", "config" );

        name   = findID( sKEY );
        script = getScript( getClauseContents( "script" ) );
        config = getConfig( getClauseContents( "config" ) );
    }

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    /**
     * The only purpose of this data is to serve for Device checkings.
     *
     * @return
     */
    public Set<DriverConfigItem> getConfig()
    {
       return Collections.unmodifiableSet( config );
    }

    @Override
    public String serialize()
    {
        return CommandSerializer.Driver( name.text().toLowerCase(),
                                         script.toLowerCase() );
    }

    //------------------------------------------------------------------------//
    // PROTECTED INTERFACE

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    /**
     * 'SCRIPT' clause is mandatory, can not be empty and must be only one token.
     *
     * @param aoToken
     */
    private String getScript( List<Lexeme> lstToken )
    {
        if( isClauseMissed( "SCRIPT", lstToken ) ||   // These methods in super class adds the error
            isClauseEmpty(  "SCRIPT", lstToken ) ||
            isNotOneToken(  "SCRIPT", lstToken ) )
        {
            return null;
        }

        Lexeme id = findID( "SCRIPT" );

        return (id == null) ? null : id.text();
    }

    /**
     * 'CONFIG' is optional, but when existing, can no be empty.
     *
     * @param aoToken
     */
    private Set<DriverConfigItem> getConfig( List<Lexeme> tokens )
    {
        if( tokens == null ||                       // It is OK because 'CONFIG' clause is optional
            isClauseEmpty( "CONFIG", tokens ) )     // But if exists, it can not be empty (this method (in super class) adds the error)
        {
            return new HashSet<>();
        }

        Set<DriverConfigItem> lst2Ret   = new HashSet<>();
        List<List<Lexeme>>    lstCfgAll = UnecTools.splitByDelimiter( tokens );

        for( List<Lexeme> lstCfg : lstCfgAll )
        {
            String  id     = null;     // A name (it is before AS)
            String  type;              // e.g. "boolean" or "any"
            boolean bRequi = false;    // REQUIRED clause

            if( (lstCfg.size() < 3) || (lstCfg.size() > 4) )
            {
                addError( "Invalid syntax, expected: \"<name> AS <data_type> [REQUIRED] [, ...]\"", findInClause( "config", lstCfg.get(0).text() ) );
            }
            else
            {
                if( validateName( lstCfg.get(0) ) )
                {
                    id = lstCfg.get(0).text().toLowerCase();      // Une is case in-sensitive
                }

                if( ! "AS".equalsIgnoreCase( lstCfg.get(1).text() ) )
                {
                    addError( "\"AS\" clause expected but found: \""+ lstCfg.get(1).text() +'"', lstCfg.get(1) );
                }

                type = lstCfg.get(2).text().toLowerCase();
                type = (UtilColls.toList( "any","number","boolean","string" ).contains( type ) ? type : null);    // NEXT: right now I only admit basic data types. In future all types whould be admited (I'm too fucking tired to do it now)

                if( type == null )
                    addError( "Not a valid data type.", lstCfg.get(2) );

                if( lstCfg.size() == 4 )
                {
                    bRequi = lstCfg.get(3).isCommandWord() &&
                             "REQUIRED".equalsIgnoreCase( lstCfg.get(3).text() );

                    if( ! bRequi )
                    {
                        addError( "\"REQUIRED\" expected but found: \""+ lstCfg.get(0).text() +'"', lstCfg.get(0) );
                    }
                }

                if( (id != null) && (type != null) )
                    lst2Ret.add( new DriverConfigItem( id, type, bRequi ) );
            }
        }

        List<String> lstDuplica = UtilColls.findDuplicates( lst2Ret );

        if( ! lstDuplica.isEmpty() )
        {
            lstDuplica.removeIf( str -> UtilStr.sEoL.equals( str ) || Language.isDelimiter( str ) );

            addError( "Following names are duplicated in \"CONFIG\" clause: "+ Arrays.toString( lstDuplica.toArray() ), tokens.get(0) );
        }

        return lst2Ret;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Used to check that device's driver configuration is valid
    //------------------------------------------------------------------------//
    public final class DriverConfigItem
    {
        public final String  name;
        public final String  type;       // "any" == any-type
        public final boolean required;

        private DriverConfigItem( String name, String type, boolean required )
        {
            this.name     = name.toLowerCase();
            this.type     = type.toLowerCase();
            this.required = required;

            if( ! "boolean,number,string,any".contains( this.type ) )
                throw new IllegalStateException();
        }

        public boolean isSameType( Lexeme value )
        {
            switch( type )
            {
                case "any"    : return true;
                case "boolean": return value.isBoolean();
                case "number" : return value.isNumber();
                case "string" : return value.isString();
                default       : return false;
            }
        }

        @Override
        public String toString()
        {
            return UtilStr.toString( this );
        }

        @Override
        public int hashCode()
        {
            int hash = 5;
                hash = 97 * hash + Objects.hashCode( this.name );
                hash = 97 * hash + Objects.hashCode( this.type );
                hash = 97 * hash + (this.required ? 1 : 0);
            return hash;
        }

        @Override
        public boolean equals( Object obj )
        {
            if( this == obj ) return true;
            if( obj == null ) return false;
            if( getClass() != obj.getClass() ) return false;

            final DriverConfigItem other = (DriverConfigItem) obj;

            if( this.required != other.required ) return false;
            if( ! Objects.equals( this.name, other.name ) ) return false;

            return Objects.equals( this.type, other.type );
        }
    }
}