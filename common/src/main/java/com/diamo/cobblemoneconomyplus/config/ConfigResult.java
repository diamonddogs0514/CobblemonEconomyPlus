package com.diamo.cobblemoneconomyplus.config;

import java.util.List;

/** Problems found while loading or checking a config file. {@code errors} mean the file was not used at all. */
public record ConfigResult(List<String> errors, List<String> warnings) {
    public ConfigResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }
}
