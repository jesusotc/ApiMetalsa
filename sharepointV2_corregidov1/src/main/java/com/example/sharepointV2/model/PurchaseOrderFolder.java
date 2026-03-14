package com.example.sharepointV2.model;

public class PurchaseOrderFolder {

    private int id;
    private String title;
    private String invoice;

    public PurchaseOrderFolder() {
    }

    public PurchaseOrderFolder(int id, String title, String invoice) {
        this.id = id;
        this.title = title;
        this.invoice = invoice;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getInvoice() {
        return invoice;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setInvoice(String invoice) {
        this.invoice = invoice;
    }

    public String getServerRelativeFolderPath(String attachmentBasePath) {
        String base = attachmentBasePath == null ? "" : attachmentBasePath.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + title + "/" + invoice;
    }
}