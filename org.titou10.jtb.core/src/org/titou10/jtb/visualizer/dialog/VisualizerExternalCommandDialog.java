/*
 * Copyright (C) 2015-2017 Denis Forveille titou10.titou10@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.titou10.jtb.visualizer.dialog;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.titou10.jtb.visualizer.VisualizersManager;
import org.titou10.jtb.visualizer.gen.Visualizer;
import org.titou10.jtb.visualizer.gen.VisualizerMessageType;

/**
 * 
 * Ask for a new Visualizer of kind "EXTERNAL_EXEC"
 * 
 * @author Denis Forveille
 *
 */
public class VisualizerExternalCommandDialog extends Dialog {

   private static final String         PLACEHOLDERS_1 = "Placeholders :";
   private static final String         PLACEHOLDERS_2 = "  " + VisualizersManager.PAYLOAD_FILENAME_PLACEHOLDER
                                                        + " : Temporary filename containing the payload";
   private static final String         PLACEHOLDERS_3 = "  " + VisualizersManager.JMS_MSG_TYPE_PLACEHOLDER
                                                        + " : JMS Message Type, either 'TEXT', 'BYTES' or 'MAP'";

   private Visualizer                  visualizer;

   private String                      commandName;
   private List<VisualizerMessageType> listMessageType;

   private Text                        textCommandName;
   private Button                      btnText;
   private Button                      btnBytes;
   private Button                      btnMap;

   public VisualizerExternalCommandDialog(Shell parentShell, Visualizer visualizer) {
      super(parentShell);
      setShellStyle(SWT.RESIZE | SWT.TITLE | SWT.PRIMARY_MODAL);

      this.visualizer = visualizer;
   }

   @Override
   protected void configureShell(Shell newShell) {
      super.configureShell(newShell);
      newShell.setText("Add/Edit an 'External Command' visualizer");
   }

   @Override
   protected Point getInitialSize() {
      Point p = super.getInitialSize();
      return new Point(700, p.y);
   }

   @Override
   protected Control createDialogArea(Composite parent) {
      Composite container = (Composite) super.createDialogArea(parent);
      container.setLayout(new GridLayout(3, false));

      Label lblNewLabel1 = new Label(container, SWT.NONE);
      lblNewLabel1.setText("Target JMS Messages: ");

      Composite compositeKind = new Composite(container, SWT.NONE);
      compositeKind.setLayout(new RowLayout(SWT.HORIZONTAL));
      compositeKind.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));

      btnText = new Button(compositeKind, SWT.CHECK);
      btnText.setText("TextMessage");

      btnBytes = new Button(compositeKind, SWT.CHECK);
      btnBytes.setText("BytesMessage");

      btnMap = new Button(compositeKind, SWT.CHECK);
      btnMap.setText("MapMessage");

      // Command Name

      Label lblNewLabel = new Label(container, SWT.NONE);
      lblNewLabel.setText("Command name: ");

      textCommandName = new Text(container, SWT.BORDER);
      textCommandName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

      Button btnBrowse = new Button(container, SWT.NONE);
      btnBrowse.setText("Browse...");
      btnBrowse.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
         // FileDialog to chose the name of a script
         FileDialog fileDialog = new FileDialog(getParentShell(), SWT.OPEN | SWT.MULTI);
         fileDialog.setText("Select script file");
         String sel = fileDialog.open();
         if (sel != null) {
            textCommandName.setText(sel);
         }
      }));

      Label lblNewLabel2 = new Label(container, SWT.NONE);
      lblNewLabel2.setText(PLACEHOLDERS_1);
      lblNewLabel2.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
      Label lblNewLabel3 = new Label(container, SWT.NONE);
      lblNewLabel3.setText(PLACEHOLDERS_2);
      lblNewLabel3.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
      Label lblNewLabel4 = new Label(container, SWT.NONE);
      lblNewLabel4.setText(PLACEHOLDERS_3);
      lblNewLabel4.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));

      if (visualizer != null) {
         for (VisualizerMessageType visualizerMessageType : visualizer.getTargetMsgType()) {
            switch (visualizerMessageType) {
               case BYTES:
                  btnBytes.setSelection(true);
                  break;
               case MAP:
                  btnMap.setSelection(true);
                  break;
               case TEXT:
                  btnText.setSelection(true);
                  break;
            }
         }
         textCommandName.setText(visualizer.getFileName());
      }

      return container;
   }

   @Override
   protected void okPressed() {

      listMessageType = new ArrayList<>();
      if (btnBytes.getSelection()) {
         listMessageType.add(VisualizerMessageType.BYTES);
      }
      if (btnMap.getSelection()) {
         listMessageType.add(VisualizerMessageType.MAP);
      }
      if (btnText.getSelection()) {
         listMessageType.add(VisualizerMessageType.TEXT);
      }
      if (listMessageType.isEmpty()) {
         btnBytes.setFocus();
         MessageDialog.openError(getShell(), "Error", "A visualizer must be associated to at least one JMS Message Type");
         return;
      }

      commandName = textCommandName.getText().trim();
      if (commandName.isEmpty()) {
         textCommandName.setFocus();
         MessageDialog.openError(getShell(), "Error", "Please enter a command");
         return;
      }

      super.okPressed();
   }

   // ----------------
   // Standard Getters
   // ----------------
   public String getCommandName() {
      return commandName;
   }

   public List<VisualizerMessageType> getListMessageType() {
      return listMessageType;
   }

}
