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
        val relativePath = saved.gcsPath
        val body = mapOf(
            "filename" to saved.fileName,
            "size" to file.size,
            "path" to relativePath,
            "url" to saved.publicUrl
        )

        logger.info("✅ Arquivo salvo com sucesso")
        logger.info("   - GCS path: ${saved.gcsPath}")
        logger.info("   - URL pública: ${saved.publicUrl}")
        return ResponseEntity.ok(body)
    }
}
