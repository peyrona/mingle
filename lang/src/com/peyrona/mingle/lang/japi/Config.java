
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Config implements IConfig
{
    private String    sURI;
    private JsonArray jaModules = null;
    private UtilCLI   cli       = null;   // Arguments passed at CLI

    //------------------------------------------------------------------------//
    // load() is used as constructor because interfaces can not define constructors

    @Override
    public IConfig load( String sUri ) throws IOException
    {
        if( UtilStr.isEmpty( sUri ) )
            sUri = new File( UtilSys.fHomeDir, "config.json" ).toURI().toString();

        try
        {
            List<URI> lstURIs = UtilIO.expandPath( sUri );

            if( lstURIs.isEmpty() )
                throw new IOException( sUri +": file(s) not found." );

            if( lstURIs.size() > 1 )
                throw new IOException( "One and only one file needed." );

            sURI = lstURIs.get( 0 ).toString();

            String sJSON = UtilIO.getAsText( lstURIs.get( 0 ) );

            if( UtilStr.isNotEmpty( sJSON ) )     // If empty, default values will be used
            {
                JsonValue jv = UtilJson.parse( UtilStr.removeComments( sJSON ) );

                if( ! jv.isArray() )
                    throw new IOException( "Invalid config" );

                JsonArray ja = jv.asArray();

                // If JSON was saved using ::toStrJSON, now we have to undo the CLI  (clarity over efficiency)
                for( int n = 0; n < ja.size(); n++ )
                {
                    if( ja.get( n ).isObject() && ja.get( n ).asObject().names().contains( "Command_Line_Interface_Options" ) )
                    {
                        JsonArray jaCli = ja.get( n ).asObject().get("Command_Line_Interface_Options").asArray();
                        String[]  asCli = new String[ jaCli.size() ];

                        for( int x = 0; x < jaCli.size(); n++ )
                            asCli[n] = jaCli.get( x ).asString();

                        cli = new UtilCLI( asCli );

                        ja.remove( n );
                        break;
                    }
                }
                //-------------------------------------------------------------------------------------------

                jaModules = ja;
            }
        }
        catch( URISyntaxException use )
        {
            throw new IOException( use );
        }

        return this;
    }

    //------------------------------------------------------------------------//

    @Override
    public String getURI()
    {
        return sURI;
    }

    @Override
    public IConfig setCliArgs( String[] as )
    {
        cli = new UtilCLI( as );

        return this;
    }

    @Override
    public IConfig set( String module, String varName, Object newValue )
    {
        JsonObject jo = getModule( module );

        if( jo == null )
            throw new MingleException( module +": module does not exists" );

        if( newValue == null )
            newValue = Json.NULL;

        JsonValue jv = (newValue instanceof JsonValue) ? ((JsonValue) newValue)
                                                       : UtilType.toJson( newValue.toString() );    // toString() is needed to invoke proper UtilType.toJson(...) method

        jo.remove( varName ).add( varName, jv );

        return this;
    }

    @Override
    public <T> T get( String module, String varName, T defValue )
    {
        // Personal note: In my NetBeans, I run Stick and Glue passing: -Dfaked_drivers="true"
        //                It is done at: NB -> Project Properties -> Run -> VM Options

        if( UtilStr.isEmpty( varName ) )
            return null;

        varName = varName.trim();

        // First we search among CLI asArgs

        if( cli != null )
        {
            if( cli.hasOption( varName ) )     // Case not sensitive
                return toSameType( cli.getValue( varName ), defValue );
        }

        // Second we search among System.getProperty (CLI: it has precedence over config.json) (e.g.: -Dfaked_drivers=true)

        if( System.getProperty( varName ) != null )                                  // Case sensitive
            return toSameType( System.getProperty( varName ), defValue );

        for( Map.Entry<Object,Object> entry : System.getProperties().entrySet() )    // Case not sensitive
        {
            if( entry.getKey().toString().equalsIgnoreCase( varName ) )
            {
                if( entry.getValue() != null )
                    return toSameType( entry.getValue().toString(), defValue );
            }
        }

        // Third search among O.S. environment vars

        String sValue = System.getenv( varName );                              // Case sensitive (O.S. environment)

        if( sValue != null )
            return toSameType( sValue, defValue );

        for( Map.Entry<String,String> entry : System.getenv().entrySet() )     // Case not sensitive (O.S. environment)
        {
            if( entry.getKey().equalsIgnoreCase( varName ) )
            {
                sValue = entry.getValue();

                if( sValue != null )
                    return toSameType( sValue, defValue );
            }
        }

        // Fourth (and last) search among config file vars

        JsonObject jo = getModule( module );

        if( jo != null )
        {
            JsonValue jv = jo.get( varName );                // Case sensitive

            if( jv != null )
                return toSameType( jv, defValue );

            for( String key : jo.names() )                   // Case not sensitive
            {
                if( key.equalsIgnoreCase( varName ) )
                {
                    jv = jo.get( key );

                    if( jv != null )
                        return toSameType( jv, defValue );
                }
            }
        }

        return defValue;
    }

    @Override
    public ICandi.IBuilder newLanguageBuilder()
    {
        JsonObject joModu = getModule( "candi" );

        UtilJson module  = (joModu == null) ? null : new UtilJson( joModu );
        UtilJson builder = (module == null) ? null : new UtilJson( module.getObject( "builder", null ) );
        String   sClass  = null;
        String[] asURIs  = null;

        if( builder != null )
        {
            sClass = builder.getString( "class", null );
            asURIs = UtilType.convertArray( UtilJson.toArray( builder.getArray( "uris", null ) ), String.class );
        }

        sClass = (sClass != null) ? sClass : "com.peyrona.mingle.candi.LangBuilder";
        asURIs = (asURIs != null) ? asURIs : new String[] { ("file://"+ UtilSys.fHomeDir +"/lib/candi.jar") };

        return instantiate( ICandi.IBuilder.class, sClass, asURIs );
    }

    @Override
    public ICmdEncDecLib newCILBuilder()
    {
        JsonObject joModu = getModule( "cil" );

        UtilJson   module = (joModu == null) ? null   : new UtilJson( joModu );

        String     sClass = (module == null) ? null   : module.getString( "class", null );
                   sClass = (sClass != null) ? sClass : "com.peyrona.mingle.cil.CilBuilder";

        String[]   asURIs = (module == null) ? null   : UtilType.convertArray( UtilJson.toArray( module.getArray( "uris", null ) ), String.class );
                   asURIs = (asURIs != null) ? asURIs : new String[] { ("file://"+ UtilSys.fHomeDir +"/lib/cil.jar") };

        return instantiate( ICmdEncDecLib.class, sClass, asURIs );
    }

    @Override
    public IXprEval newXprEval()
    {
        JsonObject joModu = getModule( "expreval" );

        UtilJson   module = (joModu == null) ? null    : new UtilJson( joModu );      // Which ExprEval to use

        String     sClass = (module == null) ? null    : module.getString( "class", null );
                   sClass = (sClass != null) ? sClass  : "com.peyrona.mingle.lang.xpreval.NAXE";

        String[]   asURIs = ((module == null) ? null   : UtilType.convertArray( UtilJson.toArray( module.getArray( "uris", null ) ), String.class ));
                   asURIs = ((asURIs != null) ? asURIs : new String[] { ("file://"+ UtilSys.fHomeDir +"/lib/lang.jar") });   // Using default NAXE's JAR

        return instantiate( IXprEval.class, sClass, asURIs );
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    @Override
    public String toStrJSON()
    {
        JsonArray ja = new JsonArray();

        if( jaModules != null )
        {
            for( Iterator<JsonValue> itera = jaModules.iterator(); itera.hasNext(); )
                ja.add( itera.next() );
        }

        if( cli != null )
        {
            JsonArray j = new JsonArray();

            for( String s : cli.getRaw() )
                j.add( s );

            JsonObject jo = new JsonObject()
                                .add( "Command_Line_Interface_Options", j );

            ja.add( jo );
        }

        return ja.toString();
    }

    //------------------------------------------------------------------------//
    // GRID RELATED METHODS

    @Override
    public List<GridNode> getGridNodes()
    {
        JsonObject     joGrid  = getGridModule();
        JsonValue      jaNodes = (joGrid  == null) ? null : joGrid.get( "nodes" );
        List<GridNode> lst2Ret = (jaNodes == null) ? null : new ArrayList<>();

        if( (jaNodes != null) && jaNodes.isArray() )
        {
            jaNodes.asArray()
                     .forEach( (JsonValue jv) ->
                                {
                                    JsonObject jo = (jv.isObject()) ? jv.asObject() : null;

                                    lst2Ret.add( new GridNode( (jo == null) ? null : jo.get( "targets" ),
                                                               (jo == null) ? null : jo.get( "client"  ),
                                                               (jo == null) ? null : jo.get( "uris"    ) ) );
                                });
        }

        return lst2Ret;
    }

    @Override
    public int getGridReconectInterval()
    {
        JsonObject joGrid = getGridModule();
        JsonValue  jvTime = (joGrid == null) ? new JsonObject().add( "reconnect", -1 )
                                             : joGrid.get( "reconnect" );

        return (jvTime.isNumber() ? joGrid.getInt( "reconnect", -1 )
                                  : UtilType.toInteger( UtilUnit.toMillis( joGrid.getString( "reconnect", "-1" ) ) ));
    }

    @Override
    public boolean isGridDeaf()
    {
        JsonObject joGrid = getGridModule();
        JsonValue  jvDeaf = (joGrid == null) ? null : joGrid.get( "deaf" );

        return (jvDeaf != null) && jvDeaf.isBoolean() && jvDeaf.asBoolean();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private JsonObject getModule( String sName )
    {
        if( UtilStr.isNotEmpty( sName ) && (jaModules != null) )
        {
            for( int n = 0; n < jaModules.size(); n++ )
            {
                JsonObject joModule = jaModules.get( n ).asObject();

                if( joModule.getString( "module", "" ).equalsIgnoreCase( sName ) )
                    return joModule;
            }
        }

        return null;
    }

    private JsonObject getGridModule()
    {
        JsonObject jo = getModule( "network" );

        if( (jo != null) && (jo.get( "grid" ) != null) )
            return jo.get( "grid" ).asObject();

        return null;
    }

    private <T> T instantiate( Class<T> type, String sFullClassName, String... asURIs )
    {
        try
        {
            return UtilReflect.newInstance( type, sFullClassName, asURIs );
        }
        catch( ClassNotFoundException | InstantiationException | IllegalAccessException   | NoSuchMethodException |
               URISyntaxException     | IOException            | IllegalArgumentException | InvocationTargetException exc )
        {
            String msg ="Fatal error creating: "+ type.getCanonicalName() +
                        "\nClass: "+ sFullClassName +
                        "\nURIs: "+ Arrays.toString( asURIs ) +
                        "\nCan not continue.";

            if( UtilSys.getLogger() == null )   System.err.println( "[" + LocalTime.now() +"] Warning: "+ msg +"\n\nException:\n"+ UtilStr.toStringBrief( exc ) );
            else                                UtilSys.getLogger().log( ILogger.Level.SEVERE, exc, msg );

            System.exit( 1 );
        }

        return null; // Flow never arrives here
    }

    private <T> T toSameType( String sValue, T def )
    {
        if( sValue == null )
            return def;

        sValue = sValue.trim();

        if( ! sValue.isEmpty() &&
            (sValue.charAt( 0 ) == '[' || sValue.charAt( 0 ) == '{') )
        {
            return toSameType( Json.parse( sValue ), def );
        }

        Class<?> defClass = def.getClass();

        if( defClass == String.class  )  return toSameType( Json.value( sValue ), def );
        if( defClass == Boolean.class )  return toSameType( Json.value( UtilType.toBoolean( sValue ) ), def );
        if( defClass == Float.class   )  return toSameType( Json.value( UtilType.toFloat(   sValue ) ), def );
        if( defClass == Integer.class )  return toSameType( Json.value( UtilType.toInteger( sValue ) ), def );
        if( defClass == Long.class    )  return toSameType( Json.value( UtilType.toLong(    sValue ) ), def );
        if( defClass == Double.class  )  return toSameType( Json.value( UtilType.toDouble(  sValue ) ), def );

        throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );
    }

    /**
     * Converts a JsonValue to the same type as the provided default value.
     * Returns the default value if the JsonValue is null or not compatible.
     *
     * @param value The JsonValue to convert
     * @param def The default value to return if conversion fails
     * @return The converted value or the default value
     */
    private <T> T toSameType( JsonValue value, T def )
    {
        if( value == null || value.isNull() )
            return def;

        if( def == null )
            throw new MingleException( "'def' is null" );

        Class<?> defClass = def.getClass();

        if( value.getClass() == defClass )    // Is it already the same class?
            return (T) value;                 // Then, nothing to do

        if( defClass == String.class )
        {
            return (T) (value.isString() ? value.asString() : value.toString());
        }
        else if( defClass == Integer.class || defClass == int.class )
        {
            if( value.isNumber() )
                return (T) Integer.valueOf( value.asInt() );
        }
        else if( defClass == Long.class || defClass == long.class )
        {
            if( value.isNumber() )
                return (T) Long.valueOf( value.asLong() );
        }
        else if( defClass == Double.class || defClass == double.class )
        {
            if( value.isNumber() )
                return (T) Double.valueOf( value.asDouble() );
        }
        else if( defClass == Float.class || defClass == float.class )
        {
            if( value.isNumber() )
                return (T) Float.valueOf( value.asFloat() );
        }
        else if( defClass == Boolean.class || defClass == boolean.class )
        {
            if( value.isBoolean() )
                return (T) Boolean.valueOf( value.asBoolean() );
        }
        else if( JsonObject.class.isAssignableFrom( defClass ) )
        {
            if( value.isObject() )
                return (T) value.asObject();
        }
        else if( def.getClass().isArray() )
        {
            if( value.isArray() )
            {
                if( def.getClass().isArray() )     // User is passing 'String[]' instead of just 'String'
                    defClass = def.getClass().getComponentType();

                return (T) UtilType.convertArray( UtilJson.toArray( value ), defClass );
            }
        }

        throw new MingleException( "Conversion is not supported or types don't match" );
    }
}
