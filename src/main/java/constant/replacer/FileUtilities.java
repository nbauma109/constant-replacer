package constant.replacer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

public class FileUtilities {

    public static final Charset UTF8 = Charset.forName("UTF-8");

    public static String readFileToString(File file, Charset charSet) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), charSet);
    }

    public static void writeStringToFile(final File file, String modifiedFileContents, final Charset charset) throws IOException {
        Files.write(file.toPath(), modifiedFileContents.getBytes(charset));
    }

    public static List<String> readLines(final File file, Charset charset) throws IOException {
        return Files.readAllLines(file.toPath(), charset);
    }

    public static String getLine(File file, long lineNumber, Charset charset) throws IOException {
        return Files.readAllLines(file.toPath(), charset).get((int) (lineNumber - 1));
    }

    public static String getLine(File file, int sourceLineNumber) throws IOException {
        return getLine(file, sourceLineNumber, UTF8);
    }
}
