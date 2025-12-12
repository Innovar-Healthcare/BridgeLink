/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.dialog;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.WindowConstants;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.UIConstants;

import net.miginfocom.swing.MigLayout;

public class AdvancedSearchCodeSnippetDialog extends MirthDialog {

    private JLabel snippetLabel;
    private RSyntaxTextArea snippetTextArea;
    private RTextScrollPane snippetScrollPane;

    private JButton copyButton;
    private JButton closeButton;

    private Frame parent;
    private String snippet;

    public AdvancedSearchCodeSnippetDialog(Frame parent, String snippet) {
        super(parent, true);

        this.parent = parent;
        this.snippet = snippet != null ? snippet : "";

        initComponents();
        initLayout();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Advanced Search Code Snippet");

        setPreferredSize(new Dimension(800, 600));
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        snippetLabel = new JLabel("Code Snippet:");

        snippetTextArea = new RSyntaxTextArea(20, 60);
        snippetTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        snippetTextArea.setCodeFoldingEnabled(true);
        snippetTextArea.setAntiAliasingEnabled(true);
        snippetTextArea.setEditable(true);
        snippetTextArea.setText(snippet);

        snippetScrollPane = new RTextScrollPane(snippetTextArea);
        snippetScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        snippetScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        snippetScrollPane.setFoldIndicatorEnabled(true);

        copyButton = new JButton("Copy");
        copyButton.addActionListener(e -> copyToClipboard());

        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> close());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill"));

        JPanel mainPanel = new JPanel(new MigLayout("novisualpadding, hidemode 0, align center, insets 0 0 0 0, fill", "25[right][grow,fill]", ""));
        mainPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder());

        mainPanel.add(snippetLabel, "right");
        mainPanel.add(snippetScrollPane, "wmin 300, hmin 200, grow, push");

        add(mainPanel, "grow, push");
        add(new JSeparator(), "newline, sx, growx");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        buttonPanel.add(copyButton);
        buttonPanel.add(closeButton);

        add(buttonPanel, "newline, sx, growx");
    }

    private void copyToClipboard() {
        StringSelection selection = new StringSelection(snippetTextArea.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        // Optional
        JOptionPane.showMessageDialog(this, "Code snippet copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void close() {
        dispose();
    }
}
