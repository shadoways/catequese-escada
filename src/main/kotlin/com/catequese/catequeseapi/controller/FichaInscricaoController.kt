package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.FichaInscricaoDTO
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.FichaInscricao
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.repository.FichaInscricaoRepository
import org.slf4j.LoggerFactory
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
@RequestMapping("/api/fichas")
class FichaInscricaoController(
    private val repo: FichaInscricaoRepository,
    private val cateqRepo: CatequisandoRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(FichaInscricaoController::class.java)
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<FichaInscricao>> {
        logger.info("🔍 GET /api/fichas - Listando todas as fichas")
        val result = repo.findAll()
        logger.info("✅ Encontradas ${result.size} fichas")
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<FichaInscricao> {
        logger.info("🔍 GET /api/fichas/$id - Buscando ficha por ID")
        val result = repo.findById(id).orElseThrow { ResourceNotFoundException("Ficha não encontrada") }
        logger.info("✅ Ficha encontrada: ID=${result.idFicha}")
        return ResponseEntity.ok(result)
    }

    @GetMapping("/catequisando/{catequisandoId}")
    fun getByCatequisando(@PathVariable catequisandoId: Long): ResponseEntity<List<FichaInscricao>> {
        logger.info("🔍 GET /api/fichas/catequisando/$catequisandoId - Buscando fichas do catequisando")
        val catequisando = cateqRepo.findById(catequisandoId)
            .orElseThrow { ResourceNotFoundException("Catequisando id=$catequisandoId não encontrado") }
        val result = repo.findByCatequisando(catequisando)
        logger.info("✅ Encontradas ${result.size} ficha(s)")
        return ResponseEntity.ok(result)
    }

    @PostMapping
    fun create(@RequestBody dto: FichaInscricaoDTO): ResponseEntity<FichaInscricao> {
        logger.info("📝 POST /api/fichas - Criando nova ficha (DTO)")
        logger.info("📥 DTO recebido: $dto")

        val catequisando = dto.catequisandoId?.let { id ->
            logger.debug("🔎 Procurando catequisando id=$id")
            cateqRepo.findById(id).orElseThrow { ResourceNotFoundException("Catequisando id=$id não encontrado") }
        }

        val ficha = FichaInscricao(
            dataInscricao = dto.dataInscricao,
            observacoes = dto.observacoes,
            catequisando = catequisando
        )

        val saved = repo.save(ficha)
        logger.info("✅ Ficha criada com ID: ${saved.idFicha}")
        return ResponseEntity.created(URI("/api/fichas/${saved.idFicha}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody dto: FichaInscricaoDTO): ResponseEntity<FichaInscricao> {
        logger.info("🔄 PUT /api/fichas/$id - Atualizando ficha (DTO)")
        logger.info("📥 DTO recebido: $dto")

        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Ficha não encontrada") }

        val catequisando = dto.catequisandoId?.let { cid ->
            cateqRepo.findById(cid).orElseThrow { ResourceNotFoundException("Catequisando id=$cid não encontrado") }
        } ?: existing.catequisando

        val updated = existing.copy(
            dataInscricao = dto.dataInscricao,
            observacoes = dto.observacoes,
            catequisando = catequisando
        )

        val saved = repo.save(updated)
        logger.info("✅ Ficha atualizada com sucesso: ID=${saved.idFicha}")
        return ResponseEntity.ok(saved)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        logger.info("🗑️  DELETE /api/fichas/$id - Deletando ficha")
        repo.deleteById(id)
        logger.info("✅ Ficha deletada com sucesso")
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/catequisando/{catequisandoId}")
    fun deleteByCatequisando(@PathVariable catequisandoId: Long): ResponseEntity<Void> {
        logger.info("🗑️  DELETE /api/fichas/catequisando/$catequisandoId - Deletando ficha por catequisando")
        val catequisando = cateqRepo.findById(catequisandoId)
            .orElseThrow { ResourceNotFoundException("Catequisando não encontrado") }
        repo.deleteByCatequisando(catequisando)
        logger.info("✅ Ficha(s) do catequisando deletada(s) com sucesso")
        return ResponseEntity.noContent().build()
    }
}
