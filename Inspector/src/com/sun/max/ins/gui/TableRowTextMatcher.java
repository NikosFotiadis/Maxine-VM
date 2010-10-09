/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.gui;

import java.awt.*;
import java.util.*;
import java.util.regex.*;

import javax.swing.*;
import javax.swing.table.*;

import com.sun.max.ins.*;

/**
 * A row-based engine for locating rows in a {@link JTable} that match a regexp.
 * Requires that cell renderers in the table implement {@link TextSearchable}.
 *
 * @author Michael Van De Vanter
 * @see {@link java.util.regexp.Pattern}
 */
public class TableRowTextMatcher extends AbstractInspectionHolder implements RowTextMatcher {

    private final JTable table;

    /**
     * The text rows being searched.
     */
    private String[] rowsOfText;

    /**
     * Create a search session for a table.
     *
     * @param inspection
     * @param jTable a table whose cell renderers implement {@link TextSearchable}.
     */
    public TableRowTextMatcher(Inspection inspection, JTable jTable) {
        super(inspection);
        this.table = jTable;
        rowsOfText = rowsOfText(table);
    }

    /**
     * Builds a list of strings corresponding to the current contents
     * of the specified table.
     * <br>
     * Note that this will only see the whole table if no filtering on the table
     * model is in place.
     *
     * @param table
     * @return the text being displayed by each row
     */
    private static String[] rowsOfText(JTable table) {
        final TableModel tableModel = table.getModel();
        final int rowCount = tableModel.getRowCount();
        final TableColumnModel columnModel = table.getColumnModel();
        final int columnCount = columnModel.getColumnCount();
        String[] rowsOfText = new String[rowCount];
        for (int row = 0; row < rowCount; row++) {
            StringBuilder rowText = new StringBuilder();
            for (int col = 0; col < columnCount; col++) {
                final TableColumn column = columnModel.getColumn(col);
                final TableCellRenderer cellRenderer = column.getCellRenderer();
                final int columnIndex = column.getModelIndex();
                final Object valueAt = tableModel.getValueAt(row, columnIndex);
                final Component tableCellRendererComponent = cellRenderer.getTableCellRendererComponent(table, valueAt, false, false, row, columnIndex);
                final TextSearchable searchable = (TextSearchable) tableCellRendererComponent;
                rowText.append(' ').append(searchable.getSearchableText());
            }
            rowsOfText[row] = rowText.toString();
        }
        return rowsOfText;
    }

    public void refresh() {
        rowsOfText = rowsOfText(table);
    }

    public int rowCount() {
        return rowsOfText.length;
    }

    public int[] findMatches(Pattern pattern) {
        String[] rowsOfText = this.rowsOfText;
        final int textRowCount = rowsOfText.length;
        int[] matchingRows = new int[textRowCount];
        int matchingRowCount = 0;
        for (int row = 0; row < textRowCount; row++) {
            if (pattern.matcher(rowsOfText[row]).find()) {
                matchingRows[matchingRowCount++] = row;
            }
        }
        return Arrays.copyOf(matchingRows, matchingRowCount);
    }
}