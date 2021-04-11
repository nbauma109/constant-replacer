package constant.replacer;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Range;
import org.netbeans.api.diff.DiffController;
import org.netbeans.modules.diff.builtin.visualizer.editable.EditableDiffView;
import org.netbeans.modules.editor.java.JavaKit;

import de.cismet.custom.visualdiff.DiffPanel;

public class ConstantReplacerDiffPanel extends DiffPanel {

    private static final long serialVersionUID = 1L;

    private File file;

    private int lineNumber;
    private int columnNumber;

    public ConstantReplacerDiffPanel(File file) {
        this.file = file;
    }

    public void showReplacements(Map<Range<Integer>, Modification[]> modificationMap) {
        try (FileReader fileReader = new FileReader(file)) {
            String currentSource = IOUtils.toString(fileReader);
            String modifiedSource = StringUtilities.applyModifications(currentSource, modificationMap);
            setLeftAndRight(currentSource, JavaKit.JAVA_MIME_TYPE, "Current Source", modifiedSource, JavaKit.JAVA_MIME_TYPE, "Modified Source");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void showDiff() {
        super.showDiff();
        SwingUtilities.invokeLater(() -> {
            if (view instanceof EditableDiffView) {
                ((JComponent) view.getComponent()).putClientProperty("diff.smartScrollDisabled", Boolean.TRUE);
                if (lineNumber > 1) {
                    ((EditableDiffView) view).setLocation(DiffController.DiffPane.Base, DiffController.LocationType.LineNumber, lineNumber - 1);
                    gotoPosition(lineNumber, columnNumber);
                }
            }
        });
    }

    public void gotoPosition(int lineNumber, int columnNumber) {
        setLineNumber(lineNumber);
        setColumnNumber(columnNumber);
        SwingUtilities.invokeLater(() -> {
            applyActionOnEditor(view.getComponent(), this::gotoPosition);
        });
    }

    public void applyActionOnEditor(Component component, UnaryOperator<JEditorPane> function) {
        if (component instanceof JEditorPane) {
            JEditorPane editor = (JEditorPane) component;
            function.apply(editor);
        } else if (component instanceof Container) {
            Container container = (Container) component;
            Component[] components = container.getComponents();
            if (components != null) {
                for (Component childComponent : components) {
                    applyActionOnEditor(childComponent, function);
                }
            }
        }
    }

    public JEditorPane gotoPosition(JEditorPane editor) {
        int position = ASTParserFactory.getInstance().getPositionAt(lineNumber, columnNumber, editor.getText());
        editor.setCaretPosition(position - lineNumber + 1);
        return editor;
    }

    public void saveFile() {
        try (FileWriter fileWriter = new FileWriter(file)) {
            IOUtils.write(right.getContent(), fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }
}
