package com.image.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request object containing folder path for duplicate search")
public class FolderRequest {

    @Schema(
            description = "Path to the folder to search for duplicate images",
            example = "/home/user/images",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String folderPath;

    @Override
    public String toString() {
        return "FolderRequest{" +
                "folderPath='" + folderPath + '\'' +
                '}';
    }
}
