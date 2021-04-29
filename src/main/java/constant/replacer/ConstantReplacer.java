package constant.replacer;

import static constant.replacer.ConstantTable.COLS;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.file.SimplePathVisitor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class ConstantReplacer extends SimplePathVisitor {

    private static final String JAVA_EXTENSION = ".java";
    private static final List<String> EXCLUDES = Arrays.asList("", "0");

    private final ProgressMonitor progressMonitor = new ProgressMonitor(null, "Parsing Files ...", "", 0, 100);
    private final Map<File, ImportSet> importsByFile = new HashMap<>();
    private final Map<File, List<Object[]>> rowData = new HashMap<>();
    private final Map<File, List<ConstantCandidates>> allContantCandidates = new HashMap<>();
    private final Map<String, Map<Location, ConstantName>> constantsByValue = new HashMap<>();
    private final Map<String, String> switches = new HashMap<>();
    private File sourceFolder;
    private BiFunction<File, Long, Void> function;
    private double totalToParse = 0;
    private double totalParsed = 0;
    private ParserWorker parserWorker;

    public void clear() {
        importsByFile.clear();
        rowData.clear();
        allContantCandidates.clear();
        constantsByValue.clear();
    }

    public Void collectTotalFileLength(final File file, final Long fileLength) {
        if (file.getName().endsWith(JAVA_EXTENSION)) {
            totalToParse += fileLength;
        }
        return null;
    }

    public Void collectDeclarations(final File file, final Long fileLength) {
        if (file.getName().endsWith(JAVA_EXTENSION)) {
            try (FileReader fileReader = new FileReader(file)) {
                ASTParserFactory.getInstance().newASTParser(new ASTVisitor() {
                    @Override
                    public boolean visit(QualifiedName node) {
                        SwitchCase switchCase = null;
                        if (node.getParent() instanceof SwitchCase) {
                            switchCase = (SwitchCase) node.getParent();
                            if (switchCase.getParent() instanceof SwitchStatement) {
                                SwitchStatement switchStatement = (SwitchStatement) switchCase.getParent();
                                switches.put(switchStatement.getExpression().toString(), node.getQualifier().getFullyQualifiedName());
                            }
                        }
                        return super.visit(node);
                    }

                    @Override
                    public boolean visit(final VariableDeclarationFragment node) {
                        final Expression initializer = node.getInitializer();
                        if (initializer != null) {
                            final int startPosition = initializer.getStartPosition();
                            final int endPosition = startPosition + initializer.getLength();
                            final IVariableBinding binding = node.resolveBinding();
                            if (binding != null) {
                                final int flags = binding.getModifiers();
                                if (Modifier.isStatic(flags) && Modifier.isFinal(flags)) {
                                    final Object constantValue = binding.getConstantValue();
                                    if (constantValue != null) {
                                        final ITypeBinding declaringClass = binding.getDeclaringClass();
                                        if (declaringClass != null) {
                                            final IPackageBinding packageBinding = declaringClass.getPackage();
                                            if (packageBinding != null) {
                                                final String packageName = packageBinding.getName();
                                                final String fullyQualifiedName = declaringClass.getQualifiedName();
                                                if (fullyQualifiedName.startsWith(packageName)) {
                                                    final String qualifier = fullyQualifiedName.substring(packageName.length() + 1);
                                                    final String constantName = node.getName().getIdentifier();
                                                    final String constantQualifiedName = String.join(".", qualifier, constantName);
                                                    final StringBuilder sb = new StringBuilder();
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
                                                    final String constantValueAsString = sb.toString();
                                                    constantsByValue.putIfAbsent(constantValueAsString, new HashMap<>());
                                                    final Location location = new Location(file, startPosition, endPosition);
                                                    constantsByValue.get(constantValueAsString).put(location, new ConstantName(constantQualifiedName, qualifier, packageName));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return true;
                    }
                }, file, sourceFolder);
            } catch (final IOException e) {
                e.printStackTrace();
            }
            updateProgress(fileLength);

        }
        return null;
    }

    public Void collectReferences(final File file, final Long fileLength) {
        if (file.getName().endsWith(JAVA_EXTENSION)) {
            try {
                List<String> lines;
                try (FileReader fileReader = new FileReader(file)) {
                    lines = IOUtils.readLines(fileReader);
                }
                try (FileReader fileReader = new FileReader(file)) {
                    ASTParserFactory.getInstance().newASTParser(new ASTVisitor() {

                        private CompilationUnit compilationUnit;
                        private int startPosition = Integer.MAX_VALUE;
                        private int endPosition = Integer.MIN_VALUE;
                        private Map<ImportKey, Import> imports = new LinkedHashMap<>();

                        @Override
                        public boolean visit(PackageDeclaration node) {
                            startPosition = Math.min(startPosition, node.getStartPosition() + node.getLength());
                            endPosition = Math.max(endPosition, node.getStartPosition() + node.getLength() + System.lineSeparator().length());
                            return true;
                        }

                        @Override
                        public boolean visit(ImportDeclaration node) {
                            if (node.getName() instanceof QualifiedName) {
                                QualifiedName qualifiedName = (QualifiedName) node.getName();
                                SimpleName simpleName = qualifiedName.getName();
                                startPosition = Math.min(startPosition, node.getStartPosition());
                                endPosition = Math.max(endPosition, node.getStartPosition() + node.getLength());
                                imports.put(new ImportKey(simpleName), new Import(qualifiedName));
                            }
                            return super.visit(node);
                        }

                        @Override
                        public boolean visit(final CompilationUnit node) {
                            compilationUnit = node;
                            return true;
                        }

                        @Override
                        public void endVisit(CompilationUnit node) {
                            ImportSet importSet = new ImportSet();
                            importSet.setRange(Range.between(startPosition, endPosition));
                            importSet.setImports(imports);
                            importSet.setAdditionalLine(imports.isEmpty());
                            importsByFile.put(file, importSet);
                        }

                        @Override
                        public boolean visit(final NumberLiteral node) {
                            Object constantExpressionValue = node.resolveConstantExpressionValue();
                            final String constantValueAsString = String.valueOf(constantExpressionValue);
                            final int startPosition = node.getStartPosition();
                            final int endPosition = startPosition + node.getLength();
                            if (node.getParent() instanceof PrefixExpression) {
                                final PrefixExpression exp = (PrefixExpression) node.getParent();
                                if (PrefixExpression.Operator.MINUS.equals(exp.getOperator())) {
                                    return visitLiteral(file, lines, exp.getStartPosition(), endPosition, String.valueOf(exp));
                                }
                            }
                            if ("1".equals(constantValueAsString) && node.getParent() instanceof InfixExpression) {
                                final InfixExpression exp = (InfixExpression) node.getParent();
                                if (InfixExpression.Operator.PLUS.equals(exp.getOperator())) {
                                    return true;
                                }
                                if (InfixExpression.Operator.MINUS.equals(exp.getOperator())) {
                                    return true;
                                }
                            }
                            if ("2".equals(constantValueAsString) && node.getParent() instanceof Assignment) {
                                final Assignment exp = (Assignment) node.getParent();
                                if (Assignment.Operator.PLUS_ASSIGN.equals(exp.getOperator())) {
                                    return true;
                                }
                                if (Assignment.Operator.MINUS_ASSIGN.equals(exp.getOperator())) {
                                    return true;
                                }
                            }
                            if (EXCLUDES.contains(constantValueAsString) && !(node.getParent() instanceof SwitchCase)) {
                                return true;
                            }
                            if (node.getParent() instanceof SwitchCase) {
                                SwitchCase switchCase = (SwitchCase) node.getParent();
                                if (switchCase.getParent() instanceof SwitchStatement) {
                                    SwitchStatement switchStatement = (SwitchStatement) switchCase.getParent();
                                    return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString, switchStatement);
                                }
                            }
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString);
                        }

                        @Override
                        public boolean visit(final StringLiteral node) {
                            final int startPosition = node.getStartPosition();
                            final int endPosition = startPosition + node.getLength();
                            final String constantValueAsString = node.getEscapedValue();
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString);
                        }

                        @Override
                        public boolean visit(final CharacterLiteral node) {
                            final int startPosition = node.getStartPosition();
                            final int endPosition = startPosition + node.getLength();
                            final String constantValueAsString = node.getEscapedValue();
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString);
                        }

                        public boolean visitLiteral(File file, List<String> lines, int startPosition, int endPosition, String constantValueAsString) {
                            return visitLiteral(file, lines, startPosition, endPosition, constantValueAsString, null);
                        }

                        public boolean visitLiteral(File file, List<String> lines, int startPosition, int endPosition, String constantValueAsString,
                                SwitchStatement switchStatement) {
                            final int lineNumber = compilationUnit.getLineNumber(startPosition);
                            final String line = lines.get(lineNumber - 1);
                            final Map<Location, ConstantName> declarations = constantsByValue.get(constantValueAsString);
                            if (declarations != null) {
                                final Location refLocation = new Location(file, startPosition, endPosition);
                                if (!declarations.containsKey(refLocation)) {
                                    final TreeSet<ConstantName> constantCandidates = new TreeSet<>(declarations.values());
                                    if (!constantCandidates.isEmpty()) {
                                        String switchExpression = switchStatement == null ? "" : switchStatement.getExpression().toString();
                                        String preSelection = switches.get(switchExpression);
                                        rowData.putIfAbsent(file, new ArrayList<>());
                                        final Object[] tableRow = new Object[COLS.length];
                                        setColumnValue(tableRow, ConstantTable.LINE_NUMBER, lineNumber);
                                        setColumnValue(tableRow, ConstantTable.LINE_CONTENT, line.trim());
                                        setColumnValue(tableRow, ConstantTable.CONSTANT_VALUE, constantValueAsString);
                                        setColumnValue(tableRow, ConstantTable.CONSTANT_NAME, preSelect(constantValueAsString, constantCandidates, preSelection));
                                        setColumnValue(tableRow, ConstantTable.NUMBER_OF_CANDIDATES, constantCandidates.size());
                                        setColumnValue(tableRow, ConstantTable.SWITCH_EXPRESSION, switchExpression);
                                        rowData.get(file).add(tableRow);
                                        allContantCandidates.putIfAbsent(file, new ArrayList<>());
                                        Range<Integer> range = Range.between(startPosition, endPosition);
                                        allContantCandidates.get(file).add(new ConstantCandidates(constantValueAsString, range, constantCandidates));
                                    }
                                }
                            }
                            return true;
                        }
                    }, file, sourceFolder);

                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
            updateProgress(fileLength);
        }
        return null;
    }

    public SortedSet<ConstantName> getConstantNamesForValue(String constantValue) {
        final TreeSet<ConstantName> constantCandidates = new TreeSet<>();
        final Map<Location, ConstantName> declarations = constantsByValue.get(constantValue);
        if (declarations != null) {
            constantCandidates.addAll(declarations.values());
        }
        return constantCandidates;
    }

    public void updateProgress(final Long fileLength) {
        totalParsed += fileLength;
        parserWorker.updateProgress((int) Math.round(100 * totalParsed / (5 * totalToParse)));
    }

    public static void setColumnWidth(final JTable table, final String columnName, final int minWidth, final int preferredWidth, final int maxWidth) {
        final TableColumn tableColumn = table.getColumn(columnName);
        tableColumn.setMinWidth(minWidth);
        tableColumn.setPreferredWidth(preferredWidth);
        tableColumn.setMaxWidth(maxWidth);
    }

    public static List<ConstantName> findConstantsByQualifier(ListModel<?> listModel, String qualifier) {
        List<ConstantName> constantNames = new ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            ConstantName constantName = (ConstantName) listModel.getElementAt(i);
            if (constantName.getQualifier().equals(qualifier)) {
                constantNames.add(constantName);
            }
        }
        return constantNames;
    }

    public void createGUI() {

        final JTabbedPane tabbedPane = new JTabbedPane();

        for (final Entry<File, List<Object[]>> entry : getRowData().entrySet()) {
            final File file = entry.getKey();
            final List<Object[]> fileRows = entry.getValue();
            final Object[][] rows = fileRows.toArray(new Object[fileRows.size()][ConstantTable.COLS.length]);
            final ConstantTable table = new ConstantTable(rows);
            table.setRowHeight(25);
            setColumnWidth(table, ConstantTable.LINE_NUMBER, 80, 100, 120);
            setColumnWidth(table, ConstantTable.CONSTANT_VALUE, 100, 150, 200);
            setColumnWidth(table, ConstantTable.NUMBER_OF_CANDIDATES, 120, 130, 150);

            final ConstantReplacerDiffPanel diffPanel = new ConstantReplacerDiffPanel(file);
            diffPanel.showReplacements(buildModificationMap(file, false, rows));
            table.getModel().addTableModelListener(new TableModelListener() {

                private boolean adjusting;

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
                                        List<ConstantName> constantNames = findConstantsByQualifier(choices.getModel(), constantName.getQualifier());
                                        Object closestMatch = StringUtilities.findClosestMatch(constantNames, constantName);
                                        table.setValueAt(closestMatch, i, col);
                                    }
                                }
                            }
                        }
                        diffPanel.showReplacements(buildModificationMap(file, false, rows));
                        adjusting = false;
                    }
                }
            });

            final ConstantChoiceEditor constantChoiceEditor = new ConstantChoiceEditor(this, file);
            table.getColumn(ConstantTable.CONSTANT_NAME).setCellEditor(constantChoiceEditor);

            final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.add(new JScrollPane(table));
            splitPane.add(diffPanel);

            table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
                if (listSelectionEvent.getValueIsAdjusting()) {
                    return;
                }
                final ListSelectionModel lsm = (ListSelectionModel) listSelectionEvent.getSource();
                if (!lsm.isSelectionEmpty()) {
                    final int selectedRow = lsm.getMinSelectionIndex();
                    final Integer lineNumber = getColumnValue(rows[selectedRow], ConstantTable.LINE_NUMBER);
                    diffPanel.gotoLineNumber(lineNumber);
                }
            });

            tabbedPane.addTab(file.getName().replace(JAVA_EXTENSION, ""), null, splitPane, file.getAbsolutePath());
        }

        final JMenuBar menuBar = new JMenuBar();
        final JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem(new AbstractAction("Save & Close Tab") {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent e) {
                Component selectedComponent = tabbedPane.getSelectedComponent();
                final JSplitPane splitPane = (JSplitPane) selectedComponent;
                final ConstantReplacerDiffPanel diffPanel = (ConstantReplacerDiffPanel) splitPane.getRightComponent();
                diffPanel.saveFile();
                tabbedPane.remove(selectedComponent);
            }
        }));
        fileMenu.add(new JMenuItem(new AbstractAction("Close Without Saving") {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent e) {
                tabbedPane.remove(tabbedPane.getSelectedComponent());
            }
        }));
        menuBar.add(fileMenu);

        final JFrame frame = new JFrame("Constant Replacer");
        frame.setJMenuBar(menuBar);
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        frame.getContentPane().add(tabbedPane);
        frame.setVisible(true);
    }

    public Void applyAutomaticReplacements(final File file, final Long fileLength) {
        if (file.getName().endsWith(JAVA_EXTENSION)) {
            List<ConstantCandidates> constantCandidates = allContantCandidates.get(file);
            if (constantCandidates != null) {
                final Object[][] rows = getRowData(file);
                Map<Range<Integer>, Modification[]> modificationMap = buildModificationMap(file, true, rows);
                if (!modificationMap.isEmpty()) {
                    try {
                        FileUtilities.applyModifications(file, modificationMap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            updateProgress(fileLength);
        }
        return null;
    }

    public Map<Range<Integer>, Modification[]> buildModificationMap(final File file, boolean auto, Object[][] rows) {
        final Map<Range<Integer>, Modification[]> modificationMap = new HashMap<>();
        ImportSet importSet = getImports(file);
        for (int row = 0; row < rows.length; row++) {
            final ConstantCandidates constantCandidates = getConstantCandidates(file).get(row);
            if (!auto || constantCandidates.getCandidates().size() == 1) {
                final ConstantName constantName = getColumnValue(rows[row], ConstantTable.CONSTANT_NAME);
                if (constantName.needsImport()) {
                    importSet.addImport(new Import(constantName.getImport(), constantName.getQualifier()));
                }
                modificationMap.put(constantCandidates.getRange(), new Modification[] { new Patch(constantName.getName()) });
            }
        }
        if (!modificationMap.isEmpty()) {
            Patch importPatch = new Patch(importSet.toString());
            modificationMap.put(importSet.getRange(), new Modification[] { importPatch });
        }
        return modificationMap;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
        function.apply(file.toFile(), attrs.size());
        return super.visitFile(file, attrs);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        exc.printStackTrace();
        return super.visitFileFailed(file, exc);
    }

    public void setFunction(final BiFunction<File, Long, Void> function) {
        this.function = function;
    }

    public ImportSet getImports(File file) {
        return importsByFile.get(file).copy();
    }

    public List<ConstantCandidates> getConstantCandidates(final File file) {
        return allContantCandidates.get(file);
    }

    public Object[][] getRowData(final File file) {
        final List<Object[]> fileRows = rowData.get(file);
        return fileRows.toArray(new Object[fileRows.size()][COLS.length]);
    }

    @SuppressWarnings("unchecked")
    public <T> T getColumnValue(final Object[] tableRow, final String columnName) {
        return (T) tableRow[ArrayUtils.indexOf(COLS, columnName)];
    }

    public void setColumnValue(final Object[] tableRow, final String columnName, Object value) {
        tableRow[ArrayUtils.indexOf(COLS, columnName)] = value;
    }

    public void setColumnValue(JTable table, int row, String columnName, ConstantName item) {
        int col = ArrayUtils.indexOf(COLS, columnName);
        table.setValueAt(item, row, col);
    }

    public Map<File, List<Object[]>> getRowData() {
        return rowData;
    }

    public File getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(final File sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    protected void setParserWorker(final ParserWorker parserWorker) {
        this.parserWorker = parserWorker;
    }

    private void setProgessNote(final String message) {
        progressMonitor.setNote(message);
    }

    private void setProgress(final Integer progress) {
        progressMonitor.setProgress(progress);
    }

    private boolean isCanceled() {
        return parserWorker.isCancelled();
    }

    public Object preSelect(final String constantValueAsString, final SortedSet<ConstantName> constantCandidates, String prefix) {
        if (prefix != null) {
            for (ConstantName constantName : constantCandidates) {
                if (constantName.getQualifier().equals(prefix)) {
                    return constantName;
                }
            }
        }
        return constantCandidates.size() > 3 ? new ConstantName(constantValueAsString) : constantCandidates.first();
    }

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> {
            final JFrame fileChooserFrame = new JFrame("Constant Replacer");
            final JMenuBar menuBar = new JMenuBar();
            fileChooserFrame.setJMenuBar(menuBar);
            final JTextField directory = new JTextField(100);
            menuBar.add(directory);
            menuBar.add(new JButton(new AbstractAction("Choose Directory") {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(final ActionEvent e) {

                    final JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    final int returnVal = fileChooser.showOpenDialog(null);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        final File selectedFile = fileChooser.getSelectedFile();
                        if (selectedFile.isDirectory()) {
                            directory.setText(selectedFile.getAbsolutePath());
                        } else {
                            JOptionPane.showMessageDialog(null, "Not a directory. Program will exit.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }

            }));
            menuBar.add(new JButton(new AbstractAction("Scan Directory") {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(final ActionEvent e) {
                    final File selectedFile = new File(directory.getText());
                    if (selectedFile.isDirectory()) {
                        final ConstantReplacer constantReplacer = new ConstantReplacer();
                        final ParserWorker task = new ParserWorker(constantReplacer, selectedFile);
                        task.addPropertyChangeListener(evt -> {
                            if ("progress".equals(evt.getPropertyName())) {
                                final Integer progress = (Integer) evt.getNewValue();
                                final String message = String.format("Completed %d%%.%n", progress);
                                constantReplacer.setProgessNote(message);
                                constantReplacer.setProgress(progress);
                                if (constantReplacer.isCanceled()) {
                                    task.cancel(true);
                                }
                            }
                        });
                        JOptionPane.showMessageDialog(fileChooserFrame, "Scan will start now");
                        task.execute();

                    } else {
                        JOptionPane.showMessageDialog(null, "Not a directory. Program will exit.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }));

            fileChooserFrame.setLocationRelativeTo(null);
            fileChooserFrame.setSize(600, 100);
            fileChooserFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            fileChooserFrame.setVisible(true);
        });

    }

}
