package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.service.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/files")
class
UploadController(private val storage: FileStorageService) {

    companion object {
        private val logger = LoggerFactory.getLogger(UploadController::class.java)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestPart("file") file: MultipartFile): ResponseEntity<Map<String, Any>> {
        logger.info("📤 POST /api/files - Upload de arquivo iniciado")
        logger.info("   - Filename: ${file.originalFilename}")
        logger.info("   - Content-Type: ${file.contentType}")
        logger.info("   - Size: ${file.size} bytes")

        val saved = storage.store(file)
        val relativePath = "uploads/${saved.fileName}"
        val body = mapOf(
            "filename" to saved.fileName.toString(),
            "size" to file.size,
            "path" to relativePath
        )

        logger.info("✅ Arquivo salvo com sucesso")
        logger.info("   - Caminho absoluto: ${saved.toAbsolutePath()}")
        logger.info("   - Caminho retornado (relativo): $relativePath")
        return ResponseEntity.ok(body)
    }
}
