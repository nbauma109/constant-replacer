package constant.replacer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;

public class ASTParserFactory {

    public static ASTParserFactory getInstance() {
        return ASTParserFactoryHolder.INSTANCE;
    }

    private static class ASTParserFactoryHolder {
        private static final ASTParserFactory INSTANCE = new ASTParserFactory();
    }

    public ASTParser newASTParser(final ASTVisitor listener, final File javaFile, final File sourceFolder) throws IOException {
        final ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        try (FileReader fileReader = new FileReader(javaFile)) {
            parser.setSource(IOUtils.toCharArray(fileReader));
        }
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        final String[] classPath = System.getProperty("java.class.path").split(File.pathSeparator);
        final String[] sourcepathEntries = new String[] { sourceFolder.getAbsolutePath() };
        final String[] encodings = new String[] { StandardCharsets.UTF_8.name() };
        parser.setEnvironment(classPath, sourcepathEntries, encodings, true);
        parser.setUnitName(javaFile.getName());
        final Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.CORE_ENCODING, StandardCharsets.UTF_8.name());
        parser.setCompilerOptions(options);
        parser.createAST(null).accept(listener);
        return parser;
    }

}
