
package com.peyrona.mingle.glue.exen.commands;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.candi.unec.parser.ParseRule;
import com.peyrona.mingle.glue.JTools;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorPane;
import com.peyrona.mingle.glue.codeditor.UneEditorTabContent.UneEditorUnit;
import com.peyrona.mingle.glue.gswing.GFrame;
import com.peyrona.mingle.glue.gswing.GList;
import com.peyrona.mingle.lang.interfaces.commands.ICmdKeys;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.lexer.Lexer;
import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class PnlRule extends PnlCmdBase
{
    private static final String                sGROUP_SUFFIX   = " (group)";
    private        final GList<String>         ltbThen;
    private        final ListSelectionListener ltbThenListener = (ListSelectionEvent evt) -> selectedChangedInActionsListBox();
    private              boolean               isStarting      = true;
    private        final UneEditorPane         codeWhen        = UneEditorUnit.newEditor( "" ).setRows( 5 ).hideLineNumbers();
    private        final UneEditorPane         codeIf          = UneEditorUnit.newEditor( "" ).setRows( 5 ).hideLineNumbers();

    //------------------------------------------------------------------------//

    public PnlRule( IRule rule, PnlAllCmdsSelector.CommandWise cmdWise )
    {
        super( cmdWise );

        initComponents();

        ltbThen = new GList<>( lstThen ).setSorted( false );

        if( initExtra() && (rule != null) )
        {
            JsonObject joRule = Json.parse( cmdCodec.unbuild( rule ) ).asObject();

            String    sWhen  = joRule.get( ICmdKeys.RULE_WHEN ).asString();
            String    sIf    = joRule.get( ICmdKeys.RULE_IF   ).isNull() ? null : joRule.get( ICmdKeys.RULE_IF ).asString();
            JsonArray jaActs = joRule.get( ICmdKeys.RULE_THEN ).asArray();

            for( int n = 0; n < jaActs.size(); n++ )            // Fills the ListBox that shows all the actions.
            {
                JsonObject joAction = jaActs.get( n ).asObject();

                int    nAfter  = joAction.getInt( "after", 0 );
                String sValue  = joAction.get( "value" ).isNull() ? "" : joAction.get( "value" ).toString();
                String caption = joAction.get( "target" ).asString() +
                                 (sValue.isEmpty() ? "" : (" = "+ sValue)) +
                                 ((nAfter > 0) ? (" AFTER "+ nAfter) : "");

                ltbThen.add( caption, joAction );
            }

            txtName.setText( rule.name() );
            codeWhen.setText( sWhen );
            codeIf.setText( sIf );

            ltbThen.list.setSelectedIndex( 0 );
            selectedChangedInActionsListBox();
            ltbThen.list.addListSelectionListener( ltbThenListener );     // Don't move
        }

        isStarting = false;
    }

    //------------------------------------------------------------------------//

    @Override
    protected String getSourceCode()
    {
        String        name    = txtName.getText().trim();
        String        sIF     = codeIf.getText().trim();
        StringBuilder actions = new StringBuilder( 1024 );
        String        sSep    = ",\n";

        for( int n = 0; n < ltbThen.getAll().size(); n++ )
        {
            actions.append( (n == 0 ? "" : "\t     ") ).append( ltbThen.get(n) ).append( sSep );
        }

        actions = UtilStr.removeLast( actions, sSep.length() );

        return (name.isEmpty() ? "" : ("RULE "+ name +"\n\t"))+
               "WHEN "+ codeWhen.getText().trim() +"\n\t" +
               "THEN "+ actions +
               (sIF.isEmpty() ? "" : ("\n\tIF "+ sIF));
    }

    @Override
    protected String getTranspiled()
    {
        String    src   = getSourceCode();
        Lexer     lexer = new Lexer( src );
        ParseRule trule = new ParseRule( lexer.getLexemes(), UtilSys.getConfig().newXprEval() );

        return showErrors( src, lexer, trule) ? null : trule.serialize();
    }

    //------------------------------------------------------------------------//

    private boolean initExtra()
    {
        JTools.setJTextIsUneName( txtName );

        // Note: up and down buttons can not be added to this list
        lstBtn2Check4Changes.add( btnThenAdd );
        lstBtn2Check4Changes.add( btnThenDel );

        btnNAXE.setIcon( IconFontSwing.buildIcon( FontAwesome.CALCULATOR, 16, JTools.getIconColor() ) );

        btnThenAdd.setIcon(  IconFontSwing.buildIcon( FontAwesome.PLUS , 16, JTools.getIconColor() ) );
        btnThenDel.setIcon(  IconFontSwing.buildIcon( FontAwesome.TRASH, 16, JTools.getIconColor() ) );

        pnl4EditorWhen.setLayout( new BorderLayout(0,0) );
        pnl4EditorIf.setLayout(   new BorderLayout(0,0) );
        pnl4EditorWhen.add( codeWhen, BorderLayout.CENTER );
        pnl4EditorIf.add(   codeIf  , BorderLayout.CENTER );

        for( IScript scp : cmdWise.getScripts() )
            cmbThenScriptOrRule.addItem( scp.name() );

        for( IRule rul : cmdWise.getRules())
            if( ! rul.name().equals( txtName.getText() ) )      // A Rule can not invoke itself
                cmbThenScriptOrRule.addItem( rul.name() );

        for( IDevice act : cmdWise.getDevices() )
            cmbThenActionActuatorName.addItem( act.name() );

        for( String sGroupName : cmdWise.getGroups( cmdWise.getDevices() ) )
            cmbThenActionActuatorName.addItem( sGroupName + sGROUP_SUFFIX );

        spnAfterAmount.setModel( new SpinnerNumberModel( 0,                 // initial value
                                                         0,                 // min
                                                         Integer.MAX_VALUE, // max
                                                         1 ) );             // step;
        radGroupThen.add( radThenScript );
        radGroupThen.add( radThenAction );
        radThenAction.setSelected( true );

        if( (cmbThenScriptOrRule.getItemCount() == 0) || (cmbThenActionActuatorName.getItemCount() == 0) )
            JTools.alert( "Prior to create a RULE you must create\nat least one SCRIPT or one device" );
        return (cmbThenScriptOrRule.getItemCount() > 0);
    }

    private void updateSelectedItemInActionsListbox()
    {
        if( isStarting )
            return;

        int    nAfter  = ((Number) spnAfterAmount.getModel().getValue()).intValue();

        String sTarget = (radThenScript.isSelected() ? cmbThenScriptOrRule.getSelectedItem().toString()
                                                     : cmbThenActionActuatorName.getSelectedItem().toString());

        String sValue  = (radThenScript.isSelected() ? ""
                                                     : txtThenActionActuatorValue.getText().trim());

        String sAction = (radThenScript.isSelected() ? sTarget
                                                     : sTarget +" = "+ sValue);

        if( nAfter > 0 )
        {
            String  sUnit  = cmbAfterUnit.getSelectedItem().toString();
            char    chUnit = sUnit.charAt( sUnit.length() -2 );
            sAction       += " AFTER "+ nAfter + chUnit;
        }

        JsonObject joAction = Json.object()
                                  .add( "target", sTarget )
                                  .add( "method", "value" )
                                  .add( "value" , sValue  )
                                  .add( "after" , nAfter  );

        ltbThen.udpate( sAction, joAction );
    }

    private void selectedChangedInActionsListBox()
    {
        String sAction = ltbThen.getSelected();

        if( sAction == null )
            return;

        final JsonObject joAction = (JsonObject) ltbThen.getSelectedAssociated();
        final String     sTarget  = joAction.get( "target" ).asString();

        if( cmdWise.isScript( sTarget ) )
        {
            radThenAction.setSelected( false );
            radThenScript.setSelected( true  );

            int index = JTools.getIndexForItem( cmbThenScriptOrRule.getModel(),
                                                item -> item.toString().equals( sTarget ) );

            cmbThenScriptOrRule.setSelectedIndex( index );
        }
        else
        {
            radThenAction.setSelected( true  );
            radThenScript.setSelected( false );

            int index = JTools.getIndexForItem( cmbThenActionActuatorName.getModel(),
                                                item -> item.toString().equals( sTarget ) ||
                                                        item.toString().equals( sTarget + sGROUP_SUFFIX ) );    // because groups "doors" is "doors (group)" in the combobox

            String sValue = (joAction.get( "value" ).isString() ? joAction.get( "value" ).asString()
                                                                : joAction.get( "value" ).toString() );

            txtThenActionActuatorValue.setText(UtilType.toUne( sValue ).toString() );    // Do not move
            cmbThenActionActuatorName.setSelectedIndex( index );                           // Do not move
        }

        spnAfterAmount.getModel().setValue( joAction.getInt( "after", 0 ) );
    }

    private void onRadioThenSelected()
    {
        cmbThenScriptOrRule.setEnabled( radThenScript.isSelected() );
        cmbThenActionActuatorName.setEnabled( radThenAction.isSelected() );
        txtThenActionActuatorValue.setEnabled( radThenAction.isSelected() );
        updateSelectedItemInActionsListbox();
    }

    //------------------------------------------------------------------------//

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        radGroupThen = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        txtName = new javax.swing.JTextField();
        pnlWHEN = new javax.swing.JPanel();
        pnl4EditorWhen = new javax.swing.JPanel();
        pnlTHEN = new javax.swing.JPanel();
        radThenScript = new javax.swing.JRadioButton();
        cmbThenScriptOrRule = new javax.swing.JComboBox<>();
        txtThenActionActuatorValue = new javax.swing.JTextField();
        cmbThenActionActuatorName = new javax.swing.JComboBox<>();
        radThenAction = new javax.swing.JRadioButton();
        jLabel7 = new javax.swing.JLabel();
        spnAfterAmount = new javax.swing.JSpinner();
        cmbAfterUnit = new javax.swing.JComboBox<>();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstThen = new javax.swing.JList<>();
        btnThenAdd = new javax.swing.JButton();
        btnThenDel = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        radThenExpr = new javax.swing.JRadioButton();
        txtThenExpr = new javax.swing.JTextField();
        pnlIF = new javax.swing.JPanel();
        pnl4EditorIf = new javax.swing.JPanel();
        btnNAXE = new javax.swing.JButton();
        pnlIF1 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        jLabel2.setText("RULE");

        txtName.setColumns(24);
        txtName.setName("ControlName"); // NOI18N

        pnlWHEN.setBorder(javax.swing.BorderFactory.createTitledBorder(" WHEN "));

        javax.swing.GroupLayout pnl4EditorWhenLayout = new javax.swing.GroupLayout(pnl4EditorWhen);
        pnl4EditorWhen.setLayout(pnl4EditorWhenLayout);
        pnl4EditorWhenLayout.setHorizontalGroup(
            pnl4EditorWhenLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        pnl4EditorWhenLayout.setVerticalGroup(
            pnl4EditorWhenLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 83, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout pnlWHENLayout = new javax.swing.GroupLayout(pnlWHEN);
        pnlWHEN.setLayout(pnlWHENLayout);
        pnlWHENLayout.setHorizontalGroup(
            pnlWHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlWHENLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pnl4EditorWhen, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        pnlWHENLayout.setVerticalGroup(
            pnlWHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlWHENLayout.createSequentialGroup()
                .addComponent(pnl4EditorWhen, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlTHEN.setBorder(javax.swing.BorderFactory.createTitledBorder(" THEN "));

        radThenScript.setText("Execute Script or Rule");
        radThenScript.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                radThenScriptActionPerformed(evt);
            }
        });

        cmbThenScriptOrRule.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cmbThenScriptOrRuleActionPerformed(evt);
            }
        });

        txtThenActionActuatorValue.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                txtThenActionActuatorValueKeyReleased(evt);
            }
        });

        cmbThenActionActuatorName.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cmbThenActionActuatorNameActionPerformed(evt);
            }
        });

        radThenAction.setText("Update Actuator state");
        radThenAction.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                radThenActionActionPerformed(evt);
            }
        });

        jLabel7.setText("AFTER");

        spnAfterAmount.setModel(new javax.swing.SpinnerNumberModel(0L, 0L, null, 1L));
        spnAfterAmount.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                spnAfterAmountStateChanged(evt);
            }
        });

        cmbAfterUnit.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1/100 of a second (u)", "1/10 of a second (t)", "Seconds  (s)", "Minutes (m)", "Hours (h)", "Days (d)" }));
        cmbAfterUnit.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cmbAfterUnitActionPerformed(evt);
            }
        });

        lstThen.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(lstThen);

        btnThenAdd.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnThenAddActionPerformed(evt);
            }
        });

        btnThenDel.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnThenDelActionPerformed(evt);
            }
        });

        jLabel1.setText("Value / Expr");

        radThenExpr.setText("Evaluate expression");
        radThenExpr.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                radThenExprActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlTHENLayout = new javax.swing.GroupLayout(pnlTHEN);
        pnlTHEN.setLayout(pnlTHENLayout);
        pnlTHENLayout.setHorizontalGroup(
            pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlTHENLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnlTHENLayout.createSequentialGroup()
                        .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(radThenAction, javax.swing.GroupLayout.DEFAULT_SIZE, 162, Short.MAX_VALUE)
                            .addComponent(radThenScript, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(pnlTHENLayout.createSequentialGroup()
                                .addComponent(cmbThenActionActuatorName, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtThenActionActuatorValue, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE))
                            .addComponent(cmbThenScriptOrRule, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(pnlTHENLayout.createSequentialGroup()
                        .addComponent(jScrollPane1)
                        .addGap(12, 12, 12)
                        .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnThenDel, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(btnThenAdd, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(pnlTHENLayout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spnAfterAmount, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cmbAfterUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(pnlTHENLayout.createSequentialGroup()
                        .addComponent(radThenExpr)
                        .addGap(31, 31, 31)
                        .addComponent(txtThenExpr)))
                .addContainerGap())
        );

        pnlTHENLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {btnThenAdd, btnThenDel});

        pnlTHENLayout.setVerticalGroup(
            pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlTHENLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(radThenScript)
                    .addComponent(cmbThenScriptOrRule, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(15, 15, 15)
                .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(radThenAction)
                    .addComponent(cmbThenActionActuatorName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(txtThenActionActuatorValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(radThenExpr)
                    .addComponent(txtThenExpr, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(spnAfterAmount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbAfterUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(pnlTHENLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnlTHENLayout.createSequentialGroup()
                        .addComponent(btnThenAdd)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnThenDel))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlTHENLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {btnThenAdd, btnThenDel});

        pnlIF.setBorder(javax.swing.BorderFactory.createTitledBorder(" IF "));

        javax.swing.GroupLayout pnl4EditorIfLayout = new javax.swing.GroupLayout(pnl4EditorIf);
        pnl4EditorIf.setLayout(pnl4EditorIfLayout);
        pnl4EditorIfLayout.setHorizontalGroup(
            pnl4EditorIfLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        pnl4EditorIfLayout.setVerticalGroup(
            pnl4EditorIfLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 89, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout pnlIFLayout = new javax.swing.GroupLayout(pnlIF);
        pnlIF.setLayout(pnlIFLayout);
        pnlIFLayout.setHorizontalGroup(
            pnlIFLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlIFLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pnl4EditorIf, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        pnlIFLayout.setVerticalGroup(
            pnlIFLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlIFLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(pnl4EditorIf, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        btnNAXE.setText("fn(x)");
        btnNAXE.setToolTipText("Open Expressions Evaluator dialog");
        btnNAXE.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnNAXEActionPerformed(evt);
            }
        });

        pnlIF1.setBorder(javax.swing.BorderFactory.createTitledBorder(" USE ... AS ..."));

        jLabel3.setText("TODO");

        javax.swing.GroupLayout pnlIF1Layout = new javax.swing.GroupLayout(pnlIF1);
        pnlIF1.setLayout(pnlIF1Layout);
        pnlIF1Layout.setHorizontalGroup(
            pnlIF1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 721, Short.MAX_VALUE)
            .addGroup(pnlIF1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(pnlIF1Layout.createSequentialGroup()
                    .addGap(0, 0, Short.MAX_VALUE)
                    .addComponent(jLabel3)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );
        pnlIF1Layout.setVerticalGroup(
            pnlIF1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 113, Short.MAX_VALUE)
            .addGroup(pnlIF1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(pnlIF1Layout.createSequentialGroup()
                    .addGap(0, 0, Short.MAX_VALUE)
                    .addComponent(jLabel3)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnlIF, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlWHEN, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlTHEN, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(txtName)
                        .addGap(18, 18, 18)
                        .addComponent(btnNAXE))
                    .addComponent(pnlIF1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnNAXE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pnlWHEN, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pnlTHEN, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pnlIF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pnlIF1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnThenAddActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnThenAddActionPerformed
    {//GEN-HEADEREND:event_btnThenAddActionPerformed
        String sTarget = (cmdWise.getDevices().isEmpty() ? "unknown" : cmdWise.getDevices().get(0).name() );

        JsonObject joAction = Json.object()
                                  .add( "target", sTarget )
                                  .add( "method", "value" )
                                  .add( "value" , true    )
                                  .add( "after" , 0       );

        ltbThen.add( sTarget +" = true", joAction );
    }//GEN-LAST:event_btnThenAddActionPerformed

    private void btnThenDelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnThenDelActionPerformed
    {//GEN-HEADEREND:event_btnThenDelActionPerformed
        if( ltbThen.model.getSize() > 1 )
            ltbThen.remove();
        else
            JTools.alert( "A RULE must have at least one action (THEN clause)" );
    }//GEN-LAST:event_btnThenDelActionPerformed

    private void spnAfterAmountStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_spnAfterAmountStateChanged
    {//GEN-HEADEREND:event_spnAfterAmountStateChanged
        updateSelectedItemInActionsListbox();
    }//GEN-LAST:event_spnAfterAmountStateChanged

    private void txtThenActionActuatorValueKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_txtThenActionActuatorValueKeyReleased
    {//GEN-HEADEREND:event_txtThenActionActuatorValueKeyReleased
        updateSelectedItemInActionsListbox();
    }//GEN-LAST:event_txtThenActionActuatorValueKeyReleased

    private void radThenScriptActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_radThenScriptActionPerformed
    {//GEN-HEADEREND:event_radThenScriptActionPerformed
        onRadioThenSelected();
    }//GEN-LAST:event_radThenScriptActionPerformed

    private void radThenActionActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_radThenActionActionPerformed
    {//GEN-HEADEREND:event_radThenActionActionPerformed
        onRadioThenSelected();
    }//GEN-LAST:event_radThenActionActionPerformed

    private void cmbThenScriptOrRuleActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmbThenScriptOrRuleActionPerformed
    {//GEN-HEADEREND:event_cmbThenScriptOrRuleActionPerformed
        updateSelectedItemInActionsListbox();
    }//GEN-LAST:event_cmbThenScriptOrRuleActionPerformed

    private void cmbThenActionActuatorNameActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmbThenActionActuatorNameActionPerformed
    {//GEN-HEADEREND:event_cmbThenActionActuatorNameActionPerformed
        updateSelectedItemInActionsListbox();
    }//GEN-LAST:event_cmbThenActionActuatorNameActionPerformed

    private void cmbAfterUnitActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmbAfterUnitActionPerformed
    {//GEN-HEADEREND:event_cmbAfterUnitActionPerformed
        updateSelectedItemInActionsListbox();
    }//GEN-LAST:event_cmbAfterUnitActionPerformed

    private void btnNAXEActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnNAXEActionPerformed
    {//GEN-HEADEREND:event_btnNAXEActionPerformed
        GFrame.make()  // I prefer a Frame over a Dialog
              .title( "Expression Evaluator" )
              .icon( "xpr_eval.png" )
              .onClose( JFrame.DISPOSE_ON_CLOSE )
              .put( new PnlExprEval(), BorderLayout.CENTER )
              .setVisible()
              .toFront();
    }//GEN-LAST:event_btnNAXEActionPerformed

    private void radThenExprActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_radThenExprActionPerformed
    {//GEN-HEADEREND:event_radThenExprActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_radThenExprActionPerformed

    //------------------------------------------------------------------------//

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnThenAdd;
    private javax.swing.JButton btnThenDel;
    private javax.swing.JButton btnNAXE;
    private javax.swing.JComboBox<String> cmbAfterUnit;
    private javax.swing.JComboBox<String> cmbThenActionActuatorName;
    private javax.swing.JComboBox<String> cmbThenScriptOrRule;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList<String> lstThen;
    private javax.swing.JPanel pnl4EditorIf;
    private javax.swing.JPanel pnl4EditorWhen;
    private javax.swing.JPanel pnlIF;
    private javax.swing.JPanel pnlIF1;
    private javax.swing.JPanel pnlTHEN;
    private javax.swing.JPanel pnlWHEN;
    private javax.swing.ButtonGroup radGroupThen;
    private javax.swing.JRadioButton radThenAction;
    private javax.swing.JRadioButton radThenExpr;
    private javax.swing.JRadioButton radThenScript;
    private javax.swing.JSpinner spnAfterAmount;
    private javax.swing.JTextField txtName;
    private javax.swing.JTextField txtThenActionActuatorValue;
    private javax.swing.JTextField txtThenExpr;
    // End of variables declaration//GEN-END:variables
}
