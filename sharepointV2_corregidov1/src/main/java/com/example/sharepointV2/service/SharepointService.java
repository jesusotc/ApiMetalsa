package com.example.sharepointV2.service;

import com.example.sharepointV2.model.PurchaseOrderFolder;
import com.example.sharepointV2.model.SharePointFileMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SharepointService {

    private static final Map<Integer, String> DOCUMENT_TYPE_MAP = createDocumentTypeMap();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${sharepoint.auth.url}")
    private String tokenUrl;

    @Value("${sharepoint.site.url}")
    private String siteUrl;

    @Value("${sharepoint.attachment.base.path}")
    private String attachmentBasePath;

    @Value("${sharepoint.purchase-orders.list}")
    private String purchaseOrdersList;

    @Value("${sharepoint.sbu}")
    private int sbu;

    @Value("${sharepoint.client.id}")
    private String clientId;

    @Value("${sharepoint.client.secret}")
    private String clientSecret;

    @Value("${sharepoint.resource}")
    private String resource;

    public SharepointService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> obtenerDocumentosPorPedimento(String pedimento) {
        if (pedimento == null || pedimento.trim().isEmpty()) {
            throw new RuntimeException("El pedimento es obligatorio");
        }

        String token = getTokenFTTS();
        List<PurchaseOrderFolder> folders = getListJesus(token, pedimento.trim());
        List<SharePointFileMetadata> metadata = getFilesListJesus(token, folders);
        List<Map<String, Object>> files = getFiless(token, metadata);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pedimento", pedimento.trim());
        response.put("totalCarpetas", folders.size());
        response.put("totalArchivos", files.size());
        response.put("value", files);
        return response;
    }

    public String getTokenFTTS() {
        Map<String, String> formData = new HashMap<>();
        formData.put("grant_type", "client_credentials");
        formData.put("client_id", clientId);
        formData.put("client_secret", clientSecret);
        formData.put("resource", resource);

        String formBody = formData.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body(), "obteniendo token de SharePoint");

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode accessTokenNode = json.get("access_token");

            if (accessTokenNode == null || accessTokenNode.asText().trim().isEmpty()) {
                throw new RuntimeException("No se pudo obtener access_token: " + response.body());
            }

            return accessTokenNode.asText().trim();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Proceso interrumpido obteniendo token de SharePoint", e);
        } catch (IOException e) {
            throw new RuntimeException("Error obteniendo token de SharePoint", e);
        }
    }

    public List<PurchaseOrderFolder> getListJesus(String token, String pedimento) {
        String filterText = String.format(
                "(SBU eq %d and (StatusId eq 4 or StatusId eq 7) and Invoice ne null and Title ne null and ImportRequest eq '%s' and Traffic ne null)",
                sbu,
                pedimento.replace("'", "''")
        );

        String apiUrl = normalizeSiteUrl()
                + "/_api/web/lists/GetByTitle('" + purchaseOrdersList + "')/items"
                + "?$filter=" + URLEncoder.encode(filterText, StandardCharsets.UTF_8)
                + "&$top=50000"
                + "&$select=Id,Title,Invoice";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/json;odata=nometadata")
                .header("Authorization", "Bearer " + token)
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body(), "consultando PurchaseOrders");

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.get("value");
            List<PurchaseOrderFolder> folders = new ArrayList<>();

            if (values == null || !values.isArray()) {
                return folders;
            }

            for (JsonNode item : values) {
                String title = asText(item, "Title");
                String invoice = asText(item, "Invoice");

                if (title.isBlank() || invoice.isBlank()) {
                    continue;
                }

                folders.add(new PurchaseOrderFolder(
                        item.path("Id").asInt(),
                        title.trim(),
                        invoice.trim()
                ));
            }

            return folders;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Proceso interrumpido consultando PurchaseOrders", e);
        } catch (IOException e) {
            throw new RuntimeException("Error consultando carpetas por pedimento en SharePoint", e);
        }
    }

    public List<SharePointFileMetadata> getFilesListJesus(String token, List<PurchaseOrderFolder> folders) {
        List<SharePointFileMetadata> result = new ArrayList<>();

        for (PurchaseOrderFolder folder : folders) {
            String folderPath = folder.getServerRelativeFolderPath(attachmentBasePath);
            String encodedFolderPath = UriUtils.encodePath(folderPath, StandardCharsets.UTF_8);
            String escapedPath = escapeODataString(encodedFolderPath);

            String apiUrl = normalizeSiteUrl()
                    + "/_api/web/GetFolderByServerRelativeUrl('"
                    + escapedPath
                    + "')/Files"
                    + "?$expand=ListItemAllFields"
                    + "&$select=Name,ServerRelativeUrl,ListItemAllFields/FTTS_DocumentTypesId";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/json;odata=nometadata")
                    .header("Authorization", "Bearer " + token)
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 404) {
                    continue;
                }

                ensureSuccess(response.statusCode(), response.body(),
                        "consultando metadata de archivos de " + folderPath);

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode files = root.get("value");

                if (files == null || !files.isArray()) {
                    continue;
                }

                for (JsonNode file : files) {
                    String name = asText(file, "Name");
                    String serverRelativeUrl = asText(file, "ServerRelativeUrl");

                    JsonNode idsNode = file.path("ListItemAllFields").path("FTTS_DocumentTypesId");
                    List<Integer> ids = new ArrayList<>();

                    if (idsNode.isArray()) {
                        for (JsonNode idNode : idsNode) {
                            ids.add(idNode.asInt());
                        }
                    } else if (!idsNode.isMissingNode() && !idsNode.isNull() && idsNode.canConvertToInt()) {
                        ids.add(idsNode.asInt());
                    }

                    result.add(new SharePointFileMetadata(folderPath, name, serverRelativeUrl, ids));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Proceso interrumpido consultando tipos de documento en la carpeta: " + folderPath, e);
            } catch (IOException e) {
                throw new RuntimeException("Error consultando tipos de documento en la carpeta: " + folderPath, e);
            }
        }

        return result;
    }

    public List<Map<String, Object>> getFiless(String token, List<SharePointFileMetadata> filesMetadata) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (SharePointFileMetadata metadata : filesMetadata) {
            String base64 = descargarArchivo(token, metadata.getServerRelativeUrl());
            Integer typeId = metadata.getPrimaryDocumentTypeId();
            String tipo = mapDocumentType(typeId);

            Map<String, Object> listItemAllFields = new LinkedHashMap<>();
            listItemAllFields.put("FTTS_DocumentTypesId", metadata.getDocumentTypeIds());
            listItemAllFields.put("TIPO", tipo);

            Map<String, Object> file = new LinkedHashMap<>();
            file.put("ListItemAllFields", listItemAllFields);
            file.put("Name", metadata.getName());
            file.put("Base64", base64);
            file.put("TIPO", tipo);

            result.add(file);
        }

        return result;
    }

    public String descargarArchivo(String token, String serverRelativeUrl) {
        if (serverRelativeUrl == null || serverRelativeUrl.trim().isEmpty()) {
            return "";
        }

        String encodedPath = UriUtils.encodePath(serverRelativeUrl.trim(), StandardCharsets.UTF_8);
        String escapedPath = escapeODataString(encodedPath);

        String apiUrl = normalizeSiteUrl()
                + "/_api/web/GetFileByServerRelativeUrl('"
                + escapedPath
                + "')/$value";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + token)
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body(), StandardCharsets.UTF_8);
                throw new RuntimeException("Error descargando archivo: " + serverRelativeUrl + ". Respuesta: " + body);
            }

            return Base64.getEncoder().encodeToString(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Proceso interrumpido descargando archivo: " + serverRelativeUrl, e);
        } catch (IOException e) {
            throw new RuntimeException("Error descargando archivo: " + serverRelativeUrl, e);
        }
    }

    private void ensureSuccess(int statusCode, String responseBody, String action) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException("Error " + statusCode + " " + action + ": " + responseBody);
        }
    }

    private String normalizeSiteUrl() {
        if (siteUrl == null || siteUrl.trim().isEmpty()) {
            throw new RuntimeException("sharepoint.site.url no está configurado");
        }
        String clean = siteUrl.trim();
        return clean.endsWith("/") ? clean.substring(0, clean.length() - 1) : clean;
    }

    private String escapeODataString(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String mapDocumentType(Integer id) {
        if (id == null) {
            return "SIN_TIPO";
        }
        return DOCUMENT_TYPE_MAP.getOrDefault(id, "DESCONOCIDO_" + id);
    }

    private static Map<Integer, String> createDocumentTypeMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "PackingList");
        map.put(2, "Factura");
        map.put(3, "ConocimientoEmbarque");
        map.put(4, "CertificadoOrigen");
        map.put(5, "CertificadoMolino");
        map.put(6, "Otro");
        map.put(7, "Proforma");
        map.put(8, "Precuenta");
        map.put(9, "FacturaGontor");
        map.put(10, "Recibo");
        map.put(11, "Anexo");
        map.put(12, "Adicional");
        map.put(13, "EmailAnexo");
        map.put(14, "EscritosMV");
        map.put(15, "PolizaSeguroGlobal");
        map.put(16, "FletePreFactura");
        return map;
    }

    private String asText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? "" : field.asText("");
    }
}