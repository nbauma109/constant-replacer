package constant.replacer;

import java.util.Set;

import org.apache.commons.lang3.Range;

public class ConstantCandidates {

    private String constantValue;
    private Range<Integer> range;
    private Set<ConstantName> candidates;

    public ConstantCandidates(String constantValue, Range<Integer> range, Set<ConstantName> candidates) {
        this.constantValue = constantValue;
        this.range = range;
        this.candidates = candidates;
    }

    public String getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(String constantValue) {
        this.constantValue = constantValue;
    }

    public Range<Integer> getRange() {
        return range;
    }

    public void setRange(Range<Integer> range) {
        this.range = range;
    }

    public Set<ConstantName> getCandidates() {
        return candidates;
    }

    public void setCandidates(Set<ConstantName> candidates) {
        this.candidates = candidates;
    }
}
