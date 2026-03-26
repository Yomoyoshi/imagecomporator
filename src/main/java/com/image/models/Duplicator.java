package com.image.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class Duplicator {

    private static final Logger log = LoggerFactory.getLogger(Duplicator.class);
    Map<Long, Set<String>> duplicates;
    AtomicInteger checkedImagesCount;

    public Map<Long, Set<String>> getMapOfDuplicates() {
        if (Objects.isNull(duplicates)) {
            duplicates = new ConcurrentHashMap<>();
        }
        return duplicates;
    }

    public AtomicInteger getCheckedImagesCount() {
        if (Objects.isNull(checkedImagesCount)) {
            checkedImagesCount = new AtomicInteger();
        }
        return checkedImagesCount;
    }

    public void reset() {
        duplicates = new ConcurrentHashMap<>();
        checkedImagesCount = new AtomicInteger(0);
        log.debug("Duplicator reset");
    }

    public void incrementCheckedImagesCount() {
        int newValue = getCheckedImagesCount().incrementAndGet();
        log.debug("Checked images count incremented to: {}", newValue);
    }

    public void deleteDuplicates() {
        getMapOfDuplicates().values().removeIf(set -> set.size() <= 1);
        log.debug("Deleted non-duplicate groups, remaining: {}", getMapOfDuplicates().size());
    }

    public void consolePrintResults() {
        if (getMapOfDuplicates().isEmpty()) {
            log.info("Нет повторяющихся изображений.");
        } else {
            for (Set<String> duplicateGroup : getMapOfDuplicates().values()) {
                log.info("Группа повторяющихся изображений ({} файлов):", duplicateGroup.size());
                duplicateGroup.forEach(path -> log.info("  {}", path));
            }
        }
    }

    public void filePrintResults() {
        if (getMapOfDuplicates().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
        String fileName = "duplicates_" + now.format(formatter) + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (Set<String> duplicateGroup : getMapOfDuplicates().values()) {
                writer.write("Группа повторяющихся изображений (" + duplicateGroup.size() + " файлов):\n");
                for (String imagePath : duplicateGroup) {
                    writer.write(imagePath + "\n");
                }
                writer.newLine();
            }
            log.info("Дубликаты сохранены в файл {}", fileName);
        } catch (IOException e) {
            log.error("Ошибка при сохранении результатов:", e);
        }
    }
}
