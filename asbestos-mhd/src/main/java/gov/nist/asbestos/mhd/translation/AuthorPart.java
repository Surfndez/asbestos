package gov.nist.asbestos.mhd.translation;

import gov.nist.asbestos.simapi.validation.Val;

public abstract class AuthorPart {
    String value;
    String[] parts;

    abstract void validate(Val val);

    void parse() {
        parts = value.split("\\^");
    }

    public AuthorPart setValue(String value, Val val) {
        this.value = value;
        parse();
        validate(val);
        return this;
    }

    String get(int i) {
        if (i > parts.length)
            return "";
        return parts[i-1];
    }
}
