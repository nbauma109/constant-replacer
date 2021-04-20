package constant.replacer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class FileUtilities {

    private FileUtilities() {
        super();
    }

    public static String readFileToString(final File file, final Charset charSet) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), charSet);
    }

    public static void writeStringToFile(final File file, final String modifiedFileContents, final Charset charset) throws IOException {
        Files.write(file.toPath(), modifiedFileContents.getBytes(charset));
    }

    public static List<String> readLines(final File file, final Charset charset) throws IOException {
        return Files.readAllLines(file.toPath(), charset);
    }

    public static String getLine(final File file, final long lineNumber, final Charset charset) throws IOException {
        return Files.readAllLines(file.toPath(), charset).get((int) (lineNumber - 1));
    }

    public static String getLine(final File file, final int sourceLineNumber) throws IOException {
        return getLine(file, sourceLineNumber, StandardCharsets.UTF_8);
    }
}
