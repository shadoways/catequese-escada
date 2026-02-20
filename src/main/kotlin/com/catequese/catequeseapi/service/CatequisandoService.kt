package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.repository.CatequisandoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CatequisandoService(private val repo: CatequisandoRepository) {

    companion object {
        private val logger = LoggerFactory.getLogger(CatequisandoService::class.java)
    }

    fun findAll(): List<Catequisando>{
        logger.debug("🔍 Service: Buscando todos os catequisandos")
        val result = repo.findAll()
        logger.debug("✅ Service: Encontrados ${result.size} catequisandos")
        return result
    }

    fun findById(id: Long): Catequisando{
        logger.debug("🔍 Service: Buscando catequisando ID=$id")
        val result = repo.findById(id).orElseThrow {
            logger.error("❌ Service: Catequisando ID=$id não encontrado")
            ResourceNotFoundException("Catequisando não encontrado")
        }
        logger.debug("✅ Service: Catequisando encontrado: ${result.nome}")
        return result
    }

    fun create(catequisando: Catequisando): Catequisando{
        logger.debug("💾 Service: Salvando catequisando no banco: ${catequisando.nome}")
        val saved = repo.save(catequisando)
        logger.info("✅ Service: Catequisando salvo com ID=${saved.idCatequisando}")
        return saved
    }

    fun update(id: Long, catequisando: Catequisando): Catequisando{
        logger.debug("🔄 Service: Atualizando catequisando ID=$id")
        val existing = repo.findById(id).orElseThrow {
            logger.error("❌ Service: Catequisando ID=$id não encontrado para atualização")
            ResourceNotFoundException("Catequisando não encontrado")
        }
        val updated = existing.copy(
            nome = catequisando.nome,
            telefone = catequisando.telefone,
            email = catequisando.email,
            dataNascimento = catequisando.dataNascimento,
            nomeResponsavel = catequisando.nomeResponsavel,
            telefoneResponsavel = catequisando.telefoneResponsavel,
            endereco = catequisando.endereco,
            numeroDocumento = catequisando.numeroDocumento,
            tipoDocumento = catequisando.tipoDocumento,
            intoleranteGluten = catequisando.intoleranteGluten,
            foiBatizado = catequisando.foiBatizado,
            fezPrimeiraEucaristia = catequisando.fezPrimeiraEucaristia,
            estadoConjugal = catequisando.estadoConjugal,
            ativo = catequisando.ativo,
            turma = catequisando.turma,
            comunidade = catequisando.comunidade
        )
        val result = repo.save(updated)
        logger.info("✅ Service: Catequisando ID=$id atualizado com sucesso")
        return result
    }

    fun remove(id: Long){
        logger.debug("🗑️  Service: Deletando catequisando ID=$id")
        repo.deleteById(id)
        logger.info("✅ Service: Catequisando ID=$id deletado com sucesso")
    }
}