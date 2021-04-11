package constant.replacer;

import java.util.Set;

import org.apache.commons.lang3.Range;

public class ConstantCandidates {

    private Set<String> candidates;
    private Range<Integer> range;

    public ConstantCandidates(Set<String> candidates, Range<Integer> range) {
        this.candidates = candidates;
        this.range = range;
    }

    public Range<Integer> getRange() {
        return range;
    }

    public void setRange(Range<Integer> range) {
        this.range = range;
    }

    public Set<String> getCandidates() {
        return candidates;
    }

    public void setCandidates(Set<String> candidates) {
        this.candidates = candidates;
    }
}
