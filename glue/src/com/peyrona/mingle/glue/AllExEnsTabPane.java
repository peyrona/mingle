package com.peyrona.mingle.glue;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.glue.exen.Pnl4ExEn;
import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.glue.gswing.GTabbedPane;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 * A TabbePane where each tab contains 5 JList (one per command type) and below a TabbedPane with tables with information.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class AllExEnsTabPane extends GTabbedPane
{
    private final BufferedImage img = createTransparentImage();
    private File  fUne = null;
    private Image scaledImage;
    private int   lastWidth = -1;
    private int   lastHeight = -1;

    //------------------------------------------------------------------------//

    AllExEnsTabPane()
    {
        setOpaque( true );
        setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT );
        setBorder( new EmptyBorder( 0, 7, 7, 7 ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Add a new tab.
     */
    void add( String sConnName, ExEnClient cc )
    {
        SwingUtilities.invokeLater( () ->
        {
            addTab( sConnName,
                    new Pnl4ExEn( cc ),
                    (ActionEvent evt) ->
                                        {
                                            int tabIndex = getTabIndexWhichButtonIs( (JButton) evt.getSource() );

                                            if( tabIndex >= 0 )
                                            {
                                                Component comp = getComponentAt( tabIndex );
                                                removeTabAt( tabIndex );                        // Remove tab from UI immediately (EDT safe)

                                                if( comp instanceof Pnl4ExEn )
                                                {
                                                    new SwingWorker<Void, Void>()
                                                    {
                                                        @Override
                                                        protected Void doInBackground()
                                                        {
                                                            ((Pnl4ExEn) comp).disconnect();     // Network teardown off the EDT
                                                            return null;
                                                        }
                                                    }.execute();
                                                }
                                            }
                                        } );

            AllExEnsTabPane.this.validate();
            setSelectedIndex( getTabCount() - 1 );
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
     * Add all commands contained in passed model file to the exiting commands in target ExEn.
     *
     * @param fModel A *.model file.
     */
    void load( File fModel )
    {
        if( getSelectedIndex() > -1 )
        {
            try
            {
                String         sModelJSON = new String( Files.readAllBytes( fModel.toPath() ), StandardCharsets.UTF_8 );
                List<ICommand> lstCmds    = new ArrayList<>();
                ICmdEncDecLib  builder    = UtilSys.getConfig().newCILBuilder();
                JsonObject     joModel    = Json.parse( sModelJSON ).asObject();

                joModel.get( "commands" )
                       .asArray()
                       .forEach( jv -> lstCmds.add( builder.build( jv.toString() ) ) );

                getFocused().add( lstCmds.toArray( ICommand[]::new ) );
            }
            catch( IOException exc )
            {
                JTools.error( "Could not read model file: " + exc.getMessage() );
            }
        }
    }

    /**
     * Saves all commands into a Mingle source code file.
     */
    void save()
    {
        if( getSelectedIndex() > -1 )
        {
            String sUne = getFocused().getUseSourceCode();

            if( UtilStr.isEmpty( sUne ) )  JTools.info( "The editor is empty: nothing to save" );
            else                           saveUneSourceCode( sUne );
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
     * Removes all tabs.
     */
    void close()
    {
        SwingUtilities.invokeLater( () ->
                                    {
                                        while( getTabCount() > 0 )
                                        {
                                            setSelectedIndex( 0 );
                                            del();
                                        }
                                    } );
    }

    //------------------------------------------------------------------------//
    // PROTECTED

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent( g );

        int currentWidth = getWidth();
        int currentHeight = getHeight();

        // Only create new scaled image if sizeAsPercent has changed
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

            // Only draw if scaledImage is not null
            if( scaledImage != null )
            {
                // Calculate position to center the image
                int x = (getWidth() - scaledImage.getWidth( null )) / 2;
                int y = (getHeight() - scaledImage.getHeight( null )) / 2;

                g2d.drawImage( scaledImage, x, y, this );
            }
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
        Component selected = getSelectedComponent();
        return (selected instanceof Pnl4ExEn) ? (Pnl4ExEn) selected : null;
    }

    private void saveUneSourceCode(String sCode)
    {
        UneEditorUnit pnlEditor = new UneEditorUnit( sCode );

        JButton btnSave = new JButton( "Save to file " );
                btnSave.setIcon( IconFontSwing.buildIcon( FontAwesome.FLOPPY_O, 16, JTools.getIconColor() ) );
                btnSave.addActionListener( (ActionEvent evt) -> fUne = JTools.fileSaver( JTools.FileType.Une, fUne, pnlEditor.getText() ) );

        new GFrame()
                .title( "An opportunity to review the script prior to save it" )
                .icon( "editor-256x256.png" )
                .onClose( JFrame.DISPOSE_ON_CLOSE )
                .closeOnEsc()
                .put( btnSave, BorderLayout.NORTH )
                .put( pnlEditor, BorderLayout.CENTER )
                .setVisible();
    }

    //------------------------------------------------------------------------//
    // PRIVATE STATIC SCOPE (related with background image)

    /**
     * Creates a transparent version of the image at the specified path.
     *
     * @param imagePath Path to the original image
     * @return A new BufferedImage with applied transparency
     */
    private static BufferedImage createTransparentImage()
    {
        BufferedImage originalImage = JTools.getImage( "glue.png" );

        if( originalImage == null )
        {
            return null;
        }

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

    /**
     * Scales the image maintaining aspect ratio to fit within the given dimensions.
     *
     * @param image        Source image to scale
     * @param targetWidth  Maximum width
     * @param targetHeight Maximum height
     * @return Scaled image that maintains aspect ratio
     */
    private Image getScaledImage(Image image, int targetWidth, int targetHeight)
    {
        if( image == null )
        {
            return null;
        }

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

        return image.getScaledInstance( scaledWidth - 50, scaledHeight - 50, Image.SCALE_SMOOTH );
    }
}