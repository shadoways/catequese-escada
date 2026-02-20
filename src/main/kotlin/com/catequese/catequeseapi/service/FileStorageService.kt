package com.catequese.catequeseapi.service

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.util.UUID

@Service
class FileStorageService(
    @Value("\${gcs.bucket}") private val bucketName: String
) {
    // Storage client: try to read JSON credentials from env var GOOGLE_APPLICATION_CREDENTIALS_JSON, otherwise use ADC
    private val storage: Storage = run {
        val envJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON") ?: ""

        when {
            envJson.trimStart().startsWith('{') -> {
                logger.info("🔑 Carregando credenciais de Service Account a partir da variável de ambiente GOOGLE_APPLICATION_CREDENTIALS_JSON")
                ByteArrayInputStream(envJson.toByteArray()).use { bais ->
                    val creds = ServiceAccountCredentials.fromStream(bais)
                    StorageOptions.newBuilder().setCredentials(creds).build().service
                }
            }
            else -> {
                logger.info("🔑 Usando Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS ou ambiente de GCP)")
                StorageOptions.getDefaultInstance().service
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileStorageService::class.java)
    }

    init {
        logger.info("📂 Inicializando FileStorageService usando bucket GCS: $bucketName")
    }

    data class StoredFile(
        val fileName: String,
        val gcsPath: String,
        val publicUrl: String
    )

    fun store(file: MultipartFile): StoredFile {
        if (file.isEmpty) {
            logger.error("❌ Tentativa de upload de arquivo vazio")
            throw IllegalArgumentException("Arquivo vazio")
        }

        val original = file.originalFilename ?: "file-${System.currentTimeMillis()}"
        logger.debug("📥 Salvando arquivo: $original (${file.size} bytes) no bucket $bucketName")

        // sanitize filename (keep extension)
        val basename = java.nio.file.Path.of(original).fileName.toString()
        val dotIndex = basename.lastIndexOf('.')
        val namePart = if (dotIndex > 0) basename.substring(0, dotIndex) else basename
        val extPart = if (dotIndex > 0) basename.substring(dotIndex) else ""
        // allow letters, numbers, underscore and dash
        val safeName = namePart.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val unique = "${UUID.randomUUID()}_${safeName}${extPart}"

        val blobId = BlobId.of(bucketName, unique)
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType(file.contentType ?: "application/octet-stream")
            .build()

        logger.debug("   - Enviando para GCS: gs://$bucketName/$unique")

        // Create blob (this will upload the data)
        storage.create(blobInfo, file.inputStream.readAllBytes())

        val gcsPath = "gs://$bucketName/$unique"
        val publicUrl = "https://storage.googleapis.com/$bucketName/$unique"

        logger.info("✅ Arquivo enviado ao GCS: $gcsPath")

        return StoredFile(unique, gcsPath, publicUrl)
    }
}
