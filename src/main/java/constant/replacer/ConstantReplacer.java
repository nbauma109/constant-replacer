package constant.replacer;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.function.BiFunction;

import javax.swing.AbstractAction;
import javax.swing.JButton;
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
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
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

    private static final String JAVA_EXTENSION = ".java";
    private static final List<String> EXCLUDES = Arrays.asList("", "0");
    public static final String CONSTANT_NAME = "Constant name";
    public static final String CONSTANT_VALUE = "Constant value";
    public static final String LINE_CONTENT = "Line content";
    public static final String LINE_NUMBER = "Line number";
    public static final String COLUMN_NUMBER = "Column number";
    public static final String START_POSITION = "Start Position";
    public static final String NUMBER_OF_CANDIDATES = "Number of candidates";

    private static final Object[] COLS = new Object[] { LINE_NUMBER, COLUMN_NUMBER, START_POSITION, LINE_CONTENT, CONSTANT_VALUE, CONSTANT_NAME, NUMBER_OF_CANDIDATES };
    private final ProgressMonitor progressMonitor = new ProgressMonitor(null, "Parsing Files ...", "", 0, 100);
    private final Map<File, List<Object[]>> rowData = new HashMap<>();
    private final Map<File, List<ConstantCandidates>> allContantCandidates = new HashMap<>();
    private final Map<String, Map<Location, String>> constantsByValue = new HashMap<>();
    private File sourceFolder;
    private BiFunction<File, Long, Void> function;
    private double totalToParse = 0;
    private double totalParsed = 0;
    private ParserWorker parserWorker;

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
            } catch (final IOException e) {
                e.printStackTrace();
            }
            updateProgress(file, fileLength);

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

                        @Override
                        public boolean visit(final CompilationUnit node) {
                            compilationUnit = node;
                            return true;
                        }

                        @Override
                        public boolean visit(final NumberLiteral node) {
                            final int startPosition = node.getStartPosition();
                            final int endPosition = startPosition + node.getLength();
                            if (node.getParent() instanceof PrefixExpression) {
                                final PrefixExpression exp = (PrefixExpression) node.getParent();
                                if (PrefixExpression.Operator.MINUS.equals(exp.getOperator())) {
                                    return visitLiteral(file, lines, exp.getStartPosition(), endPosition, String.valueOf(exp));
                                }
                            }
                            if (node.getParent() instanceof InfixExpression) {
                                final InfixExpression exp = (InfixExpression) node.getParent();
                                if (InfixExpression.Operator.PLUS.equals(exp.getOperator())) {
                                    return true;
                                }
                                if (InfixExpression.Operator.MINUS.equals(exp.getOperator())) {
                                    return true;
                                }
                            }
                            if (node.getParent() instanceof Assignment) {
                                final Assignment exp = (Assignment) node.getParent();
                                if (Assignment.Operator.PLUS_ASSIGN.equals(exp.getOperator())) {
                                    return true;
                                }
                                if (Assignment.Operator.MINUS_ASSIGN.equals(exp.getOperator())) {
                                    return true;
                                }
                            }
                            final String constantValueAsString = String.valueOf(node);
                            if (EXCLUDES.contains(constantValueAsString) && !(node.getParent() instanceof SwitchCase)) {
                                return true;
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

                        public boolean visitLiteral(final File file, final List<String> lines, final int startPosition, final int endPosition, final String constantValueAsString) {
                            final Map<Location, String> declarations = constantsByValue.get(constantValueAsString);
                            if (declarations != null) {
                                final Location refLocation = new Location(file, startPosition, endPosition);
                                if (!declarations.containsKey(refLocation)) {
                                    final TreeSet<String> constantCandidates = new TreeSet<>(declarations.values());
                                    if (!constantCandidates.isEmpty()) {
                                        final int lineNumber = compilationUnit.getLineNumber(startPosition);
                                        final int columnNumber = compilationUnit.getColumnNumber(startPosition);
                                        final String line = lines.get(lineNumber - 1);
                                        rowData.putIfAbsent(file, new ArrayList<>());
                                        final Object[] tableRow = new Object[COLS.length];
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
            } catch (final Exception e) {
                e.printStackTrace();
            }
            updateProgress(file, fileLength);
        }
        return null;
    }

    public void updateProgress(final File file, final Long fileLength) {
        totalParsed += fileLength;
        parserWorker.updateProgress((int) Math.round(100 * totalParsed / (2 * totalToParse)));
    }

    public static void setColumnWidth(final JTable table, final String columnName, final int minWidth, final int preferredWidth, final int maxWidth) {
        final TableColumn tableColumn = table.getColumn(columnName);
        tableColumn.setMinWidth(minWidth);
        tableColumn.setPreferredWidth(preferredWidth);
        tableColumn.setMaxWidth(maxWidth);
    }

    public void createGUI() {

        final JTabbedPane tabbedPane = new JTabbedPane();

        for (final Entry<File, List<Object[]>> entry : getRowData().entrySet()) {
            final File file = entry.getKey();
            final List<Object[]> fileRows = entry.getValue();
            final Object[][] rows = fileRows.toArray(new Object[fileRows.size()][COLS.length]);
            final JTable table = new JTable(rows, COLS) {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(final int row, final int column) {
                    return CONSTANT_NAME.equals(getColumnName(column));
                }
            };
            table.setRowHeight(25);
            setColumnWidth(table, LINE_NUMBER, 80, 100, 120);
            setColumnWidth(table, COLUMN_NUMBER, 80, 100, 120);
            setColumnWidth(table, START_POSITION, 80, 100, 120);
            setColumnWidth(table, CONSTANT_VALUE, 100, 150, 200);
            setColumnWidth(table, NUMBER_OF_CANDIDATES, 120, 130, 150);

            final Map<Range<Integer>, Modification[]> modificationMap = buildModificationMap(file);
            final ConstantReplacerDiffPanel diffPanel = new ConstantReplacerDiffPanel(file);
            diffPanel.showReplacements(modificationMap);

            final ConstantChoiceEditor constantChoiceEditor = new ConstantChoiceEditor(this, diffPanel, file);
            table.getColumn(CONSTANT_NAME).setCellEditor(constantChoiceEditor);

            final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setDividerLocation(0.5);
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
                    final Integer lineNumber = getColumnValue(file, selectedRow, LINE_NUMBER);
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
                final JSplitPane splitPane = (JSplitPane) tabbedPane.getSelectedComponent();
                final ConstantReplacerDiffPanel diffPanel = (ConstantReplacerDiffPanel) splitPane.getRightComponent();
                diffPanel.saveFile();
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

    public Map<Range<Integer>, Modification[]> buildModificationMap(final File file) {
        final Map<Range<Integer>, Modification[]> modificationMap = new HashMap<>();
        final Object[][] rows = getRowData(file);
        for (int row = 0; row < rows.length; row++) {
            final ConstantCandidates constantCandidates = getConstantCandidates(file).get(row);
            final String constantName = getColumnValue(file, row, CONSTANT_NAME);
            modificationMap.put(constantCandidates.getRange(), new Modification[] { new Patch(constantName) });
        }
        return modificationMap;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
        function.apply(file.toFile(), attrs.size());
        return super.visitFile(file, attrs);
    }

    public void setFunction(final BiFunction<File, Long, Void> function) {
        this.function = function;
    }

    public List<ConstantCandidates> getConstantCandidates(final File file) {
        return allContantCandidates.get(file);
    }

    public Object[][] getRowData(final File file) {
        final List<Object[]> fileRows = rowData.get(file);
        return fileRows.toArray(new Object[fileRows.size()][COLS.length]);
    }

    @SuppressWarnings("unchecked")
    public <T> T getColumnValue(final File file, final int row, final String columnName) {
        return (T) rowData.get(file).get(row)[ArrayUtils.indexOf(COLS, columnName)];
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
