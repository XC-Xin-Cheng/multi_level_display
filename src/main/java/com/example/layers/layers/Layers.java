package com.example.layers.layers;

import com.example.layers.LayersAddon;
import com.example.layers.gui.LayerGui;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class Layers {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(MeteorClient.FOLDER, "layers.json");

    private static Layers instance;

    public final List<Layer> layers = new ArrayList<>();
    public int activeIndex;

    public static void init() {
        instance = new Layers();
        instance.load();
    }

    public static Layers get() {
        return instance;
    }

    public Layer getActive() {
        if (layers.isEmpty()) layers.add(new Layer("Default", true));
        if (activeIndex < 0 || activeIndex >= layers.size()) activeIndex = 0;
        return layers.get(activeIndex);
    }

    public void next() {
        if (layers.isEmpty()) return;
        switchTo(Math.floorMod(activeIndex + 1, layers.size()));
    }

    public void previous() {
        if (layers.isEmpty()) return;
        switchTo(Math.floorMod(activeIndex - 1, layers.size()));
    }

    /**
     * Переключение слоя: уходящий слой запоминает текущие состояния модулей,
     * входящий применяет свои сохранённые (если сохранялись).
     */
    private void switchTo(int newIndex) {
        if (layers.isEmpty() || newIndex == activeIndex) return;

        getActive().captureStates();
        activeIndex = newIndex;
        getActive().applyStates();
        changed();
    }

    public Layer add(String name) {
        String unique = name;
        int i = 2;
        while (byName(unique) != null) unique = name + " " + i++;

        getActive().captureStates();

        Layer layer = new Layer(unique, false);
        layers.add(layer);
        activeIndex = layers.size() - 1;
        changed();
        return layer;
    }

    public boolean remove(String name) {
        if (layers.size() <= 1) return false;

        Layer layer = byName(name);
        if (layer == null) return false;

        int index = layers.indexOf(layer);
        layers.remove(index);

        if (activeIndex >= layers.size()) activeIndex = layers.size() - 1;
        else if (index < activeIndex) activeIndex--;

        changed();
        return true;
    }

    public boolean setActive(String name) {
        Layer layer = byName(name);
        if (layer == null) return false;

        switchTo(layers.indexOf(layer));
        return true;
    }

    public boolean rename(Layer layer, String name) {
        Layer existing = byName(name);
        if (existing != null && existing != layer) return false;

        layer.name = name;
        changed();
        return true;
    }

    public Layer byName(String name) {
        for (Layer layer : layers) {
            if (layer.name.equalsIgnoreCase(name)) return layer;
        }
        return null;
    }

    public void changed() {
        save();
        LayerGui.onLayersChanged();
    }

    /** Атомарная запись: сначала во временный файл в той же папке, затем перенос — краш игры посреди записи не оставит битый layers.json. */
    public void save() {
        try {
            File dir = FILE.getParentFile();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();

            File temp = File.createTempFile("layers-", ".tmp", dir);
            Files.writeString(temp.toPath(), GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LayersAddon.LOG.error("Failed to save layers", e);
        }
    }

    private void load() {
        if (FILE.exists()) {
            try {
                Layers data = GSON.fromJson(Files.readString(FILE.toPath(), StandardCharsets.UTF_8), Layers.class);
                if (data != null && data.layers != null) {
                    layers.addAll(data.layers);
                    activeIndex = data.activeIndex;
                }
            } catch (Exception e) {
                // Битый файл не затираем — переименовываем в бэкап, чтобы данные можно было вытащить руками
                File backup = new File(FILE.getParentFile(), "layers.json.corrupted-" + java.lang.System.currentTimeMillis());
                try {
                    Files.copy(FILE.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LayersAddon.LOG.error("Failed to load layers, corrupted file backed up to {}", backup, e);
                } catch (IOException e2) {
                    LayersAddon.LOG.error("Failed to load layers (backup also failed)", e2);
                }
            }
        }

        if (layers.isEmpty()) layers.add(new Layer("Default", true));
    }
}
