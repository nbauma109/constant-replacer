package constant.replacer;

import static constant.replacer.ConstantReplacer.CONSTANT_NAME;
import static constant.replacer.ConstantReplacer.CONSTANT_VALUE;
import static constant.replacer.ConstantReplacer.LINE_NUMBER;

import java.awt.Component;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;

import org.apache.commons.lang3.Range;

public class ConstantChoiceEditor extends DefaultCellEditor {

    private static final long serialVersionUID = 1L;

    private final transient ConstantReplacer constantReplacer;
    private final ConstantReplacerDiffPanel constantReplacerPanel;
    private String constantName;
    private final Map<Integer, String> contantNamesByRow = new HashMap<>();
    private final File file;

    public ConstantChoiceEditor(final ConstantReplacer constantReplacer, final ConstantReplacerDiffPanel constantReplacerPanel, final File file) {
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
    public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
        if (value instanceof String) {
            constantName = (String) value;
            contantNamesByRow.put(row, constantName);
        }
        final JComboBox<String> constantNameChoices = new JComboBox<>(constantReplacer.getConstantCandidates(file).get(row).getCandidates().stream().toArray(String[]::new));
        final String constantValue = constantReplacer.getColumnValue(file, row, CONSTANT_VALUE);
        constantNameChoices.addItem(constantValue);
        constantNameChoices.addItem("");
        constantNameChoices.setSelectedItem(constantName);
        constantNameChoices.addActionListener(e -> {
            constantName = (String) constantNameChoices.getSelectedItem();
            contantNamesByRow.put(row, constantName);
            final Object[][] rows = constantReplacer.getRowData(file);
            constantReplacerPanel.showReplacements(buildModificationMap(rows));
            final Integer lineNumber = constantReplacer.getColumnValue(file, row, LINE_NUMBER);
            constantReplacerPanel.setLineNumber(lineNumber);
        });
        return constantNameChoices;
    }

    public Map<Range<Integer>, Modification[]> buildModificationMap(final Object[][] rows) {
        final Map<Range<Integer>, Modification[]> modificationMap = new HashMap<>();
        for (int row = 0; row < rows.length; row++) {
            final ConstantCandidates constantCandidates = constantReplacer.getConstantCandidates(file).get(row);
            final String constantName = contantNamesByRow.getOrDefault(row, constantReplacer.getColumnValue(file, row, CONSTANT_NAME));
            modificationMap.put(constantCandidates.getRange(), new Modification[] { new Patch(constantName) });
        }
        return modificationMap;
    }

}