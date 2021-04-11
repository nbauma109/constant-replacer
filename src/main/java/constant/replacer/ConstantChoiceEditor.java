package constant.replacer;

import static constant.replacer.ConstantReplacer.COLUMN_NUMBER;
import static constant.replacer.ConstantReplacer.CONSTANT_NAME;
import static constant.replacer.ConstantReplacer.CONSTANT_VALUE;
import static constant.replacer.ConstantReplacer.LINE_NUMBER;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;

import org.apache.commons.lang3.Range;

public class ConstantChoiceEditor extends DefaultCellEditor {

    private static final long serialVersionUID = 1L;

    private final ConstantReplacer constantReplacer;
    private final ConstantReplacerDiffPanel constantReplacerPanel;
    private String constantName;
    private Map<Integer, String> contantNamesByRow = new HashMap<>();
    private File file;

    public ConstantChoiceEditor(ConstantReplacer constantReplacer, ConstantReplacerDiffPanel constantReplacerPanel, File file) {
        super(new JComboBox<>());
        this.constantReplacer = constantReplacer;
        this.constantReplacerPanel = constantReplacerPanel;
        this.file = file;
    }

    @Override
    public Object getCellEditorValue() {
        return constantName;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        if (value instanceof String) {
            constantName = (String) value;
            contantNamesByRow.put(row, constantName);
        }
        JComboBox<String> constantNameChoices = new JComboBox<>(constantReplacer.getConstantCandidates(file).get(row).getCandidates().stream().toArray(String[]::new));
        String constantValue = constantReplacer.getColumnValue(file, row, CONSTANT_VALUE);
        constantNameChoices.addItem(constantValue);
        constantNameChoices.addItem("");
        constantNameChoices.setSelectedItem(constantName);
        constantNameChoices.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                constantName = (String) constantNameChoices.getSelectedItem();
                contantNamesByRow.put(row, constantName);
                Object[][] rows = constantReplacer.getRowData(file);
                constantReplacerPanel.showReplacements(buildModificationMap(rows));
                Integer lineNumber = constantReplacer.getColumnValue(file, row, LINE_NUMBER);
                Integer columnNumber = constantReplacer.getColumnValue(file, row, COLUMN_NUMBER);
                constantReplacerPanel.setLineNumber(lineNumber);
                constantReplacerPanel.setColumnNumber(columnNumber);
            }
        });
        return constantNameChoices;
    }

    public Map<Range<Integer>, Modification[]> buildModificationMap(Object[][] rows) {
        Map<Range<Integer>, Modification[]> modificationMap = new HashMap<>();
        for (int row = 0; row < rows.length; row++) {
            ConstantCandidates constantCandidates = constantReplacer.getConstantCandidates(file).get(row);
            String constantName = contantNamesByRow.getOrDefault(row, constantReplacer.getColumnValue(file, row, CONSTANT_NAME));
            modificationMap.put(constantCandidates.getRange(), new Modification[] { new Patch(constantName) });
        }
        return modificationMap;
    }

}