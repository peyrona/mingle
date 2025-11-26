
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilStr;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

/**
 * WebService to retrieve miscellaneous information.
 *
 * (Used by Balata)
 */
final class ServiceUtil extends ServiceBase
{
    private static int timeout = -1;   // In seconds

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    ServiceUtil( HttpServerExchange xchg )
    {
        super( xchg );
    }

    static int getSessionTimeout()
    {
        return ServiceUtil.timeout;
    }

    static void setSessionTimeout( int nSecs )
    {
        ServiceUtil.timeout = nSecs;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    protected void doGet() throws Exception
    {
        String sPath = xchg.getRelativePath();

        if( UtilStr.endsWith( sPath, "sessionTimeout" ) )
        {
            sendText( String.valueOf( timeout ) );   // In seconds
        }
        else
            sendError( sPath, StatusCodes.NOT_FOUND );

//        String sPath = xchg.getRelativePath();
//
//        if( sPath.endsWith( "localip" ) )  sendText( xchg.getSourceAddress()
//                                                         .getAddress()
//                                                         .getHostAddress() );
//        else
//            sendError( sPath, SC_NOT_FOUND );
    }
}