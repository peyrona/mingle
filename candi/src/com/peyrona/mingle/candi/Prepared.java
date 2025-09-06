
package com.peyrona.mingle.candi;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.lexer.CodeError;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class Prepared implements ICandi.IPrepared
{
    private String              code      = null;
    private String              callName  = null;
    private List<ICandi.IError> lstErrors = null;
    private Map<String,Object>  mapExtras = new HashMap<>();

    //------------------------------------------------------------------------//

    public Prepared()
    {
    }

    public Prepared( String code )
    {
        setCode( code );
    }

    public Prepared( byte[] code )
    {
        setCode( code );
    }

    //------------------------------------------------------------------------//

    public Prepared setCode( String code )
    {
        this.code = code;   // Can not make .trim(); e.g. Python uses them and /t too
        return this;
    }

    public Prepared setCode( byte[] code )
    {
        this.code = (code == null) ? null
                                   : Base64.getEncoder().encodeToString( code );
        return this;
    }

    @Override
    public String getCode()
    {
        return code;
    }

    @Override
    public String getCallName()
    {
        return callName;
    }

    public Prepared setCallName( String s )
    {
        callName = s;
        return this;
    }

    @Override
    public ICandi.IError[] getErrors()
    {
        ICandi.IError[] aoErrors = new ICandi.IError[0];

        return UtilColls.isEmpty( lstErrors ) ? aoErrors : lstErrors.toArray( aoErrors );
    }

    @Override
    public Object getExtra( String key )
    {
        return UtilColls.isEmpty( mapExtras ) ? null : mapExtras.get( key );
    }

    //------------------------------------------------------------------------//

    public Prepared addError( String sMsg )
    {
        return addError( new CodeError( sMsg, -1, -1 ) );
    }

    public Prepared addError( ICandi.IError error )
    {
        if( lstErrors == null )
            lstErrors = new ArrayList<>();

        lstErrors.add( error );

        return this;
    }

    public ICandi.IPrepared addExtra( String key, Object value )
    {
        if( mapExtras == null )
            mapExtras = new HashMap<>();

        mapExtras.put( key, value );

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

//    private int[] findLinAndCol( int nOffset )
//    {
//        int[] aLinCol = { -1, -1 };
//
//        if( (code == null) || (isBase64) )
//            return aLinCol;
//
//        // sPrevious -> All previous code: from begining to nOffset
//        String sPrevious = code.substring( 0, nOffset ).replaceAll( "\\R", "\n" );     // Java 8 provides an “\R” pattern that matches any Unicode line-break sequence
//
//        aLinCol[0] = UtilStr.countChar( code, '\n' );                       // Lines
//        aLinCol[1] = sPrevious.length() - sPrevious.lastIndexOf( '\n' );    // Columns
//
//        return aLinCol;
//    }
}