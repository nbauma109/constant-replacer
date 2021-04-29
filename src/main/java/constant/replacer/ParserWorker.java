package constant.replacer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

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
        collectDeclarationsAndReferences();
        constantReplacer.setFunction(constantReplacer::applyAutomaticReplacements);
        Files.walkFileTree(selectedFile.toPath(), constantReplacer);
        constantReplacer.clear();
        collectDeclarationsAndReferences();
        return null;
    }

    public void collectDeclarationsAndReferences() throws IOException {
        constantReplacer.setFunction(constantReplacer::collectDeclarations);
        Files.walkFileTree(selectedFile.toPath(), constantReplacer);

        constantReplacer.setFunction(constantReplacer::collectReferences);
        Files.walkFileTree(selectedFile.toPath(), constantReplacer);
    }

    @Override
    protected void done() {
        try {
            get();
            constantReplacer.createGUI();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    public void updateProgress(final int progress) {
        setProgress(progress);
    }
}