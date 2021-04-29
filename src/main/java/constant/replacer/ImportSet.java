package constant.replacer;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.Range;

public class ImportSet implements Cloneable {

    private Range<Integer> range;
    private Map<ImportKey, Import> imports;
    private boolean additionalLine = true;

    public void addImport(Import newImport) {
        imports.put(new ImportKey(newImport.getSimpleName()), newImport);
    }

    public Range<Integer> getRange() {
        return range;
    }

    public void setRange(Range<Integer> range) {
        this.range = range;
    }

    public Map<ImportKey, Import> getImports() {
        return imports;
    }

    public void setImports(Map<ImportKey, Import> imports) {
        this.imports = imports;
    }

    public void setAdditionalLine(boolean additionalLine) {
        this.additionalLine = additionalLine;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        for (Import imp : imports.values()) {
            sb.append(System.lineSeparator());
            sb.append(imp);
        }
        if (additionalLine) {
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    public ImportSet copy() {
        ImportSet importSet = new ImportSet();
        importSet.setAdditionalLine(additionalLine);
        importSet.setImports(new LinkedHashMap<>(imports));
        importSet.setRange(range);
        return importSet;
    }
}
