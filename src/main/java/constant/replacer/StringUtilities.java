package constant.replacer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;

public class StringUtilities {

    private StringUtilities() {
        super();
    }

    public static String replaceAll(final String text, final String regex, final String replacement, final int fromIdx, final int toIdx) {
        final StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, fromIdx));
        sb.append(text.substring(fromIdx, toIdx).replaceAll(regex, replacement));
        sb.append(text.substring(toIdx));
        return sb.toString();
    }

    public static String replace(final String text, final String searchString, final String replacement, final int fromIdx, final int toIdx) {
        final StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, fromIdx));
        sb.append(text.substring(fromIdx, toIdx).replace(searchString, replacement));
        sb.append(text.substring(toIdx));
        return sb.toString();
    }

    public static String patch(final String text, final String patch, final int fromIdx, final int toIdx) {
        final StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, fromIdx));
        sb.append(patch);
        sb.append(text.substring(toIdx));
        return sb.toString();
    }

    public static String applyModifications(final String text, final Map<Range<Integer>, Modification[]> modificationMap) {
        final List<Integer> indexes = collectIndexes(modificationMap.keySet());
        indexes.add(text.length());
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indexes.size() - 1; i++) {
            final Integer fromIdx = indexes.get(i);
            final Integer toIdx = indexes.get(i + 1);
            final Range<Integer> range = Range.between(fromIdx, toIdx);
            final Modification[] modification = modificationMap.get(range);
            sb.append(applyModifications(text, fromIdx, toIdx, modification));
        }
        return sb.toString();
    }

    private static String applyModifications(final String text, final int fromIdx, final int toIdx, final Modification... modifications) {
        String edit = text.substring(fromIdx, toIdx);
        if (modifications != null && modifications.length > 0) {
            for (final Modification modification : modifications) {
                if (modification.getSearchString() == null) {
                    edit = modification.getReplacementString();
                } else if (modification.isRegex()) {
                    if (modification.isAll()) {
                        edit = edit.replaceAll(modification.getSearchString(), modification.getReplacementString());
                    } else {
                        edit = edit.replaceFirst(modification.getSearchString(), modification.getReplacementString());
                    }
                } else {
                    if (modification.isAll()) {
                        edit = edit.replace(modification.getSearchString(), modification.getReplacementString());
                    } else {
                        edit = StringUtils.replaceOnce(edit, modification.getSearchString(), modification.getReplacementString());
                    }
                }
            }
        }
        return edit;
    }

    private static List<Integer> collectIndexes(final Set<Range<Integer>> rangeSet) {
        final Set<Integer> indexes = new TreeSet<>();
        indexes.add(0);
        for (final Range<Integer> range : rangeSet) {
            indexes.add(range.getMinimum());
            indexes.add(range.getMaximum());
        }
        return new ArrayList<>(indexes);
    }

}
