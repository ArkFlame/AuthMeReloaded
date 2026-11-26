package fr.xephi.authme.platform;

import java.util.Objects;

/**
 * Description of one text input displayed in a dialog.
 */
public final class DialogInputSpec {

    private final String id;
    private final String label;
    private final int maxLength;

    public DialogInputSpec(String id, String label, int maxLength) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.maxLength = maxLength;
    }

    public String id() { return id; }
    public String label() { return label; }
    public int maxLength() { return maxLength; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DialogInputSpec)) return this == o;
        DialogInputSpec that = (DialogInputSpec) o;
        return maxLength == that.maxLength && id.equals(that.id) && label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, maxLength);
    }

    @Override
    public String toString() {
        return "DialogInputSpec[id=" + id + ", label=" + label + ", maxLength=" + maxLength + "]";
    }
}
