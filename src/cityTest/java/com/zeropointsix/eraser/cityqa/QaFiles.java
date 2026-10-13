package com.zeropointsix.eraser.cityqa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

final class QaFiles {
    static final boolean ENABLED = Boolean.getBoolean("yuji.city.qa.enabled");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static Path directory() { return Path.of(System.getProperty("yuji.city.qa.dir")).toAbsolutePath(); }

    static void write(String name, Object value) {
        try {
            Path destination = directory().resolve(name);
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write city QA marker " + name, failure);
        }
    }

    static JsonObject read(String name) {
        Path file = directory().resolve(name);
        if (!Files.exists(file)) return null;
        try {
            return JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read city QA marker " + name, failure);
        }
    }

    static void failure(String side, String phase, Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        org.slf4j.LoggerFactory.getLogger("RainCityQA").error("City QA failed during " + phase, failure);
        write(side + "-failure.json", Map.of("passed", false, "phase", phase, "error", trace.toString()));
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private QaFiles() { }
}
