
package com.peyrona.mingle.candi.unec.transpiler;

import com.peyrona.mingle.candi.IntermediateCodeWriter;
import com.peyrona.mingle.candi.unec.parser.ParseBase;
import com.peyrona.mingle.candi.unec.parser.ParseDevice;
import com.peyrona.mingle.candi.unec.parser.ParseDriver;
import com.peyrona.mingle.candi.unec.parser.ParseDriver.DriverConfigItem;
import com.peyrona.mingle.candi.unec.parser.ParseRule;
import com.peyrona.mingle.candi.unec.parser.ParseRuleThen;
import com.peyrona.mingle.candi.unec.parser.ParseRuleThen.Action;
import com.peyrona.mingle.candi.unec.parser.ParseScript;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.CodeError;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// NEXT: puedo dar la opción de que Tape reciba todos los *.une que forman un Grid y entonces sí puedo hacer todas las comprobaciones que hago cuando no se trata de un Grid.

/**
 * Makes all possible validations at the highest level.
 * <p>
 * Although every command makes its own validations (inner-command-validations),
 * there are some validations that can only be performed by knowing all the
 * commands (inter-command-validations).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Checker
{
    private final Map<String,List<String>> mapDeviceGroups = new HashMap<>();      // Key == group-name, Value == devices-in-this-group
    private final IConfig config;

    //------------------------------------------------------------------------//

    Checker( IConfig config )
    {
        this.config = config;
    }

    //------------------------------------------------------------------------//

    void check( List<TransUnit> tus )
    {
        // Inter-commands checks can be performed only if the unit has no errors.
        // In fact it could be done (because almost everything can be done), but it does not worth it.
        // And it is a common practice among compilers to make a two-phases process when cheking errors.

        for( TransUnit tu : tus )
        {
            if( tu.hasErrors() )
                return;
        }

        boolean isForGrid = config.get( null, "grid", false ) || (config.getGridNodes() != null);

        if( ! isForGrid )
        {
            checkCmdsName( tus );

            // When there are errors in names, can not continue with further checks

            for( TransUnit tu : tus )
            {
                if( tu.hasErrors() )
                    return;
            }
        }

        for( TransUnit tu : tus )
        {
            for( ParseBase cmd : tu.getCommands() )
            {
                     if( cmd instanceof ParseScript ) checkScript();
                else if( cmd instanceof ParseDriver ) checkDriver();
                else if( cmd instanceof ParseDevice ) checkDevice( (ParseDevice) cmd, tu, tus, config.newCILBuilder() );
            }
        }

        // Now that all devices had been processed, we have all groups and their members:
        // now we can check RULE's expressions (WHEN, THEN and IF)

        for( TransUnit tu : tus )
        {
            for( ParseBase cmd : tu.getCommands() )
            {
                if( cmd instanceof ParseRule )
                {
                    checkRuleWhen( (ParseRule) cmd, tu, tus, isForGrid );
                    checkRuleThen( (ParseRule) cmd, tu, tus, isForGrid );
                    checkRuleIf(   (ParseRule) cmd, tu, tus, isForGrid );
                }
            }
        }
    }

    //------------------------------------------------------------------------//
    // NOTE: there is nothing to check for INCLUDEs and USEs commands

    private void checkScript()
    {
        // There is nothing to check because the code can be transpiled in a machine that is not the
        // same machine where it will be executed. Therefore, files and URLs could be not accessible
        // where the code is being transpiled but they could accesible where code is executed.
        // And checks made at driver command level are all checks that can be done.

        // NEXT: se podría utilizar una var de configuración que indicara al Transpiler que en la
        //       máquina donde él está haciendo su trabajo hay acceso a los mismos ficheros y URLs
        //       que en la máquina donde se va a ejecutar el código y que por lo tanto, se pueden
        //       realizar estas comprobaciones.
    }

    private void checkDriver()
    {
        // Nothing to do: checks made at driver command level are all needed checks.
    }

    private void checkDevice( ParseDevice device, TransUnit tuDeviceOwner, List<TransUnit> tus, ICmdEncDecLib pclBuilder )
    {
        // Lets check that DEVICE DRIVER CONFIG initialization matches with its DRIVER CONFIG definition

        ParseDriver driver = (ParseDriver) findByName( device.drvName, tus );

        // If DRIVER clause does not exists, the ParseDevice triggers an error,
        // If driver name is misspelled, it was detected and reported when cheking names.

        if( driver != null )
        {
            // Lets check that all REQUIRED items in DRIVER CONFIG exist in DEVICE DRIVER CONFIG (but does not check its type)

            for( DriverConfigItem oDrvConfItem : driver.getConfig().stream().filter( cfg -> cfg.required ).collect( Collectors.toList() ) )
            {
                boolean bFound = false;

                for( String name : device.getDriverInit().keySet() )
                {
                    if( oDrvConfItem.name.equals( name ) )
                    {
                        bFound = true;
                        break;
                    }
                }

                if( ! bFound )
                {
                    Lexeme lex = device.getClause( "config" );                         // REQUIRED property should be inside CONFIG clause, but
                           lex = (lex == null) ? device.getClause( "driver" ) : lex;   // it could be that not even the CONFIG clause is declared (although DRIVER clause will exist: it is mandatory)

                    tuDeviceOwner.addError( new CodeError( '"'+ oDrvConfItem.name +"\" is REQUIRED by Driver \""+ device.drvName +"\", but is not declared.", lex ) );
                }
            }

            // Lets check that all items (required and not required) in DEVICE DRIVER CONFIG exist in DRIVER CONFIG and they have the proper data type

            for( Map.Entry<String,Lexeme> entry : device.getDriverInit().entrySet() )
            {
                String  sDvcDrvCfgName  = entry.getKey();      // Device Driver config item name   (e.g. "interval")
                Lexeme  oDvcDrvCfgValue = entry.getValue();    // Device Driver config item value  (e.g. 3000.0 (as Lexeme))
                boolean bFound          = false;

                for( DriverConfigItem oDrvConfItem : driver.getConfig() )
                {
                    if( oDrvConfItem.name.equals( sDvcDrvCfgName ) )
                    {
                        if( ! oDrvConfItem.isSameType( oDvcDrvCfgValue ) )
                        {
                            tuDeviceOwner.addError( new CodeError( '"'+ sDvcDrvCfgName +"\": invalid data type. Expected '"+ oDrvConfItem.type, oDvcDrvCfgValue ) );
                        }

                        bFound = true;
                        break;
                    }
                }

                if( ! bFound )
                {
                    tuDeviceOwner.addError( new CodeError( '"'+ sDvcDrvCfgName +"\" is not declared in Driver \""+ device.drvName +'"',
                                                           device.findInClause( "config", sDvcDrvCfgName ) ) );
                }
            }
        }

        // Lets check that DEVICE INIT initialization is valid: properties exist in device (there is a method with same name) and
        // the value received (by the method) is of expected type.
        // This can not be done in a generic way because the CIL is loaded at runtime: so current CIL implementation must be asked.

        for( Map.Entry<String,Lexeme> entry : device.getDeviceInit().entrySet() )
        {
            String sDvcInitName  = entry.getKey();                         // Device init item name   (e.g. "interval")
            Object oDvcInitValue = UtilType.toUne( entry.getValue() );     // Device init item value  (e.g. 3000.0 (as Lexeme))
            Object oValid        = pclBuilder.checkProperty( sDvcInitName, oDvcInitValue );

            if( oValid == Number.class || oValid == String.class || oValid == Boolean.class )
            {
                tuDeviceOwner.addError( new CodeError( "\""+ oDvcInitValue +"\": invalid data type for property '"+ sDvcInitName +"'; expected '"+ oValid +"'",
                                                       device.findInClause( "init", sDvcInitName ) ) );
            }
            else if( ! ((Boolean) oValid) )
            {
                tuDeviceOwner.addError( new CodeError( '"'+ sDvcInitName +"\" property does not exits for DEVICEs.",
                                                       device.findInClause( "init", sDvcInitName ) ) );
            }
        }

        for( Map.Entry<String,Lexeme> entry : device.getDeviceInit().entrySet() )
        {
            if( entry.getKey().equals( "groups" ) )
            {
                String[] asGroups = entry.getValue().text().split( "," );    // e.g.: "Windows, Doors, Lights" --> { "Windows ", "Doors ", "Lights" }

                for( String sGroup : asGroups )
                {
                    sGroup = sGroup.trim().toLowerCase();     // e.g.: { "windows", "doors", "lights" }

                    if( ! mapDeviceGroups.containsKey( sGroup ) )
                        mapDeviceGroups.put( sGroup, new ArrayList<>() );

                    mapDeviceGroups.get( sGroup ).add( device.getName().text().toLowerCase() );
                }
            }
        }
    }

    private void checkRuleWhen( ParseRule rule, TransUnit tuRuleOwner, List<TransUnit> tus, boolean b4Grid )
    {
        if( UtilStr.isEmpty( rule.getWhen() ) )     // Can not be empty but user could leave it empty: this 'if' avoids a null-pointer error
            return;

        IXprEval xpr = checkXpr4WhenOrIf( rule, tuRuleOwner, tus, b4Grid, rule.getWhen(), "when" );

        if( xpr.getErrors().isEmpty() )
        {
            if( ! xpr.isBoolean() )
                tuRuleOwner.addError( new CodeError( "WHEN is not an expression of type boolean", rule.getClauseContents( "when" ).get( 0 ) ) );

            if( xpr.getVars().isEmpty() && UtilStr.isEmpty( rule.getName() ) )
                tuRuleOwner.addError( new CodeError( "WHEN has no devices and RULE has no name: the RULE is useless.", rule.getClauseContents( "when" ).get( 0 ) ) );

            if( xpr.getErrors().isEmpty() && IntermediateCodeWriter.isRequired() )
            {
                // TODO: mostrar el IXprEval::intern()  (no olvidar especificar el fnGroupWise)
            }
        }
    }

    private void checkRuleThen( ParseRule rule, TransUnit tuRuleOwner, List<TransUnit> tus, boolean b4Grid )
    {
        for( ParseRuleThen.Action action : rule.getThen().getActions() )
        {
            ParseBase target   = findByName( action.getTargetName(), tus );    // target could be null
            boolean   isDevice = (target instanceof ParseDevice);
            boolean   isScript = (target instanceof ParseScript);
            boolean   isRule   = (target instanceof ParseRule  );
            boolean   isGroup  = mapDeviceGroups.containsKey( action.getTargetName() );

            if( (! isDevice) && (! isGroup) && (! isScript) && (! isRule) && action.isNotOf( Action.Type.Expression ) )    // if target is not of these
            {
                tuRuleOwner.addError( new CodeError( '"'+ action.getTargetName() +"\" there is not a DEVICE (or Group) or SCRIPT or RULE with such name.",
                                                     rule.findInClause( "then", action.getTargetName() ) ) );

                continue;     // <------------------------- CONTINUE with next for(...) iteration
            }

            if( action.isOf( Action.Type.Expression ) )
            {
                assert UtilStr.isEmpty( action.getValueToSet() );    // Checked only to validate my code

                if( action.isOf( Action.Type.Expression ) )
                    checkXpr4Action( rule, tuRuleOwner, tus, b4Grid, action.getTargetName() );    // When Action.Type.Expression, the xpr is in TargetName

                continue;     // <------------------------- CONTINUE with next for(...) iteration
            }

            if( action.isOf( Action.Type.RuleOrScript ) && (! b4Grid) )
            {
                if( ! isRuleOrScript( action.getTargetName(), tus ) )
                {
                    tuRuleOwner.addError( new CodeError( "\""+ action.getTargetName() +"\" no RULE, neither SCRIPT exists with such name",
                                                         rule.findInClause( "then", action.getTargetName() ) ) );
                }
                continue;     // <------------------------- CONTINUE with next for(...) iteration
            }

            // Now we know it has to be: THEN {<device> | <group>} = <expression>
            // In action "console = date()", the target is the left side: "console" (must be a device of type actuator).
            // Whatever after the = is, it must be a constant or a device's name or an expression.

            assert (isDevice || isGroup);

            if( action.isOf( Action.Type.AssignExpression ) )           // Lets check that variable names are declared devices
            {
                assert UtilStr.isNotEmpty( action.getValueToSet() );    // Checked only to validate my code

                checkXpr4Action( rule, tuRuleOwner, tus, b4Grid, action.getValueToSet().toString() );
            }
            else if( action.isOf( Action.Type.AssignDevice ) )          // Lets check that the device's name belongs to a declared devices
            {
                if( (findByName( action.getValueToSet().toString(), tus ) == null) )    // It is a device's name; e.g.: console = my_device
                {
                    tuRuleOwner.addError( new CodeError( "\""+ action.getValueToSet() +"\" must be either an expression or a device's name.",
                                                         rule.findInClause( "then", action.getTargetName() ) ) );
                }
            }
         // else it is a basic data type (a constant)  --> Nothing to check
        }
    }

    private void checkRuleIf( ParseRule rule, TransUnit tuRuleOwner, List<TransUnit> tus, boolean b4Grid )
    {
        if( UtilStr.isEmpty( rule.getIf() ) )
            return;

        IXprEval xpr = checkXpr4WhenOrIf( rule, tuRuleOwner, tus, b4Grid, rule.getIf(), "if" );

        if( xpr.getErrors().isEmpty() && (! xpr.isBoolean()) )
            tuRuleOwner.addError( new CodeError( "IF is not an expression of type boolean", rule.getClauseContents( "if" ).get( 0 ) ) );

        // Lets check that IF includes at leats one AFTER or one WITHIN

        boolean bHasAfterOrWithin = false;

        for( Lexeme lex : rule.getIf() )
        {
            if( lex.isCommandWord() )
            {
                if( lex.isText( "after"  ) ||
                    lex.isText( "within" ) )
                {
                    bHasAfterOrWithin = true;
                    break;
                }
            }
        }

        if( ! bHasAfterOrWithin )
        {
            tuRuleOwner.addError( new CodeError( "IF must include at least: one AFTER or one WITHIN clause", rule.getClauseContents( "if" ).get( 0 ) ) );
        }
    }

    /**
     * Checks that all referred names (for Devices, Scripts, Drivers and Rules) exist and that there are no duplicated names.
     *
     * @param tus
     */
    private void checkCmdsName( List<TransUnit> tus )
    {
        // Fills a map to look for duplicated names while filling

        Map<String, Pair<TransUnit, ParseBase>> mapNames = new HashMap<>();    // To check all command names are unique

        for( TransUnit tu : tus )
        {
            for( ParseBase pc : tu.getCommands() )
            {
                if( UtilStr.isNotEmpty( pc.getName() ) )
                {
                    String name = pc.getName().text().toLowerCase();

                    if( mapNames.containsKey( name ) )  createDuplicateNameError( tu, pc, mapNames );     // Name is duplicated
                    else                                mapNames.put( name, new Pair( tu, pc ) );
                }
            }
        }

        // Checks that SCRIPTs referred from drivers exist and that DRIVERS referred from DEVICEs exist

        IXprEval xprEval = config.newXprEval();

        for( TransUnit tu : tus )
        {
            if( tu.autoInclude() && (! AutoInclude.init( xprEval )) )
                tu.addErrors( AutoInclude.lstErrors );

            boolean bAuto = tu.autoInclude() && AutoInclude.init( xprEval );

            // Check that device's driver names exitst as driver ( ¡clarity over efficiency! )
            for( ParseDevice pd : tu.getCommands( ParseDevice.class ) )
            {
                if( pd.drvName != null )    // Although it is mandatory it could be null at this moment
                {
                    if( bAuto && ! AutoInclude.addDriver2Unit( pd.drvName, tu ) )
                    {
                        tu.addError( new CodeError( "DEVICE refers to a DRIVER named \""+ pd.drvName +"\", but there is no DRIVER with such name.",
                                                    pd.findInClause( "driver", pd.drvName ) ) );
                    }
                }
            }

            // Check that driver's script names exitst as script ( ¡clarity over efficiency! ).
            // Scripts muct be checked after checking Drivers because some Drivers could be added and they need their Script.
            for( ParseDriver pd : tu.getCommands( ParseDriver.class ) )
            {
                if( pd.script != null  )    // Although it is mandatory it could be null at this moment
                {
                    if( bAuto && ! AutoInclude.addScript2Unit( pd.script, tu ) )
                    {
                        tu.addError( new CodeError( "DRIVER refers to a SCRIPT named \""+ pd.script +"\", but there is no SCRIPT with such name.",
                                                    pd.findInClause( "script", pd.script ) ) );
                    }
                }
            }

        }
    }

    private void createDuplicateNameError( TransUnit tu, ParseBase pb, Map<String, Pair<TransUnit,ParseBase>> mapNames )
    {
        String sCmdName = pb.getName().text();

        Pair<TransUnit,ParseBase> pair = mapNames.get( sCmdName.toLowerCase() );

        Lexeme token1 = pair.getValue().getStart();
        Lexeme token2 = pb.getStart();
        String file1  = pair.getKey().sourceUnit.uri;
        String file2  = tu.sourceUnit.uri;

        String msg = "Duplicated name: \""+ sCmdName +"\" is used by:\n"+
                     "  -> "+ token1.text() +" in file \""+ file1 +"\", line "+ token1.line() +'\n'+
                     " and also by:\n"+
                     "  -> "+ token2.text() +" in file \""+ file2 +"\", line "+ token2.line() +'\n';

        tu.addError( new CodeError( msg, token2 ) );
    }

    private ParseBase findByName( String sName, List<TransUnit> tus )
    {
        for( TransUnit tu : tus )
        {
            for( ParseBase cmd : tu.getCommands() )
            {
                if( cmd.getName() != null && sName.equalsIgnoreCase( cmd.getName().text() ) )
                    return cmd;
            }
        }

        return null;
    }

    private void checkXpr4Action( ParseRule rule, TransUnit tuRuleOwner, List<TransUnit> tus, boolean is4Grid, String sXpr )
    {
        Pair<IXprEval,List<String>> result = checkXpr( tus, is4Grid, new Lexer( sXpr ).getLexemes() );
        IXprEval                    xpr    = result.getKey();
        List<String>                lstVar = result.getValue();    // Vars that does not exist (were not declared as DEVICEs)

        if( xpr.getErrors().isEmpty() )
        {
            for( String sVarName : lstVar )
            {
                Lexeme lex = rule.findInClause( "then", sVarName );

                tuRuleOwner.addError( new CodeError( '"'+ sVarName +"\": there is not a DEVICE (or Group) or SCRIPT or RULE with such name.", lex ) );
            }
        }
        else
        {
            int    ndx = rule.getThen().getActionStart( sXpr );
            Lexeme lex = rule.getThen().getLexemeAt( ndx );

            tuRuleOwner.addErrors( UnecTools.updateErrorLine( lex.line(), xpr.getErrors() ) );
        }
    }

    private IXprEval checkXpr4WhenOrIf( ParseRule rule, TransUnit tuRuleOwner, List<TransUnit> tus, boolean is4Grid, List<Lexeme> lexXpr, String sWhere2Look )
    {
        Pair<IXprEval,List<String>> result = checkXpr( tus, is4Grid, lexXpr );
        IXprEval                    xpr    = result.getKey();
        List<String>                lstVar = result.getValue();    // Vars that does not exist (were not declared as DEVICEs)

        if( xpr.getErrors().isEmpty() )
        {
            for( String sVarName : lstVar )
            {
                Lexeme lex = rule.findInClause( sWhere2Look, sVarName );

                tuRuleOwner.addError( new CodeError( '"'+ sVarName +"\": is not a declared DEVICE", lex ) );
            }
        }
        else
        {
            tuRuleOwner.addErrors( UnecTools.updateErrorLine( lexXpr.get(0).line(), xpr.getErrors() ) );
        }

        return xpr;
    }

    /**
     * Returns a Pair where 'key' is the expression and 'value' is a list of non declared variables.
     *
     * @param tus
     * @param is4Grid
     * @param lexXpr
     * @return A Pair where 'key' is the expression and 'value' is a list of non declared variables.
     */
    private Pair<IXprEval,List<String>> checkXpr( List<TransUnit> tus, boolean is4Grid, List<Lexeme> lexXpr )
    {
        String                    str = Lexer.toCode( lexXpr );
        Function<String,String[]> fn  = (sGroupName) -> { String[]     a0  = new String[0];
                                                          List<String> lst = mapDeviceGroups.get( sGroupName );
                                                          return ((lst == null) ? a0 : lst.toArray( a0 )); };
        IXprEval                  xpr = config.newXprEval().build( str, (result) -> {}, fn );     // The expression is valid because it was checked previously by class ParseRule
        List<String>              lst = new ArrayList<>();   // Vars that does not exist (were not declared as DEVICEs)

        if( xpr.getErrors().isEmpty() )
        {
            if( ! is4Grid )    // If this script is for a Grid, DEVICEs could be defined in another script (.une)
            {
                for( String sVarName : xpr.getVars().keySet() )
                {
                    if( ! (findByName( sVarName, tus ) instanceof ParseDevice) )
                        lst.add( sVarName );
                }
            }
        }

        return new Pair( xpr, lst );
    }

    private boolean isRuleOrScript( String name, List<TransUnit> tus )
    {
        for( TransUnit tu : tus )
        {
            for( ParseBase cmd : tu.getCommands() )
            {
                if( (cmd instanceof ParseScript || cmd instanceof ParseRule) &&
                    (cmd.getName() != null)                                  &&    // Rules and Scripts can have or not a name
                    name.equalsIgnoreCase( cmd.getName().text() ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private static final class AutoInclude
    {
        static         List<ICandi.IError>       lstErrors  = new ArrayList<>();
        private static Map <String, ParseDriver> mapDrivers = null;
        private static Map <String, ParseScript> mapScripts = null;

        static boolean addDriver2Unit( String name, TransUnit tu )
        {
            for( ParseDriver driver : tu.getCommands( ParseDriver.class ) )
                if( driver.getName().isText( name ) )
                        return true;     // The driver is already part of the Transpiler Unit

            ParseDriver driver = mapDrivers.get( name.toLowerCase() );

            if( driver != null )
                tu.addCommand( driver );

            return (driver != null);
        }

        static boolean addScript2Unit( String name, TransUnit tu )
        {
            for( ParseDriver driver : tu.getCommands( ParseDriver.class ) )
                if( driver.getName().isText( name ) )
                        return true;     // The driver is already part of the Transpiler Unit

            ParseScript script = mapScripts.get( name.toLowerCase() );

            if( script != null )
                tu.addCommand( script );

            return (script != null);
        }

        //------------------------------------------------------------------------//

        private static synchronized boolean init( IXprEval xpreEval )
        {
            if( mapDrivers == null )
            {
                mapDrivers = new HashMap<>( 256 );
                mapScripts = new HashMap<>( 256 );

                try
                {
                    Charset cs = Charset.defaultCharset();

                    for( URI uri :UtilIO.expandPath( "{*home.inc*}/**" ) )
                        process( uri, cs, xpreEval );
                }
                catch( URISyntaxException | IOException ex )
                {
                    // Nothing to do
                }
            }

            return lstErrors.isEmpty();
        }

        // INCLUDEs are not processed because all files (and only the files) in home.inc folder and subfolders are processed
        // Only DRIVER and SCRIPT commands are processed
        // USE ... AS ... are not made: because DRIVER and SCRIPT commands do not need USE ... AS ...
        private static void process( URI uri, Charset cs, IXprEval xprEval )     // Invoked from sync method
        {
            if( ! UtilIO.hasExtension( uri, "une" ) )
                return;

            TransUnit tu = new TransUnit( new SourceUnit( uri, cs ), xprEval );
                      tu.doCommands( null );

            if( tu.hasErrors() )
            {
                lstErrors.addAll( tu.getErrors() );
            }
            else
            {
                for( ParseDriver pd : tu.getCommands( ParseDriver.class ) )
                    mapDrivers.put( pd.getName().text().toLowerCase(), pd );

                for( ParseScript ps : tu.getCommands( ParseScript.class ) )
                    mapScripts.put( ps.getName().text().toLowerCase(), ps );
            }
        }
   }
}