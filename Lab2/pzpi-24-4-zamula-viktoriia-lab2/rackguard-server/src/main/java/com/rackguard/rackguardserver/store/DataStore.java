package com.rackguard.rackguardserver.store;

import com.rackguard.rackguardserver.util.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class DataStore {
    private final Path file;
    private AppData data = new AppData();

    public DataStore(@Value("${app.data-file}") String path) {
        this.file = Path.of(path);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (Files.exists(file)) data = Util.MAPPER.readValue(file.toFile(), AppData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    public synchronized <T> T read(Function<AppData, T> f) { return f.apply(data); }

    public synchronized void write(Consumer<AppData> f) { f.accept(data); save(); }

    public synchronized <T> T update(Function<AppData, T> f) {
        T result = f.apply(data);
        save();
        return result;
    }

    public void audit(Integer actorId, String action, String target) {
        write(d -> d.audit.add(new AppData.Audit(Util.nowUtc(), actorId, action, target)));
    }

    private void save() {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Util.MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
