package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.service.CatequisandoService
import jakarta.validation.Valid
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
@RequestMapping("/api/catequisandos")
class CatequisandoController(private val service: CatequisandoService) {

    companion object {
        private val logger = LoggerFactory.getLogger(CatequisandoController::class.java)
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<Catequisando>> {
        logger.info("🔍 GET /api/catequisandos - Listando todos os catequisandos")
        val result = service.findAll()
        logger.info("✅ Encontrados ${result.size} catequisandos")
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Catequisando> {
        logger.info("🔍 GET /api/catequisandos/$id - Buscando catequisando por ID")
        val result = service.findById(id)
        logger.info("✅ Catequisando encontrado: ${result.nome}")
        return ResponseEntity.ok(result)
    }

    @PostMapping
    fun create(@Valid @RequestBody catequisando: Catequisando): ResponseEntity<Catequisando> {
        logger.info("📝 POST /api/catequisandos - Criando novo catequisando")
        logger.info("📥 Dados recebidos: $catequisando")
        logger.debug("   - Nome: ${catequisando.nome}")
        logger.debug("   - Email: ${catequisando.email}")
        logger.debug("   - Telefone: ${catequisando.telefone}")
        logger.debug("   - Endereco: ${catequisando.endereco}")
        logger.debug("   - Numero Documento: ${catequisando.numeroDocumento}")
        logger.debug("   - Tipo Documento: ${catequisando.tipoDocumento}")
        logger.debug("   - Estado Conjugal: ${catequisando.estadoConjugal}")
        logger.debug("   - Foi Batizado: ${catequisando.foiBatizado}")
        logger.debug("   - Fez Primeira Eucaristia: ${catequisando.fezPrimeiraEucaristia}")
        logger.debug("   - Intolerante Gluten: ${catequisando.intoleranteGluten}")
        logger.debug("   - Turma ID: ${catequisando.turma?.idTurma}")
        logger.debug("   - Comunidade ID: ${catequisando.comunidade?.idComunidade}")

        val saved = service.create(catequisando)
        logger.info("✅ Catequisando criado com ID: ${saved.idCatequisando}")
        return ResponseEntity.created(URI("/api/catequisandos/${saved.idCatequisando}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody catequisando: Catequisando): ResponseEntity<Catequisando> {
        logger.info("🔄 PUT /api/catequisandos/$id - Atualizando catequisando")
        logger.info("📥 Dados recebidos: $catequisando")
        val result = service.update(id, catequisando)
        logger.info("✅ Catequisando atualizado com sucesso")
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.remove(id)
        return ResponseEntity.noContent().build()
    }
}
