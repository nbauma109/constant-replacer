package constant.replacer;

import org.eclipse.jdt.core.dom.QualifiedName;

public class Import {

    private String qualifiedName;
    private String simpleName;

    public Import(QualifiedName qualifiedName) {
        this(qualifiedName.getFullyQualifiedName(), qualifiedName.getName().getIdentifier());
    }

    public Import(String qualifiedName, String simpleName) {
        this.qualifiedName = qualifiedName;
        this.simpleName = simpleName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("import ");
        sb.append(qualifiedName);
        sb.append(";");
        return sb.toString();
    }
}
