package constant.replacer;

public class Modification {

	private String searchString, replacementString;
	private boolean regex, all;

	public Modification(String searchString, String replacementString, boolean regex, boolean all) {
		this.searchString = searchString;
		this.replacementString = replacementString;
		this.regex = regex;
		this.all = all;
		if (searchString != null && searchString.matches("\\w+")) {
			this.regex = true;
			this.searchString = "\\b" + searchString + "\\b";
		}
	}

	public String getSearchString() {
		return searchString;
	}

	public void setSearchString(String searchString) {
		this.searchString = searchString;
	}

	public String getReplacementString() {
		return replacementString;
	}

	public void setReplacementString(String replacementString) {
		this.replacementString = replacementString;
	}

	public boolean isRegex() {
		return regex;
	}

	public void setRegex(boolean regex) {
		this.regex = regex;
	}

	public boolean isAll() {
		return all;
	}

	public void setAll(boolean all) {
		this.all = all;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (all ? 1231 : 1237);
		result = prime * result + (regex ? 1231 : 1237);
		result = prime * result + ((replacementString == null) ? 0 : replacementString.hashCode());
		result = prime * result + ((searchString == null) ? 0 : searchString.hashCode());
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
		Modification other = (Modification) obj;
		if (all != other.all)
			return false;
		if (regex != other.regex)
			return false;
		if (replacementString == null) {
			if (other.replacementString != null)
				return false;
		} else if (!replacementString.equals(other.replacementString))
			return false;
		if (searchString == null) {
			if (other.searchString != null)
				return false;
		} else if (!searchString.equals(other.searchString))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Modification [searchString=" + searchString + ", replacementString=" + replacementString + ", regex=" + regex + ", all=" + all + "]";
	}

}
