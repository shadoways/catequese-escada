package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.ChecklistConhecimentoDTO
import com.catequese.catequeseapi.dto.RequisitoChecklistItemDTO
import com.catequese.catequeseapi.dto.RequisitoConhecimentoDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.RequisitoConhecimento
import com.catequese.catequeseapi.model.RequisitoConhecimentoMarcado
import com.catequese.catequeseapi.repository.RequisitoConhecimentoMarcadoRepository
import com.catequese.catequeseapi.repository.RequisitoConhecimentoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * O catálogo de conhecimentos exigidos do catequista (Kerigma, Credo, Pai
 * Nosso...) e a marca de quem já tem cada um -- tela-catequistas.md, aba
 * Conhecimentos.
 *
 * Separado de `CurriculoCatequistaService` de propósito: aquele lê formação e
 * presença (dado que já existia); este é dado NOVO, com um catálogo que o
 * coordenador paroquial mantém (regra pedida: "poder exigir mais ou menos
 * conhecimentos"). As duas abas moram na mesma tela, mas a pergunta que cada
 * uma responde é diferente.
 */
@Service
class RequisitoConhecimentoService(
    private val repo: RequisitoConhecimentoRepository,
    private val marcaRepo: RequisitoConhecimentoMarcadoRepository,
    private val escopo: EscopoAcessoService
) {
    private val log = LoggerFactory.getLogger(RequisitoConhecimentoService::class.java)

    /** Catálogo inteiro (ativos e inativos) -- para a tela de gestão em Configurações. */
    @Transactional(readOnly = true)
    fun listarTodos(): List<RequisitoConhecimentoDTO> =
        repo.findAll().sortedBy { it.idRequisito }.map { RequisitoConhecimentoDTO(it.idRequisito, it.nome, it.ativo) }

    @Transactional
    fun criar(nome: String, quem: String?): RequisitoConhecimentoDTO {
        val limpo = nome.trim()
        require(limpo.isNotEmpty()) { "O nome do conhecimento não pode ficar em branco." }
        require(!repo.existsByNomeIgnoreCaseAndAtivoTrue(limpo)) {
            "Já existe um conhecimento ativo chamado \"$limpo\" -- evite duplicidade."
        }
        val criado = repo.save(
            RequisitoConhecimento(nome = limpo, ativo = true, criadoPor = quem, criadoEm = LocalDateTime.now())
        )
        log.info("Conhecimento exigido '{}' criado por '{}'", limpo, quem ?: "?")
        return RequisitoConhecimentoDTO(criado.idRequisito, criado.nome, criado.ativo)
    }

    /**
     * Renomeia e/ou ativa/inativa. Inativar não apaga marcações -- só some do
     * checklist de quem ainda não tinha marcado (e do catálogo de quem passa
     * a se cadastrar); reativar traz o item, e o histórico junto, de volta.
     */
    @Transactional
    fun atualizar(id: Long, nome: String, ativo: Boolean, quem: String?): RequisitoConhecimentoDTO {
        val atual = repo.findById(id).orElseThrow { ResourceNotFoundException("Conhecimento não encontrado") }
        val limpo = nome.trim()
        require(limpo.isNotEmpty()) { "O nome do conhecimento não pode ficar em branco." }
        require(ativo || !repo.existsByNomeIgnoreCaseAndAtivoTrue(limpo) || atual.nome.equals(limpo, ignoreCase = true)) {
            "Já existe um conhecimento ativo chamado \"$limpo\" -- evite duplicidade."
        }
        val salvo = repo.save(
            atual.copy(nome = limpo, ativo = ativo, atualizadoPor = quem, atualizadoEm = LocalDateTime.now())
        )
        log.info(
            "Conhecimento exigido #{} ({}) {} por '{}'",
            id, limpo, if (ativo) "reativado/atualizado" else "inativado", quem ?: "?"
        )
        return RequisitoConhecimentoDTO(salvo.idRequisito, salvo.nome, salvo.ativo)
    }

    /**
     * O checklist de um catequista: todo requisito ATIVO, com `possui`
     * resolvido pela última marca dele (ausência de marca = falso, nunca
     * marcado ainda). `podeEditar` vem pronto daqui -- a tela só desenha o
     * checkbox habilitado ou não, sem reconstruir a regra de permissão.
     */
    @Transactional(readOnly = true)
    fun checklistDoCatequista(idCatequista: Long): ChecklistConhecimentoDTO {
        if (!escopo.podeVerCatequista(idCatequista)) {
            throw AcessoNegadoException("Você só pode consultar o próprio checklist de conhecimentos.")
        }
        val marcas = marcaRepo.findByIdCatequista(idCatequista).associateBy { it.idRequisito }
        val itens = repo.findByAtivoTrue().sortedBy { it.idRequisito }.map { requisito ->
            RequisitoChecklistItemDTO(
                idRequisito = requisito.idRequisito,
                nome = requisito.nome,
                possui = marcas[requisito.idRequisito]?.possui == true
            )
        }
        return ChecklistConhecimentoDTO(podeEditar = escopo.ehAdmin(), itens = itens)
    }

    /**
     * Marca ou desmarca um conhecimento. A rota já é restrita ao coordenador
     * paroquial em `SecurityConfig` (mesmo padrão já usado para alterar a
     * configuração de formação); `podeVerCatequista` aqui é sobre RECORTE de
     * catequista, não sobre papel -- mesma dupla checagem que
     * `CurriculoCatequistaService.detalhe` já faz.
     */
    @Transactional
    fun marcar(idCatequista: Long, idRequisito: Long, possui: Boolean, quem: String?) {
        if (!escopo.podeVerCatequista(idCatequista)) {
            throw AcessoNegadoException("Catequista fora do seu recorte.")
        }
        if (!repo.existsById(idRequisito)) {
            throw ResourceNotFoundException("Conhecimento não encontrado")
        }
        val agora = LocalDateTime.now()
        val existente = marcaRepo.findByIdCatequistaAndIdRequisito(idCatequista, idRequisito)
        val linha = existente?.copy(possui = possui, marcadoPor = quem, marcadoEm = agora)
            ?: RequisitoConhecimentoMarcado(
                idRequisito = idRequisito, idCatequista = idCatequista,
                possui = possui, marcadoPor = quem, marcadoEm = agora
            )
        marcaRepo.save(linha)
        log.info(
            "Conhecimento #{} do catequista #{} marcado como {} por '{}'",
            idRequisito, idCatequista, if (possui) "POSSUI" else "NÃO POSSUI", quem ?: "?"
        )
    }
}
