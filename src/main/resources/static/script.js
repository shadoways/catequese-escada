const result = document.getElementById("result");
const turmasById = {};
const comunidadesById = {};
let selectedFiles = {
  DOCUMENTO: null,
  CERTIDAO: null,
  FOTO: null
}; // Armazena os arquivos selecionados por tipo

const setResult = (lines, type = "") => {
  result.innerHTML = "";
  if (!lines.length) return;
  const status = document.createElement("div");
  status.className = `status ${type}`.trim();
  status.textContent = lines[0];
  result.appendChild(status);
  lines.slice(1).forEach((line) => {
    const p = document.createElement("div");
    p.textContent = line;
    result.appendChild(p);
  });
};

  // Atualiza a lista visual de anexos
  const updateFilesList = () => {
    const listContainer = document.getElementById('anexos-list');
    const filesContainer = document.getElementById('anexos-container');

    const hasFiles = Object.values(selectedFiles).some(f => f !== null);

    if (!hasFiles) {
      listContainer.style.display = 'none';
      filesContainer.innerHTML = '';
      return;
    }

    listContainer.style.display = 'block';
    filesContainer.innerHTML = '';

    // Mapear tipos com labels amigáveis
    const typeLabels = {
      DOCUMENTO: 'Documento',
      CERTIDAO: 'Certidão de Batismo',
      FOTO: 'Foto do Catequisando'
    };

    Object.entries(selectedFiles).forEach(([fileType, file]) => {
      if (!file) return;

      const fileItem = document.createElement('div');
      fileItem.style.display = 'flex';
      fileItem.style.justifyContent = 'space-between';
      fileItem.style.alignItems = 'center';
      fileItem.style.padding = '8px 12px';
      fileItem.style.backgroundColor = 'rgba(47, 111, 126, 0.08)';
      fileItem.style.borderRadius = '8px';
      fileItem.style.fontSize = '0.9rem';

      const fileInfo = document.createElement('div');
      fileInfo.style.display = 'flex';
      fileInfo.style.flexDirection = 'column';
      fileInfo.style.flex = '1';

      const fileTypeLabel = document.createElement('span');
      fileTypeLabel.style.fontWeight = 'bold';
      fileTypeLabel.style.color = 'var(--accent-2)';
      fileTypeLabel.textContent = typeLabels[fileType];
      fileInfo.appendChild(fileTypeLabel);

      const fileName = document.createElement('span');
      fileName.style.fontSize = '0.85rem';
      fileName.style.color = 'var(--muted)';
      fileName.textContent = file.name;
      fileInfo.appendChild(fileName);

      fileItem.appendChild(fileInfo);

      const removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'secondary';
      removeBtn.textContent = 'Remover';
      removeBtn.style.padding = '4px 10px';
      removeBtn.style.fontSize = '0.85rem';
      removeBtn.addEventListener('click', (e) => {
        e.preventDefault();
        selectedFiles[fileType] = null;
        // Limpar o input também
        const inputId = 'arquivo-' + fileType.toLowerCase();
        const input = document.getElementById(inputId);
        if (input) input.value = '';
        updateFilesList();
        updateFileInputLabels(); // Atualizar label após remover
      });
      fileItem.appendChild(removeBtn);

      filesContainer.appendChild(fileItem);
    });
  };

  // Atualiza os labels dos inputs de arquivo
  const updateFileInputLabels = () => {
    const configs = [
      { inputId: 'arquivo-documento', type: 'DOCUMENTO', label: 'Documento (RG, CPF, etc.)' },
      { inputId: 'arquivo-certidao', type: 'CERTIDAO', label: 'Certidão de Batismo' },
      { inputId: 'arquivo-foto', type: 'FOTO', label: 'Foto do Catequisando' }
    ];

    configs.forEach(({ inputId, type, label }) => {
      const input = document.getElementById(inputId);
      if (!input) return;

      const file = selectedFiles[type];
      const parent = input.parentElement;

      // Encontrar o elemento que contém o texto "Nenhum arquivo escolhido"
      let textNode = null;
      for (let node of parent.childNodes) {
        if (node.nodeType === Node.TEXT_NODE && node.textContent.includes('Nenhum arquivo escolhido')) {
          textNode = node;
          break;
        }
      }

      if (file) {
        // Se tem arquivo, substituir o texto pelo nome do arquivo com ✅
        if (textNode) {
          textNode.textContent = ` ${file.name} ✅`;
        }
      } else {
        // Se não tem arquivo, restaurar o texto original
        if (textNode) {
          textNode.textContent = ' Nenhum arquivo escolhido';
        }
      }
    });
  };

// limpa classes de campo inválido (global)
const clearInvalids = () => {
  document.querySelectorAll('.invalid').forEach(el => el.classList.remove('invalid'));
};

// Reseta o formulário e reabilita o botão para novo cadastro
const resetFormAndEnable = () => {
  // se houver contador pendente, cancela
  if (window.__autoResetInterval) {
    clearInterval(window.__autoResetInterval);
    window.__autoResetInterval = null;
  }
  const existingCountdown = document.getElementById('auto-reset-countdown');
  if (existingCountdown) existingCountdown.remove();

  // limpar inputs/textareas
  [
    'nome','telefone','email','data-nascimento','nome-responsavel','telefone-responsavel',
    'endereco','numero-documento','data-inscricao','observacoes'
  ].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
      if (el.type === 'checkbox' || el.type === 'radio') el.checked = false; else el.value = '';
    }
  });

  // selects
  const turma = document.getElementById('turma-select'); if (turma) turma.selectedIndex = 0;
  const comunidade = document.getElementById('comunidade-select'); if (comunidade) comunidade.selectedIndex = 0;

  // radios
  document.querySelectorAll('input[name="tipo-documento"]').forEach(r => r.checked = false);
  document.querySelectorAll('input[name="estado-conjugal"]').forEach(r => r.checked = false);

  // checkboxes
  ['intolerante','batizado','primeira-eucaristia'].forEach(id => { const c = document.getElementById(id); if (c) c.checked = false; });

      // arquivos
      const arquivos = ['arquivo-documento', 'arquivo-certidao', 'arquivo-foto'];
      arquivos.forEach(id => {
        const input = document.getElementById(id);
        if (input) input.value = null;
      });
      selectedFiles = { DOCUMENTO: null, CERTIDAO: null, FOTO: null };
      updateFilesList();
      updateFileInputLabels(); // Restaurar labels originais

  // atualizar data de inscrição com a data atual
  const today = new Date();
  const todayStr = today.toISOString().split('T')[0]; // YYYY-MM-DD
  const dataInscricaoInput = document.getElementById('data-inscricao');

  if (dataInscricaoInput) {
    // Armazenar a data ISO em um atributo data
    dataInscricaoInput.dataset.isoDate = todayStr;
    // Exibir a data formatada
    dataInscricaoInput.value = formatDatePortuguese(todayStr);
  }

  // limpar canvas
  try { ctx.clearRect(0, 0, canvas.width, canvas.height); } catch (e) {}
  hasSignature = false;

  // desabilitar campo de documento e restaurar hint
  const numeroDocInput = document.getElementById('numero-documento');
  if (numeroDocInput) {
    numeroDocInput.disabled = true;
    numeroDocInput.placeholder = '';
  }
  const docHint = document.getElementById('doc-hint');
  if (docHint) docHint.innerHTML = '<strong>Selecione primeiro o tipo do documento.</strong>';

  // limpar marcações
  clearInvalids();

  // reabilitar botão
  submitted = false;
  try { submitBtn.disabled = false; } catch (e) {}
  try { submitBtn.textContent = 'Cadastrar ficha'; } catch (e) {}
  // limpar mensagens
  setResult([]);
};

// Mostrar botão para novo cadastro após sucesso
const showNewRegistrationButton = () => {
  const container = document.getElementById('result') || document.body;
  const wrapper = document.createElement('div');
  wrapper.id = 'auto-reset-countdown';
  wrapper.style.marginTop = '8px';
  wrapper.style.display = 'flex';
  wrapper.style.gap = '10px';
  wrapper.style.alignItems = 'center';

  const btnNow = document.createElement('button');
  btnNow.className = 'secondary';
  btnNow.textContent = 'Novo cadastro';

  wrapper.appendChild(btnNow);
  container.appendChild(wrapper);

  btnNow.addEventListener('click', () => {
    wrapper.remove();
    resetFormAndEnable();
    // Focar no campo nome
    setTimeout(() => {
      const nomeField = document.getElementById('nome');
      if (nomeField) nomeField.focus();
    }, 100);
  });
};

// Validação de email
const isValidEmail = (email) => {
  const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return regex.test(email);
};

// Validação de RG (formato simples: 5-12 dígitos)
const isValidRG = (rg) => {
  const cleaned = rg.replace(/\D/g, '');
  return cleaned.length >= 5 && cleaned.length <= 12;
};

// Validação de CPF
const isValidCPF = (cpf) => {
  const cleaned = cpf.replace(/\D/g, '');
  if (cleaned.length !== 11) return false;

  // Rejeitar CPFs com todos os dígitos iguais
  if (/^(\d)\1{10}$/.test(cleaned)) return false;

  // Validar primeiro dígito verificador
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    sum += parseInt(cleaned[i]) * (10 - i);
  }
  let remainder = sum % 11;
  const digit1 = remainder < 2 ? 0 : 11 - remainder;
  if (parseInt(cleaned[9]) !== digit1) return false;

  // Validar segundo dígito verificador
  sum = 0;
  for (let i = 0; i < 10; i++) {
    sum += parseInt(cleaned[i]) * (11 - i);
  }
  remainder = sum % 11;
  const digit2 = remainder < 2 ? 0 : 11 - remainder;
  if (parseInt(cleaned[10]) !== digit2) return false;

  return true;
};

// Auto-formatar telefone: (XX) 9XXXX-XXXX ou (XX) XXXX-XXXX
const formatTelefone = (value) => {
  const cleaned = value.replace(/\D/g, '');
  if (cleaned.length <= 2) return cleaned;
  if (cleaned.length <= 7) return `(${cleaned.slice(0, 2)}) ${cleaned.slice(2)}`;
  return `(${cleaned.slice(0, 2)}) ${cleaned.slice(2, 7)}-${cleaned.slice(7, 11)}`;
};

// Auto-formatar RG: XX.XXX.XXX-X
const formatRG = (value) => {
  const cleaned = value.replace(/\D/g, '');
  if (cleaned.length <= 2) return cleaned;
  if (cleaned.length <= 5) return `${cleaned.slice(0, 2)}.${cleaned.slice(2)}`;
  if (cleaned.length <= 8) return `${cleaned.slice(0, 2)}.${cleaned.slice(2, 5)}.${cleaned.slice(5)}`;
  return `${cleaned.slice(0, 2)}.${cleaned.slice(2, 5)}.${cleaned.slice(5, 8)}-${cleaned.slice(8, 9)}`;
};

// Auto-formatar CPF: XXX.XXX.XXX-XX
const formatCPF = (value) => {
  const cleaned = value.replace(/\D/g, '');
  if (cleaned.length <= 3) return cleaned;
  if (cleaned.length <= 6) return `${cleaned.slice(0, 3)}.${cleaned.slice(3)}`;
  if (cleaned.length <= 9) return `${cleaned.slice(0, 3)}.${cleaned.slice(3, 6)}.${cleaned.slice(6)}`;
  return `${cleaned.slice(0, 3)}.${cleaned.slice(3, 6)}.${cleaned.slice(6, 9)}-${cleaned.slice(9, 11)}`;
};

// Validação de data (YYYY-MM-DD)
const isValidDate = (dateStr) => {
  if (!dateStr) return false;
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return false;
  // Verificar se a data não é no futuro
  if (date > new Date()) return false;
  return true;
};

// Normaliza data para null se vazia
const normalizeDate = (value) => value || null;

// Formata data em português para exibição (Barueri dia XX de mês de ano)
const formatDatePortuguese = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr + 'T00:00:00');
  const day = date.getDate();
  const months = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
                  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
  const month = months[date.getMonth()];
  const year = date.getFullYear();
  return `Barueri ${day} de ${month} de ${year}`;
};

const fetchJson = async (url, options = {}) => {
  const res = await fetch(url, options);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status} ${text || res.statusText}`);
  }
  if (res.status === 204) return null;
  return res.json();
};


const canvas = document.getElementById("signature-pad");
const ctx = canvas.getContext("2d");
ctx.lineWidth = 2.5;
ctx.lineCap = "round";
ctx.strokeStyle = "#1b1a16";

let drawing = false;
let hasSignature = false;
const submitBtn = document.getElementById("btn-submit");
let submitted = false; // quando true, bloqueia novos envios até reload

const getCanvasPos = (event) => {
  const rect = canvas.getBoundingClientRect();
  const clientX = event.touches ? event.touches[0].clientX : event.clientX;
  const clientY = event.touches ? event.touches[0].clientY : event.clientY;
  return {
    x: (clientX - rect.left) * (canvas.width / rect.width),
    y: (clientY - rect.top) * (canvas.height / rect.height)
  };
};

const startDraw = (event) => {
  drawing = true;
  hasSignature = true;
  const pos = getCanvasPos(event);
  ctx.beginPath();
  ctx.moveTo(pos.x, pos.y);
};

const draw = (event) => {
  if (!drawing) return;
  const pos = getCanvasPos(event);
  ctx.lineTo(pos.x, pos.y);
  ctx.stroke();
};

const endDraw = () => {
  drawing = false;
  ctx.closePath();
};

canvas.addEventListener("pointerdown", startDraw);
canvas.addEventListener("pointermove", draw);
canvas.addEventListener("pointerup", endDraw);
canvas.addEventListener("pointerleave", endDraw);

document.getElementById("btn-clear").addEventListener("click", () => {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  hasSignature = false;
});

const uploadFile = async (file, fileType = "ANEXO") => {
  const form = new FormData();
  form.append("file", file);
  form.append("fileType", fileType); // Adiciona o identificador do tipo de arquivo
  const res = await fetch("/api/files", {
    method: "POST",
    body: form
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status} ${text || res.statusText}`);
  }
  return res.json();
};

const createDocumento = async (payload) => fetchJson("/api/documentos", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(payload)
});

const loadTurmas = async () => {
  const select = document.getElementById("turma-select");
  select.innerHTML = "<option value=\"\">Selecione uma turma</option>";
  const turmas = await fetchJson("/api/turmas");
  turmas.forEach((turma) => {
    turmasById[turma.idTurma] = turma;
    const option = document.createElement("option");
    option.value = turma.idTurma;
    option.textContent = `${turma.nome}${turma.ano ? " (" + turma.ano + ")" : ""}`;
    select.appendChild(option);
  });
};

const loadComunidades = async () => {
  const select = document.getElementById("comunidade-select");
  select.innerHTML = "<option value=\"\">Selecione uma comunidade</option>";
  const comunidades = await fetchJson("/api/comunidades");
  comunidades.forEach((comunidade) => {
    comunidadesById[comunidade.idComunidade] = comunidade;
    const option = document.createElement("option");
    option.value = comunidade.idComunidade;
    option.textContent = comunidade.nome;
    select.appendChild(option);
  });
};

submitBtn.addEventListener("click", async () => {
  // Se já estamos enviando, ignorar cliques repetidos
  if (submitted) return;

  // limpa marcações de validação anteriores
  clearInvalids();

  let firstInvalidEl = null;

  const nomeEl = document.getElementById("nome");
  const telefoneEl = document.getElementById("telefone");
  const emailEl = document.getElementById("email");
  const dataNascimentoEl = document.getElementById("data-nascimento");
  const nomeResponsavelEl = document.getElementById("nome-responsavel");
  const telefoneResponsavelEl = document.getElementById("telefone-responsavel");
  const enderecoEl = document.getElementById("endereco");
  const turmaSelectEl = document.getElementById("turma-select");
  const comunidadeSelectEl = document.getElementById("comunidade-select");
  const numeroDocumentoEl = document.getElementById("numero-documento");
  const dataInscricaoInput = document.getElementById("data-inscricao");

  if (!nomeEl || !telefoneEl || !emailEl || !dataNascimentoEl || !nomeResponsavelEl ||
      !telefoneResponsavelEl || !enderecoEl || !turmaSelectEl || !comunidadeSelectEl ||
      !numeroDocumentoEl || !dataInscricaoInput) {
    setResult(["Erro: Formulário não foi carregado corretamente. Recarregue a página."], "error");
    return;
  }

  const nome = nomeEl.value.trim();
  const telefone = telefoneEl.value.trim();
  const email = emailEl.value.trim();
  const dataNascimento = dataNascimentoEl.value;
  const nomeResponsavel = nomeResponsavelEl.value.trim();
  const telefoneResponsavel = telefoneResponsavelEl.value.trim();
  const endereco = enderecoEl.value.trim();
  const turmaId = turmaSelectEl.value;
  const comunidadeId = comunidadeSelectEl.value;
  const numeroDocumento = numeroDocumentoEl.value.trim();
  const tipoDocumentoSelecionado = document.querySelector('input[name="tipo-documento"]:checked');
  const estadoConjugalSelecionado = document.querySelector('input[name="estado-conjugal"]:checked');
  const dataInscricaoVal = dataInscricaoInput.dataset.isoDate;

  const missing = [];
  if (!nome) missing.push("Nome");
  if (!nome && !firstInvalidEl) { firstInvalidEl = document.getElementById('nome'); document.getElementById('nome').classList.add('invalid'); }
  if (!telefone) missing.push("Telefone");
  if (!telefone && !firstInvalidEl) { firstInvalidEl = document.getElementById('telefone'); document.getElementById('telefone').classList.add('invalid'); }
  if (!email) missing.push("Email");
  if (!email && !firstInvalidEl) { firstInvalidEl = document.getElementById('email'); document.getElementById('email').classList.add('invalid'); }
  if (!isValidEmail(email)) { missing.push("Email (formato inválido)"); if (!firstInvalidEl) { firstInvalidEl = document.getElementById('email'); document.getElementById('email').classList.add('invalid'); } }
  if (!dataNascimento) missing.push("Data de nascimento");
  if (!dataNascimento && !firstInvalidEl) { firstInvalidEl = document.getElementById('data-nascimento'); document.getElementById('data-nascimento').classList.add('invalid'); }
  if (dataNascimento && !isValidDate(dataNascimento)) { missing.push("Data de nascimento (inválida)"); if (!firstInvalidEl) { firstInvalidEl = document.getElementById('data-nascimento'); document.getElementById('data-nascimento').classList.add('invalid'); } }
  if (!nomeResponsavel) missing.push("Nome do responsável");
  if (!nomeResponsavel && !firstInvalidEl) { firstInvalidEl = document.getElementById('nome-responsavel'); document.getElementById('nome-responsavel').classList.add('invalid'); }
  if (!telefoneResponsavel) missing.push("Telefone do responsável");
  if (!telefoneResponsavel && !firstInvalidEl) { firstInvalidEl = document.getElementById('telefone-responsavel'); document.getElementById('telefone-responsavel').classList.add('invalid'); }
  if (!endereco) missing.push("Endereço");
  if (!endereco && !firstInvalidEl) { firstInvalidEl = document.getElementById('endereco'); document.getElementById('endereco').classList.add('invalid'); }
  if (!turmaId) missing.push("Turma");
  if (!turmaId && !firstInvalidEl) { firstInvalidEl = document.getElementById('turma-select'); document.getElementById('turma-select').classList.add('invalid'); }
  if (!comunidadeId) missing.push("Comunidade");
  if (!comunidadeId && !firstInvalidEl) { firstInvalidEl = document.getElementById('comunidade-select'); document.getElementById('comunidade-select').classList.add('invalid'); }
  if (!numeroDocumento) missing.push("Número do documento");
  if (!numeroDocumento && !firstInvalidEl) { firstInvalidEl = document.getElementById('numero-documento'); document.getElementById('numero-documento').classList.add('invalid'); }

  // Validar formato do documento se tipo foi selecionado
  if (tipoDocumentoSelecionado && numeroDocumento) {
    const tipoDoc = tipoDocumentoSelecionado.value;
    if (tipoDoc === "RG" && !isValidRG(numeroDocumento)) {
      missing.push("RG (formato inválido)");
      if (!firstInvalidEl) { firstInvalidEl = document.getElementById('numero-documento'); document.getElementById('numero-documento').classList.add('invalid'); }
    } else if (tipoDoc === "CPF" && !isValidCPF(numeroDocumento)) {
      missing.push("CPF (formato inválido)");
      if (!firstInvalidEl) { firstInvalidEl = document.getElementById('numero-documento'); document.getElementById('numero-documento').classList.add('invalid'); }
    }
  }

  if (!tipoDocumentoSelecionado) missing.push("Tipo de documento");
  if (!tipoDocumentoSelecionado && !firstInvalidEl) { const el = document.querySelector('input[name="tipo-documento"]'); if (el) { firstInvalidEl = el; el.classList.add('invalid'); } }
  if (!estadoConjugalSelecionado) missing.push("Estado de convivência conjugal");
  if (!estadoConjugalSelecionado && !firstInvalidEl) { const el = document.querySelector('input[name="estado-conjugal"]'); if (el) { firstInvalidEl = el; el.classList.add('invalid'); } }
  if (!dataInscricaoVal) missing.push("Data de inscrição");
  if (!dataInscricaoVal && !firstInvalidEl) { firstInvalidEl = document.getElementById('data-inscricao'); document.getElementById('data-inscricao').classList.add('invalid'); }
  if (dataInscricaoVal && !isValidDate(dataInscricaoVal)) { missing.push("Data de inscrição (inválida)"); if (!firstInvalidEl) { firstInvalidEl = document.getElementById('data-inscricao'); document.getElementById('data-inscricao').classList.add('invalid'); } }
  if (!hasSignature) missing.push("Assinatura digital");
  if (!hasSignature && !firstInvalidEl) { firstInvalidEl = document.getElementById('signature-pad'); document.getElementById('signature-pad').classList.add('invalid'); }

  if (missing.length) {
    setResult([`Preencha os campos obrigatórios: ${missing.join(', ')}`], "error");
    if (firstInvalidEl) {
      try {
        firstInvalidEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
        firstInvalidEl.focus({ preventScroll: true });
      } catch (e) {
        try { firstInvalidEl.focus(); } catch (e2) {}
      }
    }
    // Não iniciamos envio — permitir que o usuário corrija e clique novamente
    // Caso o botão tenha sido desabilitado por alguma versão anterior, reabilita
    submitted = false;
    try { submitBtn.disabled = false; } catch (e) {}
    try { submitBtn.textContent = "Cadastrar ficha"; } catch (e) {}
    return;
  }

  // Todos os campos validados — iniciar envio
  submitted = true;
  submitBtn.disabled = true;

  // Criar barra de progresso
  const progressBar = document.createElement('div');
  progressBar.id = 'upload-progress';
  progressBar.style.cssText = 'width: 0%; height: 4px; background: var(--accent-2); transition: width 0.5s; margin-top: 8px; border-radius: 2px;';
  submitBtn.parentElement.insertBefore(progressBar, submitBtn.nextSibling);

  // Iniciar contador regressivo
  let countdown = 60;
  let progressPercent = 0;
  submitBtn.textContent = `Enviando... (${countdown}s)`;
  progressBar.style.width = '0%';

  const countdownInterval = setInterval(() => {
    countdown--;
    progressPercent += (100 / 60); // Incrementa ~1.67% por segundo

    if (countdown > 0) {
      submitBtn.textContent = `Enviando... (${countdown}s)`;
      progressBar.style.width = `${Math.min(progressPercent, 95)}%`; // Máximo 95% até completar
    } else {
      clearInterval(countdownInterval);
      submitBtn.textContent = "Enviando...";
      progressBar.style.width = '95%';
    }
  }, 1000);

  // limpar mensagens anteriores
  setResult([]);

  try {
    const catequisandoPayload = {
      nome,
      telefone: telefone || null,
      email: email || null,
      dataNascimento: normalizeDate(dataNascimento),
      nomeResponsavel,
      telefoneResponsavel,
      endereco,
      numeroDocumento,
      tipoDocumento: tipoDocumentoSelecionado.value,
      intoleranteGluten: document.getElementById("intolerante").checked,
      foiBatizado: document.getElementById("batizado").checked,
      fezPrimeiraEucaristia: document.getElementById("primeira-eucaristia").checked,
      estadoConjugal: estadoConjugalSelecionado.value,
      ativo: true
    };

    const fichaPayload = {
      dataInscricao: normalizeDate(dataInscricaoVal),
      observacoes: document.getElementById("observacoes").value.trim() || null
    };

    // montar turma e comunidade como antes
    if (turmaId) {
      const turma = turmasById[turmaId] || await fetchJson(`/api/turmas/${Number(turmaId)}`);
      catequisandoPayload.turma = turma;
    }

    if (comunidadeId) {
      const comunidade = comunidadesById[comunidadeId] || await fetchJson(`/api/comunidades/${Number(comunidadeId)}`);
      catequisandoPayload.comunidade = comunidade;
    }

    const catequisando = await fetchJson("/api/catequisandos", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(catequisandoPayload)
    });

    // Enviar catequisandoId no payload da ficha (novo DTO)
    fichaPayload.catequisandoId = catequisando.idCatequisando;
    await fetchJson("/api/fichas", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(fichaPayload)
    });

        const today = new Date().toISOString().slice(0, 10);
        const uploadedDocIds = []; // Rastrear IDs para rollback

        try {
          // UPLOADS SEQUENCIAIS para garantir atomicidade
          const fileTypes = ['DOCUMENTO', 'CERTIDAO', 'FOTO'];

          for (const fileType of fileTypes) {
            if (selectedFiles[fileType]) {
              const file = selectedFiles[fileType];
              console.log(`📤 Iniciando upload: ${fileType}`);

              // 1. Upload para GCS primeiro
              const upload = await uploadFile(file, fileType);

              // 2. Validar que arquivo foi salvo
              if (!upload || !upload.filename) {
                throw new Error(`${fileType}: Arquivo não foi salvo no GCS`);
              }
              console.log(`✅ ${fileType} salvo no GCS: ${upload.filename}`);

              // 3. SÓ DEPOIS criar documento no banco
              const docResponse = await createDocumento({
                tipoDocumento: fileType,
                caminhoArquivo: upload.path || upload.filename,
                dataEnvio: today,
                catequisandoId: catequisando.idCatequisando,
                tipoStatus: 'ENVIADO'
              });

              uploadedDocIds.push(docResponse.idDocumento);
              console.log(`✅ ${fileType} criado no banco: ID ${docResponse.idDocumento}`);
            }
          }

          // ASSINATURA (sequencial também)
          if (hasSignature) {
            console.log(`📤 Iniciando upload: ASSINATURA`);

            const dataUrl = canvas.toDataURL("image/png");
            const byteString = atob(dataUrl.split(",")[1]);
            const buffer = new Uint8Array(byteString.length);
            for (let i = 0; i < byteString.length; i += 1) {
              buffer[i] = byteString.charCodeAt(i);
            }
            const signatureFile = new File([buffer], `assinatura-${Date.now()}.png`, { type: "image/png" });

            // 1. Upload para GCS
            const upload = await uploadFile(signatureFile, "ASSINATURA");

            // 2. Validar
            if (!upload || !upload.filename) {
              throw new Error("ASSINATURA: Arquivo não foi salvo no GCS");
            }
            console.log(`✅ ASSINATURA salvo no GCS: ${upload.filename}`);

            // 3. Criar no banco
            const docResponse = await createDocumento({
              tipoDocumento: "ASSINATURA",
              caminhoArquivo: upload.path || upload.filename,
              dataEnvio: today,
              catequisandoId: catequisando.idCatequisando,
              tipoStatus: 'ENVIADO'
            });

            uploadedDocIds.push(docResponse.idDocumento);
            console.log(`✅ ASSINATURA criada no banco: ID ${docResponse.idDocumento}`);
          }

        } catch (uploadError) {
          // ❌ ROLLBACK ROBUSTO
          console.error("❌ ERRO NO UPLOAD! Iniciando rollback...", uploadError);

          // Deletar documentos em ordem INVERSA (mais seguro, evita órfãos)
          console.log(`🗑️ Deletando ${uploadedDocIds.length} documento(s) criado(s)...`);
          for (const docId of uploadedDocIds.reverse()) {
            try {
              await fetch(`/api/documentos/${docId}`, { method: 'DELETE' });
              console.log(`✅ Documento ${docId} deletado`);
            } catch (delErr) {
              console.error(`⚠️ Erro ao deletar documento ${docId}:`, delErr.message);
              // Continua mesmo se um delete falhar
            }
          }

          // Deletar ficha
          try {
            console.log(`🗑️ Deletando ficha do catequisando ${catequisando.idCatequisando}...`);
            await fetch(`/api/fichas/catequisando/${catequisando.idCatequisando}`, { method: 'DELETE' });
            console.log(`✅ Ficha deletada`);
          } catch (fichaErr) {
            console.error(`⚠️ Erro ao deletar ficha:`, fichaErr.message);
          }

          // Deletar catequisando (último)
          try {
            console.log(`🗑️ Deletando catequisando ${catequisando.idCatequisando}...`);
            await fetch(`/api/catequisandos/${catequisando.idCatequisando}`, { method: 'DELETE' });
            console.log(`✅ Catequisando deletado`);
          } catch (cateqErr) {
            console.error(`⚠️ Erro ao deletar catequisando:`, cateqErr.message);
          }

          console.log(`🔄 Rollback concluído. Relançando erro...`);
          throw new Error(`Falha no upload: ${uploadError.message}. Todos os dados foram revertidos automaticamente.`);
        }

    const successMessages = ["Cadastro realizado com sucesso"];

    // Adicionar aviso se alguns arquivos falharam
    if (uploadErrors.length > 0 && successCount > 0) {
      successMessages.push(`⚠️ Atenção: ${uploadErrors.length} arquivo(s) não puderam ser enviados`);
      uploadErrors.forEach(e => {
        successMessages.push(`  • ${e.type}: ${e.error}`);
      });
    }

    setResult(successMessages, uploadErrors.length > 0 ? "warning" : "ok");

    // Limpar contador e completar barra
    clearInterval(countdownInterval);
    progressBar.style.width = '100%';
    setTimeout(() => {
      progressBar.remove();
    }, 500);

    // mostrar botão para novo cadastro
    showNewRegistrationButton();
  } catch (err) {
    // Limpar contador em caso de erro
    clearInterval(countdownInterval);
    if (progressBar && progressBar.parentElement) {
      progressBar.remove();
    }

    // Em caso de erro no envio (rede/servidor), reabilitar para permitir nova tentativa
    setResult([`Erro ao cadastrar: ${err.message}`], "error");
    submitted = false;
    try { submitBtn.disabled = false; } catch (e) {}
    try { submitBtn.textContent = "Cadastrar ficha"; } catch (e) {}
    return;
  }
  // Em envio bem-sucedido, mantemos o botão desabilitado para evitar reenvios acidentais.
});

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await loadTurmas();
    await loadComunidades();
  } catch (err) {
    setResult([`Erro ao carregar dados: ${err.message}`], "error");
  }

  // Preencher data de inscrição com a data atual
  const today = new Date();
  const todayStr = today.toISOString().split('T')[0]; // YYYY-MM-DD
  const dataInscricaoInput = document.getElementById('data-inscricao');

  if (dataInscricaoInput) {
    // Armazenar a data ISO em um atributo data
    dataInscricaoInput.dataset.isoDate = todayStr;
    // Exibir a data formatada
    dataInscricaoInput.value = formatDatePortuguese(todayStr);
  }

  // Auto-formatar telefones
  const telefoneFields = ['telefone', 'telefone-responsavel'];
  telefoneFields.forEach(id => {
    const field = document.getElementById(id);
    if (field) {
      field.addEventListener('input', (e) => {
        // Remover caracteres não-numéricos
        let value = e.target.value.replace(/\D/g, '');
        // Reaplica formatação
        e.target.value = formatTelefone(value);
      });
    }
  });

  // Focar no campo Nome ao carregar
  const nomeField = document.getElementById('nome');
  if (nomeField) nomeField.focus();

      // Listeners para os 3 campos de arquivo separados
      const fileInputsConfig = [
        { id: 'arquivo-documento', type: 'DOCUMENTO' },
        { id: 'arquivo-certidao', type: 'CERTIDAO' },
        { id: 'arquivo-foto', type: 'FOTO' }
      ];

      fileInputsConfig.forEach(({ id, type }) => {
        const input = document.getElementById(id);
        if (input) {
          input.addEventListener('change', (e) => {
            const files = Array.from(e.target.files || []);
            if (files.length > 0) {
              selectedFiles[type] = files[0]; // Armazena apenas um arquivo por tipo
              updateFilesList();
              updateFileInputLabels(); // Atualizar labels dos inputs
              // NÃO limpar o input.value aqui pois causa problemas com a exibição
            }
          });
        }
      });

  // Listeners para tipo de documento (habilitar e formatar campo de número)
  const tipoDocInputs = document.querySelectorAll('input[name="tipo-documento"]');
  const numeroDocInput = document.getElementById('numero-documento');
  const docHint = document.getElementById('doc-hint');

  tipoDocInputs.forEach(input => {
    input.addEventListener('change', (e) => {
      const tipoSelecionado = e.target.value;

      if (tipoSelecionado === 'RG') {
        numeroDocInput.disabled = false;
        numeroDocInput.placeholder = 'XX.XXX.XXX-X';
        docHint.innerHTML = '<strong>Formato: XX.XXX.XXX-X</strong>';
        numeroDocInput.value = ''; // limpar valor anterior
        numeroDocInput.focus();
      } else if (tipoSelecionado === 'CPF') {
        numeroDocInput.disabled = false;
        numeroDocInput.placeholder = 'XXX.XXX.XXX-XX';
        docHint.innerHTML = '<strong>Formato: XXX.XXX.XXX-XX</strong>';
        numeroDocInput.value = ''; // limpar valor anterior
        numeroDocInput.focus();
      }
    });
  });

  // Listener para formatação automática do campo de documento
  numeroDocInput.addEventListener('input', (e) => {
    const tipoSelecionado = document.querySelector('input[name="tipo-documento"]:checked');
    if (!tipoSelecionado) {
      e.target.value = e.target.value.replace(/\D/g, ''); // apenas números
      return;
    }

    const tipoDoc = tipoSelecionado.value;
    if (tipoDoc === 'RG') {
      e.target.value = formatRG(e.target.value);
    } else if (tipoDoc === 'CPF') {
      e.target.value = formatCPF(e.target.value);
    }
  });
  const fieldsToWatch = [
    'nome','telefone','email','data-nascimento','nome-responsavel','telefone-responsavel',
    'endereco','turma-select','comunidade-select','numero-documento','data-inscricao','signature-pad'
  ];
  fieldsToWatch.forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    const handler = () => el.classList.remove('invalid');
    if (el.tagName === 'SELECT' || el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'CANVAS') {
      el.addEventListener('input', handler);
      el.addEventListener('change', handler);
      // canvas won't fire input/change when drawn; handle pointerdown to clear
      if (el.tagName === 'CANVAS') el.addEventListener('pointerdown', handler);
    }
  });
});

