package constant.replacer;


import java.awt.Component;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;

import org.apache.commons.lang3.CharUtils;

public class ConstantChoiceEditor extends DefaultCellEditor {

    private static final long serialVersionUID = 1L;

    private transient List<JComboBox<ConstantName>> constantNameChoices;

    public ConstantChoiceEditor(final ConstantReplacer constantReplacer, final File file) {
        super(new JComboBox<>());
        this.constantNameChoices = new ArrayList<>();
        ActionListener al = event -> stopCellEditing(); 
        List<ConstantCandidates> constantCandidates = constantReplacer.getConstantCandidates(file);
        for (ConstantCandidates constantCandidate : constantCandidates) {
            Set<ConstantName> candidates = constantCandidate.getCandidates();
            String constantValue = constantCandidate.getConstantValue();
            if (constantValue.matches("\\d+")) {
                int integerConstant = Integer.parseInt(constantValue);
                if (integerConstant >= 0 && integerConstant <= Character.MAX_VALUE) {
                    char c = (char) integerConstant;
                    if (CharUtils.isAsciiPrintable(c)) {
                        String charConstantAsString = "'" + c + "'";
                        candidates.add(new ConstantName(charConstantAsString));
                        candidates.addAll(constantReplacer.getConstantNamesForValue(charConstantAsString));
                    }
                }
            }
            candidates.add(new ConstantName(constantValue));
            candidates.add(new ConstantName(""));
            JComboBox<ConstantName> comboBox = new JComboBox<>(candidates.stream().toArray(ConstantName[]::new));
            comboBox.addActionListener(al);
            constantNameChoices.add(comboBox);
        }
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
        return getConstantNameChoices(row);
    }

    public JComboBox<ConstantName> getConstantNameChoices(final int row) {
        return constantNameChoices.get(row);
    }


}