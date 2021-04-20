package constant.replacer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

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

    public ConstantReplacerDiffPanel(final File file) {
        this.file = file;
    }

    public void showReplacements(final Map<Range<Integer>, Modification[]> modificationMap) {
        try (FileReader fileReader = new FileReader(file)) {
            final String currentSource = IOUtils.toString(fileReader);
            final String modifiedSource = StringUtilities.applyModifications(currentSource, modificationMap);
            setLeftAndRight(currentSource, JavaKit.JAVA_MIME_TYPE, "Current Source", modifiedSource, JavaKit.JAVA_MIME_TYPE, "Modified Source");
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void showDiff() {
        super.showDiff();
        SwingUtilities.invokeLater(() -> {
            if (lineNumber > 1) {
                gotoLineNumber(lineNumber);
            }
        });
    }

    public void gotoLineNumber(final int lineNumber) {
        ((EditableDiffView) view).setLocation(DiffController.DiffPane.Base, DiffController.LocationType.LineNumber, lineNumber - 1);
    }

    public void saveFile() {
        try (FileWriter fileWriter = new FileWriter(file)) {
            IOUtils.write(right.getContent(), fileWriter);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public File getFile() {
        return file;
    }

    public void setFile(final File file) {
        this.file = file;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(final int lineNumber) {
        this.lineNumber = lineNumber;
    }
}
