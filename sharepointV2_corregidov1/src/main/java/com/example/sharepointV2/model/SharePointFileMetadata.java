package com.example.sharepointV2.model;

import java.util.ArrayList;
import java.util.List;

public class SharePointFileMetadata {

    private String folderPath;
    private String name;
    private String serverRelativeUrl;
    private List<Integer> documentTypeIds;

    public SharePointFileMetadata() {
        this.documentTypeIds = new ArrayList<>();
    }

    public SharePointFileMetadata(String folderPath, String name, String serverRelativeUrl, List<Integer> documentTypeIds) {
        this.folderPath = folderPath;
        this.name = name;
        this.serverRelativeUrl = serverRelativeUrl;
        this.documentTypeIds = documentTypeIds == null ? new ArrayList<>() : documentTypeIds;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getName() {
        return name;
    }

    public String getServerRelativeUrl() {
        return serverRelativeUrl;
    }

    public List<Integer> getDocumentTypeIds() {
        return documentTypeIds;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setServerRelativeUrl(String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
    }

    public void setDocumentTypeIds(List<Integer> documentTypeIds) {
        this.documentTypeIds = documentTypeIds;
    }

    public Integer getPrimaryDocumentTypeId() {
        if (documentTypeIds == null || documentTypeIds.isEmpty()) {
            return null;
        }
        return documentTypeIds.get(0);
    }
}