package constant.replacer;

import javax.swing.JTable;

public class ConstantTable extends JTable {

    private static final long serialVersionUID = 1L;

    public static final String CONSTANT_NAME = "Constant name";
    public static final String CONSTANT_VALUE = "Constant value";
    public static final String SWITCH_EXPRESSION = "Switch expression";
    public static final String LINE_CONTENT = "Line content";
    public static final String LINE_NUMBER = "Line number";
    public static final String NUMBER_OF_CANDIDATES = "Number of candidates";

    protected static final Object[] COLS = new Object[] { LINE_NUMBER, LINE_CONTENT, SWITCH_EXPRESSION, CONSTANT_VALUE, CONSTANT_NAME, NUMBER_OF_CANDIDATES };

    public ConstantTable(Object[][] rowData) {
        super(rowData, COLS);
    }

    @Override
    public boolean isCellEditable(final int row, final int column) {
        return CONSTANT_NAME.equals(getColumnName(column));
    }

}