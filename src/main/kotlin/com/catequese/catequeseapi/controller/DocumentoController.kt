package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.DocumentoDTO
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Documento
import com.catequese.catequeseapi.repository.DocumentoRepository
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.service.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/documentos")
class DocumentoController(
    private val repo: DocumentoRepository,
    private val cateqRepo: CatequisandoRepository,
    private val storage: FileStorageService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DocumentoController::class.java)
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<Documento>> {
        logger.info("🔍 GET /api/documentos - Listando todos os documentos")
        val result = repo.findAll()
        logger.info("✅ Encontrados ${result.size} documentos")
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Documento> {
        logger.info("🔍 GET /api/documentos/$id - Buscando documento por ID")
        val result = repo.findById(id).orElseThrow { ResourceNotFoundException("Documento não encontrado") }
        logger.info("✅ Documento encontrado: ${result.tipoDocumento}")
        return ResponseEntity.ok(result)
    }

    @GetMapping("/catequisando/{catequisandoId}")
    fun getByCatequisando(@PathVariable catequisandoId: Long): ResponseEntity<List<Documento>> {
        logger.info("🔍 GET /api/documentos/catequisando/$catequisandoId - Buscando documentos do catequisando")
        val catequisando = cateqRepo.findById(catequisandoId)
            .orElseThrow { ResourceNotFoundException("Catequisando id=$catequisandoId não encontrado") }
        val result = repo.findByCatequisando(catequisando)
        logger.info("✅ Encontrados ${result.size} documento(s)")
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}/arquivo")
    fun getArquivo(@PathVariable id: Long): ResponseEntity<ByteArray> {
        logger.info("📥 GET /api/documentos/$id/arquivo - Baixando conteúdo do documento")
        val documento = repo.findById(id).orElseThrow { ResourceNotFoundException("Documento não encontrado") }
        val caminho = documento.caminhoArquivo
            ?: throw ResourceNotFoundException("Documento id=$id não possui arquivo associado")

        val arquivo = storage.download(caminho)
        logger.info("✅ Arquivo carregado: ${arquivo.fileName} (${arquivo.contentType})")

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(arquivo.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${arquivo.fileName}\"")
            .body(arquivo.bytes)
    }

    @PostMapping
    fun create(@RequestBody dto: DocumentoDTO): ResponseEntity<Documento> {
        logger.info("📝 POST /api/documentos - Criando novo documento (DTO)")
        logger.info("📥 DTO recebido: $dto")

        val catequisando = dto.catequisandoId?.let { id ->
            cateqRepo.findById(id).orElseThrow { ResourceNotFoundException("Catequisando id=$id não encontrado") }
        }

        val doc = Documento(
            tipoDocumento = dto.tipoDocumento,
            caminhoArquivo = dto.caminhoArquivo,
            dataEnvio = dto.dataEnvio,
            catequisando = catequisando,
            tipoStatus = dto.tipoStatus ?: "PENDENTE"
        )

        val saved = repo.save(doc)
        logger.info("✅ Documento criado com ID: ${saved.idDocumento}")
        return ResponseEntity.created(URI("/api/documentos/${saved.idDocumento}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody dto: DocumentoDTO): ResponseEntity<Documento> {
        logger.info("🔄 PUT /api/documentos/$id - Atualizando documento (DTO)")
        logger.info("📥 DTO recebido: $dto")

        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Documento não encontrado") }

        val catequisando = dto.catequisandoId?.let { cid ->
            cateqRepo.findById(cid).orElseThrow { ResourceNotFoundException("Catequisando id=$cid não encontrado") }
        } ?: existing.catequisando

        val updated = existing.copy(
            tipoDocumento = dto.tipoDocumento,
            caminhoArquivo = dto.caminhoArquivo,
            dataEnvio = dto.dataEnvio,
            catequisando = catequisando
        )

        logger.info("✅ Documento atualizado com sucesso")
        return ResponseEntity.ok(repo.save(updated))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        logger.info("🗑️  DELETE /api/documentos/$id - Deletando documento")
        repo.deleteById(id)
        logger.info("✅ Documento deletado com sucesso")
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: Long,
        @RequestBody request: Map<String, String?>
    ): ResponseEntity<Documento> {
        logger.info("🔄 PUT /api/documentos/$id/status - Atualizando status")
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Documento não encontrado") }
        val novoStatus = request["novoStatus"] ?: "PENDENTE"

        val updated = existing.copy(
            tipoStatus = novoStatus
        )

        logger.info("✅ Status atualizado para: $novoStatus")
        return ResponseEntity.ok(repo.save(updated))
    }
}
