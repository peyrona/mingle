
package com.peyrona.mingle.glue;

import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.glue.exen.Pnl4ExEn;
import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.glue.gswing.GTabbedPane;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * A TabbePane where each tab contains 4 JList (one per command type) and below a TabbedPane with
 * tables with information.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class ExEnsTabbedPane extends GTabbedPane
{
    private final BufferedImage img = createTransparentImage();
    private       File  fUne = null;
    private       Image scaledImage;
    private       int   lastWidth  = -1;
    private       int   lastHeight = -1;

    //------------------------------------------------------------------------//

    ExEnsTabbedPane()
    {
        setOpaque( true );
        setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT );
        setBorder( new EmptyBorder( 0, 7, 7, 7 ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Add a new tab and invokes connection Dialog.
     */
    void add()
    {
        add( (Consumer<Pnl4ExEn>) null );
    }

    void add( Consumer<Pnl4ExEn> onTabAdded )
    {
        final Pnl4ExEn pnl = new Pnl4ExEn();

        UtilSys.execute( getClass().getName(),
                        () ->
                        {
                            pnl.connect((exenClient) ->
                                        {
                                            SwingUtilities.invokeLater( () ->
                                                    {
                                                        addTab( exenClient.getName(), pnl, (ActionEvent evt) -> del() );
                                                        ExEnsTabbedPane.this.validate();

                                                        if( onTabAdded != null )
                                                            onTabAdded.accept( getFocused() );
                                                    } );
                                        } );
                        } );
    }

    /**
     * Removes focused tab and closes its connection to ExEn.
     */
    void del()
    {
        if( getSelectedIndex() > -1 )
        {
            getFocused().disconnect();
            removeTabAt( getSelectedIndex() );
        }
    }

    /**
     * Removes all commands from highlighted ExEn sending as many requests as commands to the Exen.
     */
    void clear()
    {
        if( getSelectedIndex() > -1 )
            getFocused().clear();
    }

    /**
     * Saves all commands into a Mingle source code file.
     */
    void save()
    {
        if( getSelectedIndex() > -1 )
        {
            String sUne = getFocused().getUseSourceCode();

            if( UtilStr.isEmpty( sUne ) )
                JTools.alert( "The editor is empty: nothing to save" );
            else
                saveUneSourceCode( sUne );
        }
    }

    /**
     * Removes all tabs.
     */
    void close()
    {
        for( int n = 0; n < getTabCount(); n++ )
        {
            setSelectedIndex( n );
            del();
        }
    }

    void onLoadModel()
    {
        if( getSelectedIndex() == -1 )
            return;

        File[] afModel = JTools.fileLoader( Main.frame, null, false,
                                            new FileNameExtensionFilter( "Model: transpiled script (*.model)", "model" ) );

        if( afModel.length > 0 )
        {
            try
            {
                getFocused().setModel( UtilIO.getAsText( afModel[0] ) );
            }
            catch( IOException exc )
            {
                JTools.error( exc );
            }
        }
    }

    //------------------------------------------------------------------------//
    // PROTECTED

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent( g );

        int currentWidth = getWidth();
        int currentHeight = getHeight();

        // Only create new scaled image if size has changed
        if( scaledImage == null || currentWidth != lastWidth || currentHeight != lastHeight )
        {
            scaledImage = getScaledImage( img, currentWidth, currentHeight );
            lastWidth = currentWidth;
            lastHeight = currentHeight;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        try
        {
            g2d.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR );

            // Calculate position to center the image
            int x = (getWidth()  - scaledImage.getWidth(  null )) / 2;
            int y = (getHeight() - scaledImage.getHeight( null )) / 2;

            g2d.drawImage( scaledImage, x, y, this );
        }
        finally
        {
            g2d.dispose();
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    private Pnl4ExEn getFocused()
    {
        return (Pnl4ExEn) getSelectedComponent();
    }

    private void saveUneSourceCode( String sCode )
    {
        UneEditorUnit pnlEditor = new UneEditorUnit( sCode );

        JButton  btnSave = new JButton( "Save to file ");
                 btnSave.setIcon( IconFontSwing.buildIcon( FontAwesome.FLOPPY_O, 16, JTools.getIconColor() ) );
                 btnSave.addActionListener( (ActionEvent evt) -> fUne = JTools.fileSaver( JTools.FileType.Une, fUne, pnlEditor.getText() ) );

        GFrame.make()
              .title( "An opportunity to review the script prior to save it" )
              .icon( "editor.png" )
              .onClose( JFrame.DISPOSE_ON_CLOSE )
              .closeOnEsc()
              .put( btnSave  , BorderLayout.NORTH  )
              .put( pnlEditor, BorderLayout.CENTER )
              .setVisible();
    }

    //------------------------------------------------------------------------//
    // PRIVATE STATIC

    /**
     * Loads an image from various possible sources (resource, file system, or URL).
     *
     * @param imagePath Path to the image
     * @return The loaded BufferedImage or null if loading fails
     */
    private static BufferedImage loadImage( String imagePath ) throws IOException
    {
        // Try loading as resource first
        URL resourceUrl = ExEnsTabbedPane.class.getResource( imagePath );

        if( resourceUrl != null )
            return ImageIO.read( resourceUrl );

        // Try as file path
        File file = new File( imagePath );

        if( file.exists() )
            return ImageIO.read( file );

        // Try as URL
        try
        {
            return ImageIO.read( new URL( imagePath ) );
        }
        catch( IOException e )
        {
            return null;
        }
    }

    /**
     * Creates a transparent version of the image at the specified path.
     *
     * @param imagePath Path to the original image
     * @return A new BufferedImage with applied transparency
     */
    private static BufferedImage createTransparentImage()
    {
        try
        {
            BufferedImage originalImage = loadImage( "/META-INF/splash.png" );

            if( originalImage == null )
                return null;

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            BufferedImage transparentImage = new BufferedImage(
                    width, height, BufferedImage.TYPE_INT_ARGB );

            Graphics2D g2d = transparentImage.createGraphics();
            try
            {
                g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
                g2d.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, 0.1f ) );
                g2d.drawImage( originalImage, 0, 0, null );
            }
            finally
            {
                g2d.dispose();
            }

            return transparentImage;
        }
        catch( IOException e )
        {
            return null;
        }
    }

    /**
     * Scales the image maintaining aspect ratio to fit within the given dimensions.
     *
     * @param image Source image to scale
     * @param targetWidth Maximum width
     * @param targetHeight Maximum height
     * @return Scaled image that maintains aspect ratio
     */
    private Image getScaledImage(Image image, int targetWidth, int targetHeight)
    {
        if( image == null )
            return null;

        double originalWidth = image.getWidth( null );
        double originalHeight = image.getHeight( null );

        if( originalWidth == 0 || originalHeight == 0 )
        {
            return image;
        }

        double originalAspect = originalWidth / originalHeight;
        double targetAspect = (double) targetWidth / targetHeight;

        int scaledWidth;
        int scaledHeight;

        if( targetAspect > originalAspect )
        {
            // Target is wider than original
            scaledHeight = targetHeight;
            scaledWidth = (int) (scaledHeight * originalAspect);
        }
        else
        {
            // Target is taller than original
            scaledWidth = targetWidth;
            scaledHeight = (int) (scaledWidth / originalAspect);
        }

        return image.getScaledInstance( scaledWidth, scaledHeight, Image.SCALE_SMOOTH );
    }
}