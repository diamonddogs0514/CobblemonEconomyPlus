package com.diamo.cobblemoneconomyplus.config;

/** A config value that cannot be used, carrying a translatable {@link ConfigMessage}. */
public final class ConfigException extends IllegalArgumentException {
    private final transient ConfigMessage problem;

    public ConfigException(String key, String english, Object... args) {
        this(ConfigMessage.of(key, english, args));
    }

    public ConfigException(ConfigMessage problem) {
        super(problem.text());
        this.problem = problem;
    }

    public ConfigMessage problem() {
        return problem;
    }
}
