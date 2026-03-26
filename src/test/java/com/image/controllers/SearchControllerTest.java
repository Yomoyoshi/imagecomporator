package com.image.controllers;

import com.image.models.requests.FolderRequest;
import com.image.services.ProcessorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessorService processor;

//    @Test
//    void postDuplicates_withNullRequest_shouldReturnBadRequest() throws Exception {
//        mockMvc.perform(post("/api/duplicates")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("null"))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.error", is("Folder path cannot be empty")));
//    }

    @Test
    void postDuplicates_withEmptyFolderPath_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Folder path cannot be empty")));
    }

    @Test
    void postDuplicates_withEmptyPath_shouldReturnBadRequest() throws Exception {
        FolderRequest request = new FolderRequest();
        request.setFolderPath("");

        mockMvc.perform(post("/api/duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Folder path cannot be empty")));
    }

    @Test
    void postDuplicates_whenServiceIsProcessing_shouldReturnAcceptedWithBusyStatus() throws Exception {
        when(processor.isProcessing()).thenReturn(true);
        when(processor.getCurrentFolder()).thenReturn("/existing/folder");

        FolderRequest request = new FolderRequest();
        request.setFolderPath("/new/folder");

        mockMvc.perform(post("/api/duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"/new/folder\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("busy")))
                .andExpect(jsonPath("$.message", containsString("Already processing folder")));
    }

    @Test
    void postDuplicates_whenServiceIsFree_shouldStartSearchAndReturnAccepted() throws Exception {
        when(processor.isProcessing()).thenReturn(false);
        when(processor.findDuplicatesStart(anyString()))
                .thenReturn(CompletableFuture.completedFuture("job123"));

        FolderRequest request = new FolderRequest();
        request.setFolderPath("/test/folder");

        mockMvc.perform(post("/api/duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"/test/folder\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("started")))
                .andExpect(jsonPath("$.folder", is("/test/folder")))
                .andExpect(jsonPath("$.message", is("Search started successfully")));

        verify(processor, times(1)).findDuplicatesStart("/test/folder");
    }

    @Test
    void getProgress_shouldReturnProgressInformation() throws Exception {
        when(processor.findDuplicatesProgress()).thenReturn(45.5f);
        when(processor.isProcessing()).thenReturn(true);
        when(processor.getTotalImages()).thenReturn(100);
        when(processor.getProcessedImages()).thenReturn(45);

        mockMvc.perform(get("/api/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage", is(45.5)))
                .andExpect(jsonPath("$.isProcessing", is(true)))
                .andExpect(jsonPath("$.totalFiles", is(100)))
                .andExpect(jsonPath("$.processedFiles", is(45)));
    }

    @Test
    void getDuplicatesResult_whenProcessing_shouldReturnAcceptedWithProgress() throws Exception {
        when(processor.isProcessing()).thenReturn(true);
        when(processor.findDuplicatesProgress()).thenReturn(30.0f);

        mockMvc.perform(get("/api/duplicates/result"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("processing")))
                .andExpect(jsonPath("$.message", is("Still processing, please wait")))
                .andExpect(jsonPath("$.progress", is(30.0)));
    }

    @Test
    void getDuplicatesResult_whenCompleted_shouldReturnResults() throws Exception {
        List<List<String>> duplicates = List.of(
                List.of("/path/a.jpg", "/path/b.jpg"),
                List.of("/path/c.png", "/path/d.png", "/path/e.png")
        );
        when(processor.isProcessing()).thenReturn(false);
        when(processor.findDuplicatesResult()).thenReturn(duplicates);

        mockMvc.perform(get("/api/duplicates/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("completed")))
                .andExpect(jsonPath("$.count", is(2)))
                .andExpect(jsonPath("$.duplicates[0].length()", is(2)))
                .andExpect(jsonPath("$.duplicates[1].length()", is(3)))
                .andExpect(jsonPath("$.duplicates[0][0]", is("/path/a.jpg")));
    }
}