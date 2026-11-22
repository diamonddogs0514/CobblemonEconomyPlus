package com.diamo.cobblemoneconomyplus.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writing config files from the game without ever leaving a half-written file behind. */
public final class ConfigFiles {
    private ConfigFiles() {
    }

    /**
     * Writes {@code text} to {@code path}: the current file is first copied to {@code <name>.bak}, the new text goes to
     * a temporary file, and that file then replaces the old one in one step.
     */
    public static void writeWithBackup(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            Files.copy(path, backupPath(path), StandardCopyOption.REPLACE_EXISTING);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, text.endsWith("\n") ? text : text + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
