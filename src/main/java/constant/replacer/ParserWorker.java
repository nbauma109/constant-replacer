package constant.replacer;

import java.io.File;
import java.nio.file.Files;

import javax.swing.SwingWorker;

public class ParserWorker extends SwingWorker<Void, Void> {
    private final ConstantReplacer constantReplacer;
    private final File selectedFile;

    public ParserWorker(final ConstantReplacer constantReplacer, final File selectedFile) {
        this.constantReplacer = constantReplacer;
        this.selectedFile = selectedFile;
    }

    @Override
    protected Void doInBackground() throws Exception {
        constantReplacer.setSourceFolder(selectedFile);
        constantReplacer.setFunction(constantReplacer::collectTotalFileLength);
        Files.walkFileTree(selectedFile.toPath(), constantReplacer);

        constantReplacer.setParserWorker(this);
        constantReplacer.setFunction(constantReplacer::collectDeclarations);
        Files.walkFileTree(selectedFile.toPath(), constantReplacer);

        constantReplacer.setFunction(constantReplacer::collectReferences);
        Files.walkFileTree(selectedFile.toPath(), constantReplacer);
        return null;
    }

    @Override
    protected void done() {
        constantReplacer.createGUI();
    }

    public void updateProgress(final int progress) {
        setProgress(progress);
    }
}