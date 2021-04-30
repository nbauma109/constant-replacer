package constant.replacer;

import static constant.replacer.ConstantTable.COLS;

import java.io.File;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.apache.commons.lang3.ArrayUtils;

public class ConstantTableListener implements TableModelListener, ListSelectionListener {
    
    private final ConstantReplacer constantReplacer;
    private final ConstantTable table;
    private final Object[][] rows;
    private final File file;
    private final ConstantReplacerDiffPanel diffPanel;
    private boolean adjusting;

    public ConstantTableListener(ConstantReplacer constantReplacer, ConstantTable table, ConstantReplacerDiffPanel diffPanel, Object[][] rows, File file) {
        this.constantReplacer = constantReplacer;
        this.table = table;
        this.rows = rows;
        this.file = file;
        this.diffPanel = diffPanel;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        if (!adjusting) {
            adjusting = true;
            int row = e.getFirstRow();
            int col = e.getColumn();
            ConstantChoiceEditor editor = (ConstantChoiceEditor) table.getColumn(ConstantTable.CONSTANT_NAME).getCellEditor();
            JComboBox<ConstantName> constantNameChoices = editor.getConstantNameChoices(row);
            ConstantName constantName = (ConstantName) constantNameChoices.getSelectedItem();
            table.setValueAt(constantName, row, col);
            Object switchExpression = table.getValueAt(row, ArrayUtils.indexOf(COLS, ConstantTable.SWITCH_EXPRESSION));
            if (switchExpression != null && !"".equals(switchExpression)) {
                for (int i = row + 1; i < table.getRowCount(); i++) {
                    if (switchExpression.equals(table.getValueAt(i, ArrayUtils.indexOf(COLS, ConstantTable.SWITCH_EXPRESSION)))) {
                        String prefix = constantName.getQualifier();
                        if (prefix != null) {
                            String constVal = (String) table.getValueAt(i, ArrayUtils.indexOf(COLS, ConstantTable.CONSTANT_VALUE));
                            JComboBox<ConstantName> choices = (JComboBox<ConstantName>) editor.getTableCellEditorComponent(table, constVal, false, i, col);
                            List<ConstantName> constantNames = ConstantReplacer.findConstantsByQualifier(choices.getModel(), constantName.getQualifier());
                            Object closestMatch = StringUtilities.findClosestMatch(constantNames, constantName);
                            table.setValueAt(closestMatch, i, col);
                        }
                    }
                }
            }
            diffPanel.showReplacements(constantReplacer.buildModificationMap(file, false, rows));
            adjusting = false;
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (listSelectionEvent.getValueIsAdjusting()) {
            return;
        }
        final ListSelectionModel lsm = (ListSelectionModel) listSelectionEvent.getSource();
        if (!lsm.isSelectionEmpty()) {
            final int selectedRow = lsm.getMinSelectionIndex();
            final Integer lineNumber = constantReplacer.getColumnValue(rows[selectedRow], ConstantTable.LINE_NUMBER);
            diffPanel.gotoLineNumber(lineNumber);
        }
    }
}