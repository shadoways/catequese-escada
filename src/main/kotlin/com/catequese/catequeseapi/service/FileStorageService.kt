package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
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

    data class DownloadedFile(
        val bytes: ByteArray,
        val contentType: String,
        val fileName: String
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

    /**
     * Baixa o conteúdo de um arquivo já armazenado, a partir do valor salvo em
     * Documento.caminhoArquivo (aceita "gs://bucket/objeto", a URL pública
     * "https://storage.googleapis.com/bucket/objeto" ou apenas o nome do objeto).
     */
    fun download(caminhoArquivo: String): DownloadedFile {
        val objectName = extractObjectName(caminhoArquivo)
        logger.debug("📥 Baixando arquivo do GCS: bucket=$bucketName objeto=$objectName")

        val blobId = BlobId.of(bucketName, objectName)
        val blob = storage.get(blobId)
            ?: throw ResourceNotFoundException("Arquivo não encontrado no bucket $bucketName: $objectName")

        return DownloadedFile(
            bytes = blob.getContent(),
            contentType = blob.contentType ?: "application/octet-stream",
            fileName = objectName.substringAfterLast('/')
        )
    }

    private fun extractObjectName(caminhoArquivo: String): String {
        val semPrefixo = when {
            caminhoArquivo.startsWith("gs://") -> caminhoArquivo.removePrefix("gs://")
            caminhoArquivo.startsWith("https://storage.googleapis.com/") ->
                caminhoArquivo.removePrefix("https://storage.googleapis.com/")
            caminhoArquivo.startsWith("http://storage.googleapis.com/") ->
                caminhoArquivo.removePrefix("http://storage.googleapis.com/")
            else -> return caminhoArquivo
        }
        // primeiro segmento é o nome do bucket; o restante é o nome do objeto
        val barraIdx = semPrefixo.indexOf('/')
        return if (barraIdx == -1) semPrefixo else semPrefixo.substring(barraIdx + 1)
    }
}
