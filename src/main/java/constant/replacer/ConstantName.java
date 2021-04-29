package constant.replacer;

import java.io.Serializable;

public class ConstantName implements Serializable, Comparable<ConstantName> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String qualifier;
    private String packageName;

    public ConstantName(String name, String qualifier, String packageName) {
        this.name = name;
        this.qualifier = qualifier;
        this.packageName = packageName;
    }

    public ConstantName(String value) {
        this(value, "", "");
    }

    public ConstantName() {
        this("");
    }

    @Override
    public int compareTo(ConstantName o) {
        if (name.equals(o.name)) {
            return packageName.compareTo(o.packageName);
        }
        return name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((packageName == null) ? 0 : packageName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConstantName other = (ConstantName) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (packageName == null) {
            if (other.packageName != null)
                return false;
        } else if (!packageName.equals(other.packageName))
            return false;
        return true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getImport() {
        return String.join(".", packageName, qualifier);
    }

    public boolean needsImport() {
        return !packageName.isEmpty();
    }

    @Override
    public String toString() {
        return name;
    }
}
