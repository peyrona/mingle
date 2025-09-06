
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import java.io.File;
import java.io.IOException;

/**
 * ServiceImages class extends ServiceBase to provide image management operations
 * through HTTP requests. This class handles GET, POST, and DELETE requests
 * to manage image files associated with specific boards.
 * <br><br>
 * Note: This class only manages the images associated with the CheckBoxes.
 * <br><br>
 *
 * <p>
 * HTTP methods and parameters:
 * <ul>
 * <li>GET: Retrieves the names of all image files in the images directory of a specific board.
 *   <ul>
 *   <li>board: Name of the board whose images are to be listed (required)
 *   </ul>
 * <li>POST: This method is not implemented and will return an error.
 * <li>DELETE: Deletes a specific image file associated with a board.
 *   <ul>
 *   <li>board: Name of the board (required)
 *   <li>image: Name of the image file to delete (required)
 *   </ul>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class ServiceImages extends ServiceBase
{
    //------------------------------------------------------------------------//

    static File getImageFile( String sBoardName, String sImageName ) throws IOException
    {
        return new File( getImagesDir( sBoardName ), sImageName );
    }

    //------------------------------------------------------------------------//

    ServiceImages( HttpServerExchange xchg )
    {
        super( xchg );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Returns all files name inside ./images folder.
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doGet() throws IOException
    {
        String   sBoard = asString( "board", null );
        String[] asImgs = getImagesDir( sBoard ).list();

        sendJSON( UtilJson.toJSON( asImgs ).toString() );
    }

    /**
     * Uploads one (and only one) file and it is stored in 'images' folder.
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override   // POST == APPEND
    protected void doPost() throws IOException
    {
        sendError( "Improper service", StatusCodes.NOT_FOUND );   // This is done at ::save
    }

    /**
     * Borra un fichero específico (pasado en la request).
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doDelete() throws IOException
    {
        String sBoard = asString( "board", null );
        String sImage = asString( "image", null );
        File   fImage = getImageFile( sBoard, sImage );

        if( fImage.exists() )
        {
            if( ! fImage.delete() )
                sendError( "Can not delete: "+ fImage.getName(), StatusCodes.UNAUTHORIZED );    // 401
        }
        else
        {
            sendError( "Not found: "+ fImage.getName(), StatusCodes.NOT_FOUND );                // 404.
        }
    }

    //------------------------------------------------------------------------//

    private static File getBoardDir( String sBoardName ) throws IOException
    {
        if( UtilStr.endsWith( sBoardName, ".html" ) )
            sBoardName = UtilStr.removeLast( sBoardName, 5 );

        return new File( Util.getBoardsDir(), sBoardName );        // Board folder inside 'dashboards' folder
    }

    private static File getImagesDir( String sBoardName ) throws IOException
    {
        return new File( getBoardDir( sBoardName ), "images" );    // Images folder inside Board folder
    }
}