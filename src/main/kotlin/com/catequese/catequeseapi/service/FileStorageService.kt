package com.catequese.catequeseapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class FileStorageService {
    private val root: Path = Paths.get("uploads")

    companion object {
        private val logger = LoggerFactory.getLogger(FileStorageService::class.java)
    }

    init {
        logger.info("📂 Inicializando FileStorageService com diretório raiz: ${root.toAbsolutePath()}")
        Files.createDirectories(root)
        logger.info("✅ Diretório de uploads criado/verificado")
    }

    fun store(file: MultipartFile): Path {
        if (file.isEmpty) {
            logger.error("❌ Tentativa de upload de arquivo vazio")
            throw IllegalArgumentException("Arquivo vazio")
        }

        val original = file.originalFilename ?: "file-${System.currentTimeMillis()}"
        logger.debug("📥 Salvando arquivo: $original (${file.size} bytes)")

        // sanitize filename (keep extension)
        val basename = Path.of(original).fileName.toString()
        val dotIndex = basename.lastIndexOf('.')
        val namePart = if (dotIndex > 0) basename.substring(0, dotIndex) else basename
        val extPart = if (dotIndex > 0) basename.substring(dotIndex) else ""
        // allow letters, numbers, underscore and dash
        val safeName = namePart.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val unique = "${UUID.randomUUID()}_${safeName}${extPart}"

        val target = root.resolve(unique)

        logger.debug("   - Caminho destino: ${target.toAbsolutePath()}")
        Files.copy(file.inputStream, target, StandardCopyOption.REPLACE_EXISTING)

        logger.info("✅ Arquivo salvo com sucesso: ${target.toAbsolutePath()}")
        return target
    }
}
