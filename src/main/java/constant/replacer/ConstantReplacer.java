package constant.replacer;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.file.SimplePathVisitor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Range;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class ConstantReplacer extends SimplePathVisitor {

    private static final List<String> EXCLUDES = Arrays.asList("", "0");
    public static final String CONSTANT_NAME = "Constant name";
    public static final String CONSTANT_VALUE = "Constant value";
    public static final String LINE_CONTENT = "Line content";
    public static final String LINE_NUMBER = "Line number";
    public static final String COLUMN_NUMBER = "Column number";
    public static final String START_POSITION = "Start Position";
    public static final String NUMBER_OF_CANDIDATES = "Number of candidates";

    public static final Object[] COLS = new Object[] { LINE_NUMBER, COLUMN_NUMBER, START_POSITION, LINE_CONTENT, CONSTANT_VALUE, CONSTANT_NAME, NUMBER_OF_CANDIDATES };
    private Map<File, List<Object[]>> rowData = new HashMap<>();
    private Map<File, List<ConstantCandidates>> allContantCandidates = new HashMap<>();
    private Map<String, Map<Location, String>> constantsByValue = new HashMap<>();
    private File sourceFolder;
    private Function<File, Void> function;

    public Void collectDeclarations(File file) {
        if (file.getName().endsWith(".java")) {
            try (FileReader fileReader = new FileReader(file)) {
                try {
                    ASTParserFactory.getInstance().newASTParser(new ASTVisitor() {

                        @Override
                        public boolean visit(VariableDeclarationFragment node) {
                            Expression initializer = node.getInitializer();
                            if (initializer != null) {
                                int startPosition = initializer.getStartPosition();
                                int endPosition = startPosition + initializer.getLength();
                                IVariableBinding binding = node.resolveBinding();
                                if (binding != null) {
                                    int flags = binding.getModifiers();
                                    if (Modifier.isStatic(flags) && Modifier.isFinal(flags)) {
                                        Object constantValue = binding.getConstantValue();
                                        if (constantValue != null) {
                                            ITypeBinding declaringClass = binding.getDeclaringClass();
                                            if (declaringClass != null) {
                                                IPackageBinding packageBinding = declaringClass.getPackage();
                                                if (packageBinding != null) {
                                                    String packageName = packageBinding.getName();
                                                    String fullyQualifiedName = declaringClass.getQualifiedName();
                                                    if (fullyQualifiedName.startsWith(packageName)) {
                                                        String qualifier = fullyQualifiedName.substring(packageName.length() + 1);
                                                        String constantName = node.getName().getIdentifier();
                                                        String constantQualifiedName = String.join(".", qualifier, constantName);
                                                        StringBuilder sb = new StringBuilder();
                                                        if (constantValue instanceof String) {
                                                            sb.append('"');
                                                            sb.append(constantValue);
                                                            sb.append('"');
                                                        } else if (constantValue instanceof Character) {
                                                            sb.append("'");
                                                            sb.append(constantValue);
                                                            sb.append("'");
                                                        } else {
                                                            sb.append(constantValue);
                                                        }
                                                        String constantValueAsString = sb.toString();
                                                        constantsByValue.putIfAbsent(constantValueAsString, new HashMap<>());
                                                        Location location = new Location(file, startPosition, endPosition);
                                                        constantsByValue.get(constantValueAsString).put(location, constantQualifiedName);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            return super.visit(node);
                        }
                    }, file, sourceFolder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Void collectReferences(File file) {
        if (file.getName().endsWith(".java")) {
            try {
                List<String> lines;
                try (FileReader fileReader = new FileReader(file)) {
                    lines = IOUtils.readLines(fileReader);
                }
                try (FileReader fileReader = new FileReader(file)) {
                    ASTParserFactory.getInstance().newASTParser(new ASTVisitor() {

                        private CompilationUnit compilationUnit;

                        @Override
                        public boolean visit(CompilationUnit node) {
                            compilationUnit = node;
                            return true;
                        }

                        @Override
                        public boolean visit(NumberLiteral node) {
                            int startPosition = node.getStartPosition();
                            int endPosition = startPosition + node.getLength();
                            if (node.getParent() instanceof PrefixExpression) {
                                PrefixExpression exp = (PrefixExpression) node.getParent();
                                if (PrefixExpression.Operator.MINUS.equals(exp.getOperator())) {
                                    return visitLiteral(file, lines, exp.getStartPosition(), endPosition, String.valueOf(exp));
                                }
                            }
                            if (node.getParent() instanceof InfixExpression) {
                                InfixExpression exp = (InfixExpression) node.getParent();
                                if (InfixExpression.Operator.PLUS.equals(exp.getOperator())) {
                                    return true;
                                }
                                if (InfixExpression.Operator.MINUS.equals(exp.getOperator())) {
                                    return true;
                                }
                            }
                            if (node.getParent() instanceof Assignment) {
                                Assignment exp = (Assignment) node.getParent();
                                if (Assignment.Operator.PLUS_ASSIGN.equals(exp.getOperator())) {
                                    return true;
                                }
                                if (Assignment.Operator.MINUS_ASSIGN.equals(exp.getOperator())) {
                                    return true;
                                }
                            }
                            String constantValueAsString = String.valueOf(node);
                            if (EXCLUDES.contains(constantValueAsString) && !(node.getParent() instanceof SwitchCase)) {
                                return true;
                            }
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString);
                        }

                        @Override
                        public boolean visit(StringLiteral node) {
                            int startPosition = node.getStartPosition();
                            int endPosition = startPosition + node.getLength();
                            String constantValueAsString = node.getEscapedValue();
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString);
                        }

                        @Override
                        public boolean visit(CharacterLiteral node) {
                            int startPosition = node.getStartPosition();
                            int endPosition = startPosition + node.getLength();
                            String constantValueAsString = node.getEscapedValue();
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString);
                        }

                        public boolean visitLiteral(File file, List<String> lines, int startPosition, int endPosition, String constantValueAsString) {
                            Map<Location, String> declarations = constantsByValue.get(constantValueAsString);
                            if (declarations != null) {
                                Location refLocation = new Location(file, startPosition, endPosition);
                                if (!declarations.containsKey(refLocation)) {
                                    TreeSet<String> constantCandidates = new TreeSet<>(declarations.values());
                                    if (!constantCandidates.isEmpty()) {
                                        int lineNumber = compilationUnit.getLineNumber(startPosition);
                                        int columnNumber = compilationUnit.getColumnNumber(startPosition);
                                        String line = lines.get(lineNumber - 1);
                                        rowData.putIfAbsent(file, new ArrayList<>());
                                        Object[] tableRow = new Object[COLS.length];
                                        int idx = 0;
                                        tableRow[idx++] = lineNumber;
                                        tableRow[idx++] = columnNumber;
                                        tableRow[idx++] = startPosition;
                                        tableRow[idx++] = line.trim();
                                        tableRow[idx++] = constantValueAsString;
                                        tableRow[idx++] = constantCandidates.first();
                                        tableRow[idx++] = constantCandidates.size();
                                        rowData.get(file).add(tableRow);
                                        allContantCandidates.putIfAbsent(file, new ArrayList<>());
                                        allContantCandidates.get(file).add(new ConstantCandidates(constantCandidates, Range.between(startPosition, endPosition)));
                                    }
                                }
                            }
                            return true;
                        }
                    }, file, sourceFolder);

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void setColumnWidth(JTable table, String columnName, int minWidth, int preferredWidth, int maxWidth) {
        TableColumn tableColumn = table.getColumn(columnName);
        tableColumn.setMinWidth(minWidth);
        tableColumn.setPreferredWidth(preferredWidth);
        tableColumn.setMaxWidth(maxWidth);
    }

    public static void createGUI(ConstantReplacer constantReplacer) throws IOException {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {

                JTabbedPane tabbedPane = new JTabbedPane();

                for (Entry<File, List<Object[]>> entry : constantReplacer.getRowData().entrySet()) {
                    File file = entry.getKey();
                    List<Object[]> fileRows = entry.getValue();
                    Object[][] rows = fileRows.toArray(new Object[fileRows.size()][COLS.length]);
                    JTable table = new JTable(rows, COLS) {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return CONSTANT_NAME.equals(getColumnName(column));
                        }
                    };
                    table.setRowHeight(25);
                    setColumnWidth(table, LINE_NUMBER, 80, 100, 120);
                    setColumnWidth(table, COLUMN_NUMBER, 80, 100, 120);
                    setColumnWidth(table, START_POSITION, 80, 100, 120);
                    setColumnWidth(table, CONSTANT_VALUE, 100, 150, 200);
                    setColumnWidth(table, NUMBER_OF_CANDIDATES, 120, 130, 150);

                    Map<Range<Integer>, Modification[]> modificationMap = constantReplacer.buildModificationMap(file);
                    ConstantReplacerDiffPanel diffPanel = new ConstantReplacerDiffPanel(file);
                    diffPanel.showReplacements(modificationMap);

                    ConstantChoiceEditor constantChoiceEditor = new ConstantChoiceEditor(constantReplacer, diffPanel, file);
                    table.getColumn(CONSTANT_NAME).setCellEditor(constantChoiceEditor);

                    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
                    splitPane.setDividerLocation(0.5);
                    splitPane.add(new JScrollPane(table));
                    splitPane.add(diffPanel);

                    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

                        @Override
                        public void valueChanged(ListSelectionEvent listSelectionEvent) {
                            if (listSelectionEvent.getValueIsAdjusting())
                                return;
                            ListSelectionModel lsm = (ListSelectionModel) listSelectionEvent.getSource();
                            if (!lsm.isSelectionEmpty()) {
                                int selectedRow = lsm.getMinSelectionIndex();
                                Integer lineNumber = constantReplacer.getColumnValue(file, selectedRow, LINE_NUMBER);
                                Integer columnNumber = constantReplacer.getColumnValue(file, selectedRow, COLUMN_NUMBER);
                                diffPanel.gotoPosition(lineNumber, columnNumber);
                            }
                        }

                    });

                    tabbedPane.addTab(file.getName().replace(".java", ""), null, splitPane, file.getAbsolutePath());
                }

                JMenuBar menuBar = new JMenuBar();
                JMenu fileMenu = new JMenu("File");
                fileMenu.add(new JMenuItem(new AbstractAction("Save & Close Tab") {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JSplitPane splitPane = (JSplitPane) tabbedPane.getSelectedComponent();
                        ConstantReplacerDiffPanel diffPanel = (ConstantReplacerDiffPanel) splitPane.getRightComponent();
                        diffPanel.saveFile();
                        tabbedPane.remove(tabbedPane.getSelectedComponent());
                    }
                }));
                menuBar.add(fileMenu);

                JFrame frame = new JFrame("Constant Replacer");
                frame.setJMenuBar(menuBar);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.getContentPane().add(tabbedPane);
                frame.setVisible(true);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            }
        });
    }

    public Map<Range<Integer>, Modification[]> buildModificationMap(File file) {
        Map<Range<Integer>, Modification[]> modificationMap = new HashMap<>();
        Object[][] rows = getRowData(file);
        for (int row = 0; row < rows.length; row++) {
            ConstantCandidates constantCandidates = getConstantCandidates(file).get(row);
            String constantName = getColumnValue(file, row, CONSTANT_NAME);
            modificationMap.put(constantCandidates.getRange(), new Modification[] { new Patch(constantName) });
        }
        return modificationMap;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        function.apply(file.toFile());
        return super.visitFile(file, attrs);
    }

    public void setFunction(Function<File, Void> function) {
        this.function = function;
    }

    public List<ConstantCandidates> getConstantCandidates(File file) {
        return allContantCandidates.get(file);
    }

    public Object[][] getRowData(File file) {
        List<Object[]> fileRows = rowData.get(file);
        return fileRows.toArray(new Object[fileRows.size()][COLS.length]);
    }

    @SuppressWarnings("unchecked")
    public <T> T getColumnValue(File file, int row, String columnName) {
        return (T) rowData.get(file).get(row)[ArrayUtils.indexOf(COLS, columnName)];
    }

    public Map<File, List<Object[]>> getRowData() {
        return rowData;
    }

    public File getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(File sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    public static void main(String[] args) throws IOException {
        ConstantReplacer constantReplacer = new ConstantReplacer();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile.isDirectory()) {
                constantReplacer.setSourceFolder(selectedFile);

                constantReplacer.setFunction(constantReplacer::collectDeclarations);
                Files.walkFileTree(selectedFile.toPath(), constantReplacer);

                constantReplacer.setFunction(constantReplacer::collectReferences);
                Files.walkFileTree(selectedFile.toPath(), constantReplacer);

                createGUI(constantReplacer);
            } else {
                JOptionPane.showMessageDialog(null, "Not a directory. Program will exit.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

    }

}
