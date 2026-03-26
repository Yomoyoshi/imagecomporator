package com.image.controllers;

import com.image.models.requests.FolderRequest;
import com.image.services.ProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST контроллер для управления поиском дубликатов изображений.
 * <p>
 * Предоставляет endpoints для:
 * <ul>
 *   <li>Запуска асинхронного поиска дубликатов в указанной директории</li>
 *   <li>Отслеживания прогресса выполнения операции</li>
 *   <li>Получения результатов поиска после завершения</li>
 * </ul>
 *
 * Все операции с дубликатами выполняются асинхронно для предотвращения блокировки
 * основных потоков приложения при обработке больших объемов изображений.
 *
 * @author Dmitry Ayoshin
 * @version 1.0
 * @since 2024
 * @see ProcessorService
 * @see FolderRequest
 */
@RestController
@RequestMapping("/api")
@Tag(name="Image Duplicate Search", description = "Endpoints for finding duplicates images in directory")
public class SearchController {
    /** Логгер для записи событий и ошибок */
    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    /** Сервис для обработки изображений и поиска дубликатов */
    private final ProcessorService processor;

    /**
     * Конструктор контроллера с внедрением зависимости ProcessorService.
     *
     * @param processor сервис для обработки изображений, не может быть null
     * @throws IllegalArgumentException если processor равен null
     */
    @Autowired
    public SearchController(ProcessorService processor) {
        this.processor = processor;
    }

    /**
     * Запускает асинхронный процесс поиска дубликатов изображений в указанной папке.
     * <p>
     * Метод проверяет валидность входных данных и текущее состояние сервиса:
     * <ul>
     *   <li>Если путь к папке пуст или не указан - возвращает 400 Bad Request</li>
     *   <li>Если сервис уже обрабатывает другую папку - возвращает 202 Accepted с статусом "busy"</li>
     *   <li>В остальных случаях запускает новый процесс поиска и возвращает 202 Accepted</li>
     * </ul>
     *
     * @param request объект с путем к папке для поиска, не может быть null
     * @return ResponseEntity с картой, содержащей:
     *         <ul>
     *           <li>При успешном запуске: status="started", folder, message</li>
     *           <li>При занятости сервиса: status="busy", message</li>
     *           <li>При ошибке валидации: error</li>
     *         </ul>
     * @see ProcessorService#findDuplicatesStart(String)
     * @see ProcessorService#isProcessing()
     * @see ProcessorService#getCurrentFolder()
     */
    @PostMapping(value = "/duplicates")
    @Operation(
            summary="Start duplicate search",
            description="Initiates the process of finding duplicate images in the specified folder"
    )
    @ApiResponses(value={
            @ApiResponse(
                    responseCode = "202",
                    description = "Search started successfully or service is busy",
                    content =@Content(
                            mediaType="application/json",
                            examples = {
                                    @ExampleObject(
                                            name="Started",
                                            value = "{\"status\": \"started\", \"folder\": \"/path/to/folder\", \"message\": \"Search started successfully\"}"
                                    ),
                                    @ExampleObject(
                                            name = "Busy",
                                            value = "{\"status\": \"busy\", \"message\": \"Already processing folder: /path/to/folder\"}"
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<Map<String, Object>> postDuplicates(
            @Parameter(
                    description = "Folder path to search for duplicates",
                    required = true,
                    schema = @Schema(implementation = FolderRequest.class)
            )
            @RequestBody FolderRequest request) {

        log.info("Received duplicate search request");

        // Валидация входных данных
        if (request == null
                || request.getFolderPath() == null
                || request.getFolderPath().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Folder path cannot be empty"));
        }

        String folderPath = request.getFolderPath().trim();
        log.debug("Folder path to process: {}", folderPath);

        // Проверка, не занят ли сервис обработкой другой папки
        if (processor.isProcessing()) {
            String currentFolder = processor.getCurrentFolder();
            log.info("Service is busy processing folder: {}", currentFolder);
            return ResponseEntity.accepted().body(Map.of(
                    "status", "busy",
                    "message", "Already processing folder: " + processor.getCurrentFolder()
            ));
        }

        // Асинхронный запуск поиска дубликатов
        log.info("Starting duplicate search in folder: {}", folderPath);
        CompletableFuture<String> future = processor.findDuplicatesStart(folderPath);

        // Обработка результата асинхронной операции (логирование без блокировки)
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Error during duplicate search in folder: {}", folderPath, throwable);
            } else {
                log.info("Duplicate search completed in folder: {}. Result: {}", folderPath, result);
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "status", "started",
                "folder", folderPath,
                "message", "Search started successfully"
        ));
    }

    /**
     * Возвращает текущий прогресс выполнения операции поиска дубликатов.
     * <p>
     * Метод предоставляет детальную информацию о ходе выполнения:
     * <ul>
     *   <li>Процент выполнения (от 0 до 100)</li>
     *   <li>Флаг активности процесса</li>
     *   <li>Общее количество найденных изображений</li>
     *   <li>Количество уже обработанных изображений</li>
     * </ul>
     *
     * @return ResponseEntity с картой, содержащей информацию о прогрессе:
     *         <ul>
     *           <li>progressPercentage - float, процент выполнения</li>
     *           <li>isProcessing - boolean, флаг активности процесса</li>
     *           <li>totalFiles - int, общее количество файлов</li>
     *           <li>processedFiles - int, количество обработанных файлов</li>
     *         </ul>
     * @see ProcessorService#findDuplicatesProgress()
     * @see ProcessorService#isProcessing()
     * @see ProcessorService#getTotalImages()
     * @see ProcessorService#getProcessedImages()
     */
    @GetMapping(value = "/progress")
    @Operation(
            summary = "Get search progress",
            description = "Returns the current progress of the duplicate search operation"
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Progress information retrieved successfully",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                value = "{\"progressPercentage\": 45.5, \"isProcessing\": true, \"totalFiles\": 100, \"processedFiles\": 45}"
                        )
                )
        )
    })
    public ResponseEntity<Map<String, Object>> getProgress() {

        log.debug("Fetching search progress");

        // Получение текущего состояния от сервиса
        float progress = processor.findDuplicatesProgress();
        boolean isProcessing = processor.isProcessing();
        int totalFiles = processor.getTotalImages();
        int processedFiles = processor.getProcessedImages();

        log.debug("Progress state - isProcessing: {}, progress: {}%, total: {}, processed: {}",
                isProcessing, progress, totalFiles, processedFiles);

        // Формирование ответа
        Map<String, Object> response = new HashMap<>();
        response.put("progressPercentage", progress);
        response.put("isProcessing", isProcessing);
        response.put("totalFiles", totalFiles);
        response.put("processedFiles", processedFiles);

        return ResponseEntity.ok(response);
    }

    /**
     * Возвращает результаты поиска дубликатов после завершения операции.
     * <p>
     * Поведение метода зависит от состояния процесса:
     * <ul>
     *   <li>Если поиск еще выполняется - возвращает 202 Accepted с информацией о прогрессе</li>
     *   <li>Если поиск завершен - возвращает 200 OK с результатами</li>
     * </ul>
     *
     * Результаты представлены в виде списка групп дубликатов, где каждая группа
     * содержит пути к файлам, являющимся дубликатами друг друга.
     *
     * @return ResponseEntity с картой, содержащей:
     *         <ul>
     *           <li>При завершенном поиске: duplicates (List&lt;List&lt;String&gt;&gt;), count, status="completed"</li>
     *           <li>При активном поиске: status="processing", message, progress</li>
     *         </ul>
     * @see ProcessorService#isProcessing()
     * @see ProcessorService#findDuplicatesProgress()
     * @see ProcessorService#findDuplicatesResult()
     */
    @GetMapping(value = "/duplicates/result")
    @Operation(
            summary = "Get duplicate search result",
            description = "Returns the result of the duplicate search after completion"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Search completed and results retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"duplicates\": [[\"file1.jpg\", \"file2.jpg\"]], \"count\": 1, \"status\": \"completed\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "202",
                    description = "Search still in progress",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"status\": \"processing\", \"message\": \"Still processing, please wait\", \"progress\": 45.5}"
                            )
                    )
            )
    })
    public ResponseEntity<Map<String, Object>> getDuplicatesResult() {

        log.debug("Fetching duplicate search results");

        // Проверка, выполняется ли еще поиск
        if (processor.isProcessing()) {
            float progress = processor.findDuplicatesProgress();
            log.debug("Search still in progress at {}%", progress);

            return ResponseEntity.accepted().body(Map.of(
                    "status", "processing",
                    "message", "Still processing, please wait",
                    "progress", processor.findDuplicatesProgress()
            ));
        }

        // Получение и возврат результатов
        List<List<String>> result = processor.findDuplicatesResult();
        int duplicateGroups = result.size();

        log.info("Returning duplicate search results. Found {} groups of duplicates", duplicateGroups);
        // Логирование деталей для отладки
        if (log.isDebugEnabled()) {
            for (int i = 0; i < result.size(); i++) {
                log.debug("Duplicate group {}: {} files - {}",
                        i + 1, result.get(i).size(), result.get(i));
            }
        }

        return ResponseEntity.ok(Map.of(
                "duplicates", result,
                "count", result.size(),
                "status", "completed"
        ));
    }
}