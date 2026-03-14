package com.example.sharepointV2.controller;

import com.example.sharepointV2.service.SharepointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/documentos")
public class DocumentosController {

    private final SharepointService sharepointService;

    public DocumentosController(SharepointService sharepointService) {
        this.sharepointService = sharepointService;
    }

    @GetMapping
    public Map<String, Object> obtenerPorQuery(@RequestParam String pedimento) {
        return sharepointService.obtenerDocumentosPorPedimento(pedimento);
    }

    @GetMapping("/{pedimento}")
    public Map<String, Object> obtenerPorPath(@PathVariable String pedimento) {
        return sharepointService.obtenerDocumentosPorPedimento(pedimento);
    }
}