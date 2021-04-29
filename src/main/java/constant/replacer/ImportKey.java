package constant.replacer;

import org.eclipse.jdt.core.dom.SimpleName;

public class ImportKey {

	private String simpleName;

	public ImportKey(SimpleName simpleName) {
		this(simpleName.getIdentifier());
	}

	public ImportKey(String simpleName) {
		this.simpleName = simpleName;
	}

	public String getSimpleName() {
		return simpleName;
	}

	public void setSimpleName(String simpleName) {
		this.simpleName = simpleName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((simpleName == null) ? 0 : simpleName.hashCode());
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
		ImportKey other = (ImportKey) obj;
		if (simpleName == null) {
			if (other.simpleName != null)
				return false;
		} else if (!simpleName.equals(other.simpleName))
			return false;
		return true;
	}

}
