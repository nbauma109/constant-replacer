package constant.replacer;
import java.io.File;

public class Location {

    private File file;
    private int startPosition, endPosition;

    public Location(File file, int startPosition, int endPosition) {
        this.file = file;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + endPosition;
        result = prime * result + ((file == null) ? 0 : file.hashCode());
        result = prime * result + startPosition;
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
        Location other = (Location) obj;
        if (endPosition != other.endPosition)
            return false;
        if (file == null) {
            if (other.file != null)
                return false;
        } else if (!file.equals(other.file))
            return false;
        if (startPosition != other.startPosition)
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new  StringBuilder();
        sb.append(file.getName());
        sb.append(":");
        sb.append(startPosition);
        sb.append("-");
        sb.append(endPosition);
        return sb.toString();
    }

}
