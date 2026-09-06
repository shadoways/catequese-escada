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
    // Os anexos sobem primeiro para o GCS; só depois a inscrição inteira é
    // enviada de uma vez. Se a gravação falhar, o banco desfaz tudo sozinho e
    // não sobra cadastro pela metade — antes isso dependia de a tela conseguir
    // apagar o que já tinha criado, o que falhava justamente quando a rede caía.
    const documentos = [];
    const hoje = new Date().toISOString().slice(0, 10);

    for (const fileType of ['DOCUMENTO', 'CERTIDAO', 'FOTO']) {
      if (!selectedFiles[fileType]) continue;
      const upload = await uploadFile(selectedFiles[fileType], fileType);
      if (!upload || !upload.filename) {
        throw new Error(`${fileType}: arquivo não foi salvo.`);
      }
      documentos.push({
        tipoDocumento: fileType,
        caminhoArquivo: upload.path || upload.filename,
        dataEnvio: hoje
      });
    }

    if (hasSignature) {
      const dataUrl = canvas.toDataURL("image/png");
      const byteString = atob(dataUrl.split(",")[1]);
      const buffer = new Uint8Array(byteString.length);
      for (let i = 0; i < byteString.length; i += 1) {
        buffer[i] = byteString.charCodeAt(i);
      }
      const signatureFile = new File([buffer], `assinatura-${Date.now()}.png`, { type: "image/png" });
      const upload = await uploadFile(signatureFile, "ASSINATURA");
      if (!upload || !upload.filename) {
        throw new Error("ASSINATURA: arquivo não foi salvo.");
      }
      documentos.push({
        tipoDocumento: "ASSINATURA",
        caminhoArquivo: upload.path || upload.filename,
        dataEnvio: hoje
      });
    }

    const inscricao = {
      catequisando: {
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
        idTurma: turmaId ? Number(turmaId) : null,
        idComunidade: comunidadeId ? Number(comunidadeId) : null
      },
      ficha: {
        dataInscricao: normalizeDate(dataInscricaoVal),
        observacoes: document.getElementById("observacoes").value.trim() || null
      },
      documentos
    };

    await fetchJson("/api/inscricoes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(inscricao)
    });

    const successMessages = ["Cadastro realizado com sucesso"];

    setResult(successMessages, "ok");


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

// ============================================================
// Consulta de catequisandos e painel de turmas/catequistas com
// status de documentos.
// A ficha em si é renderizada em ficha.html / ficha.js, que abre
// numa aba própria.
// ============================================================

const DOC_TYPE_LABELS = {
  DOCUMENTO: 'Documento (RG/CPF)',
  CERTIDAO: 'Certidão de Batismo',
  FOTO: 'Foto do Catequisando',
  ASSINATURA: 'Assinatura'
};
const DOC_TYPES_ESPERADOS = ['DOCUMENTO', 'CERTIDAO', 'FOTO', 'ASSINATURA'];

let catequisandosCache = [];
let consultaFiltrados = [];
let dashboardCache = { catequisandos: [], turmas: [], catequistas: [] };

const escapeHtml = (value) => {
  const div = document.createElement('div');
  div.textContent = value === null || value === undefined ? '' : String(value);
  return div.innerHTML;
};

// A ficha abre sempre em aba nova, para não misturar com a listagem.
const abrirFichaEmNovaAba = (query) => window.open(`ficha.html?${query}`, '_blank', 'noopener');

// ---- Navegação por abas ----
// Cadastro e a tela publica; consulta e painel sao de uso interno.
const TABS_PROTEGIDAS = ['chamada', 'agenda', 'frequencia', 'consulta', 'dashboard', 'indicadores', 'admin', 'usuarios', 'configuracoes', 'catequistas'];
// Indicadores e o relatorio da paroquia inteira: entra aqui para nao abrir nem
// forcando index.html?tab=indicadores. O backend barra de novo em /api/indicadores.
const TABS_SO_ADMIN = ['indicadores', 'admin', 'usuarios', 'configuracoes'];
// Consultar Catequistas: coordenador de comunidade so visualiza, catequista
// comum nao tem esta tela (especificacao, secao 2). Diferente de TABS_SO_ADMIN
// porque coordenador de comunidade (nao so paroquial) precisa entrar.
const TABS_SO_COORDENADOR = ['catequistas'];

// Texto da trilha (#trilha-pagina) acima do conteúdo -- o mesmo agrupamento
// usado nos rótulos da barra lateral, para reforçar "onde eu estou".
const TRILHA_POR_TAB = {
  menu: 'Início',
  cadastro: 'Cadastro',
  chamada: 'Atendimento / Chamada',
  agenda: 'Atendimento / Agenda',
  frequencia: 'Atendimento / Frequência',
  consulta: 'Atendimento / Consultar catequisandos',
  dashboard: 'Atendimento / Turmas e documentos',
  indicadores: 'Administração / Indicadores',
  admin: 'Administração / Turmas e inscrições',
  usuarios: 'Administração / Usuários',
  configuracoes: 'Administração / Configurações',
  catequistas: 'Administração / Consultar catequistas'
};

// ---- Estado do cadastro público ----
// Enquanto não sabemos, assumimos aberto: é o comportamento de sempre, e não
// faz sentido mostrar "encerradas" por causa de uma consulta que ainda não voltou.
let cadastroAberto = true;
// Chave de inscrição: null = ainda não conferimos; true/false = resultado.
let chaveValida = null;
let motivoChave = '';

const definirAviso = (titulo, texto, pedirChave) => {
  const elTitulo = document.getElementById('aviso-cadastro-titulo');
  const elTexto = document.getElementById('aviso-cadastro-texto');
  const elChave = document.getElementById('aviso-cadastro-chave');
  if (elTitulo) elTitulo.textContent = titulo;
  if (elTexto) elTexto.textContent = texto;
  if (elChave) elChave.hidden = !pedirChave;
};

const aplicarEstadoCadastro = () => {
  // Coordenador e coordenador paroquial cadastram pelo sistema: não dependem
  // de chave nem do período de inscrições.
  const interno = Auth.podeEditar();
  const fechado = !cadastroAberto && !interno;
  const semChave = !interno && chaveValida === false;
  const bloqueado = fechado || semChave;

  if (fechado) {
    definirAviso(
      'Inscrições encerradas',
      'O período de inscrições da catequese não está aberto no momento. ' +
      'Procure a secretaria da paróquia para mais informações.',
      false
    );
  } else if (semChave) {
    definirAviso(
      'Chave de inscrição necessária',
      motivoChave || 'Use o link enviado pela paróquia, ou informe abaixo a chave de inscrição.',
      true
    );
  }

  const aviso = document.getElementById('aviso-cadastro-bloqueado');
  if (aviso) aviso.hidden = !bloqueado;
  ['section-catequisando', 'section-anexos-assinatura', 'section-envio'].forEach((id) => {
    const secao = document.getElementById(id);
    if (secao) secao.hidden = bloqueado;
  });
};

// Confere a chave antes de mostrar o formulário, para a pessoa não preencher
// tudo e só descobrir no envio que o link não vale mais.
const verificarChaveInscricao = async () => {
  if (Auth.podeEditar()) { chaveValida = true; return; }

  const codigo = Auth.chaveInscricao();
  if (!codigo) {
    chaveValida = false;
    motivoChave = 'Use o link enviado pela paróquia, ou informe abaixo a chave de inscrição.';
    return;
  }

  try {
    const resposta = await fetch(`/api/chaves/validar?codigo=${encodeURIComponent(codigo)}`);
    const dados = await resposta.json();
    chaveValida = Boolean(dados.valida);
    motivoChave = dados.motivo || '';
  } catch (err) {
    // Sem resposta não dá para afirmar que é inválida; o backend barra no envio.
    chaveValida = true;
    console.warn('Não foi possível conferir a chave de inscrição:', err.message);
  }
};

// configuracoes.js chama isto ao salvar, para a tela não ficar desatualizada.
window.definirCadastroAberto = (valor) => {
  cadastroAberto = valor;
  aplicarEstadoCadastro();
};

const verificarCadastroAberto = async () => {
  try {
    await verificarChaveInscricao();
    const resposta = await fetch('/api/config/cadastro');
    if (resposta.ok) {
      const dados = await resposta.json();
      cadastroAberto = Boolean(dados.cadastroAberto);
    }
    aplicarEstadoCadastro();
  } catch (err) {
    // Sem resposta, mantém o cadastro visível: o backend barra de qualquer forma.
    console.warn('Não foi possível consultar o estado do cadastro:', err.message);
  }
};

// O cadastro é a porta de entrada pública: quem não está logado precisa dele
// para se inscrever. Já entre os usuários do sistema, só o coordenador
// paroquial cadastra — catequista e coordenador usam consulta e painel.
const podeVerCadastro = () => !Auth.estaLogado() || Auth.ehAdmin();

// Coordenador de comunidade OU paroquial -- nao inclui catequista comum.
// Usado em telas de recorte por comunidade que nao sao so do paroquial
// (Consultar Catequistas), diferente de Auth.ehAdmin() que e so paroquial.
const ehCoordenadorOuMais = () => {
  const tipo = Auth.usuario()?.tipo;
  return tipo === 'COORDENADOR' || tipo === 'COORDENADOR_PAROQUIAL';
};

// Mostra ou esconde o que depende do papel de quem esta logado.
// Isto e conforto visual: quem bloqueia de verdade e o backend.
const aplicarPermissoesNaTela = () => {
  const admin = Auth.ehAdmin();
  document.querySelectorAll('.somente-admin').forEach((el) => {
    el.hidden = !admin;
  });
  document.querySelectorAll('.somente-coordenador').forEach((el) => {
    el.hidden = !ehCoordenadorOuMais();
  });
  document.querySelectorAll('.somente-cadastro').forEach((el) => {
    el.hidden = !podeVerCadastro();
  });
  // Consulta e painel são área interna: para quem chega sem login, o index
  // mostra só o cadastro. Entrar é o caminho para o resto, pela barra do topo.
  document.querySelectorAll('.somente-logado').forEach((el) => {
    el.hidden = !Auth.estaLogado();
  });
};

const switchTab = (tabName) => {
  // Manda para o login guardando o destino, para voltar direto na aba pedida.
  if (TABS_PROTEGIDAS.includes(tabName) && !Auth.estaLogado()) {
    Auth.irParaLogin(tabName);
    return;
  }

  // Alguem sem permissao chegou pela URL (index.html?tab=usuarios).
  if (TABS_SO_ADMIN.includes(tabName) && !Auth.ehAdmin()) {
    switchTab('menu');
    return;
  }

  // Idem para quem forca index.html?tab=catequistas sem ser coordenador.
  if (TABS_SO_COORDENADOR.includes(tabName) && !ehCoordenadorOuMais()) {
    switchTab('menu');
    return;
  }

  // Catequista e coordenador não têm tela de cadastro, nem forçando a URL.
  if (tabName === 'cadastro' && !podeVerCadastro()) {
    switchTab('menu');
    return;
  }

  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === tabName);
  });
  document.querySelectorAll('.tab-content').forEach((el) => {
    el.hidden = el.dataset.tabContent !== tabName;
  });
  // A barra lateral fica sempre visível, inclusive na tela inicial -- é o
  // mapa permanente do sistema, não um apoio que só aparece depois que a
  // pessoa já escolheu uma opção (era assim na barra de abas antiga).
  const trilha = document.getElementById('trilha-pagina');
  if (trilha) trilha.textContent = TRILHA_POR_TAB[tabName] || '';
  // chamada.js registra esta funcao; a aba e a tela de trabalho do catequista.
  if (tabName === 'chamada' && window.carregarChamada) window.carregarChamada();
  // agenda.js registra esta funcao.
  if (tabName === 'agenda' && window.carregarAgenda) window.carregarAgenda();
  if (tabName === 'frequencia' && window.carregarFrequencia) window.carregarFrequencia();
  if (tabName === 'admin' && window.carregarAdminCatequese) window.carregarAdminCatequese();
  if (tabName === 'consulta') carregarConsulta();
  if (tabName === 'dashboard') carregarDashboard();
  // usuarios.js registra esta função; só existe para quem carrega aquela tela.
  // indicadores.js registra esta funcao.
  if (tabName === 'indicadores' && window.carregarIndicadores) window.carregarIndicadores();
  if (tabName === 'usuarios' && window.carregarUsuarios) window.carregarUsuarios();
  if (tabName === 'configuracoes' && window.carregarConfiguracoes) window.carregarConfiguracoes();
  // catequistas.js registra esta funcao.
  if (tabName === 'catequistas' && window.carregarCatequistas) window.carregarCatequistas();
  if (tabName === 'cadastro') aplicarEstadoCadastro();
};

document.querySelectorAll('.tab-btn').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

// Cards da tela inicial levam para a mesma aba que o botão do menu superior.
document.querySelectorAll('.menu-card').forEach((card) => {
  card.addEventListener('click', () => switchTab(card.dataset.tab));
});

aplicarPermissoesNaTela();
verificarCadastroAberto();

// Quem recebeu o código por outro caminho (cartaz, mural, de viva voz) pode
// digitá-lo em vez de usar o link.
const botaoChave = document.getElementById('btn-usar-chave');
if (botaoChave) {
  botaoChave.addEventListener('click', async () => {
    const entrada = document.getElementById('entrada-chave');
    const codigo = (entrada?.value || '').trim();
    if (!codigo) return;
    botaoChave.disabled = true;
    Auth.definirChaveInscricao(codigo);
    await verificarChaveInscricao();
    aplicarEstadoCadastro();
    botaoChave.disabled = false;
    if (chaveValida === false && entrada) entrada.focus();
  });
}

// Depois do login o usuário volta direto para a aba que tentou abrir
// (login.js redireciona para index.html?tab=consulta, por exemplo).
const tabInicial = new URLSearchParams(window.location.search).get('tab');
if (tabInicial) switchTab(tabInicial);

// ---- Consulta de catequisandos ----
// Preenche um <select> de filtro preservando a opção que já estava marcada.
const preencherFiltro = (selectId, textoPadrao, itens, getValor, getTexto) => {
  const select = document.getElementById(selectId);
  const selecionado = select.value;
  select.innerHTML = `<option value="">${textoPadrao}</option>`;
  itens.forEach((item) => {
    const option = document.createElement('option');
    option.value = getValor(item);
    option.textContent = getTexto(item);
    select.appendChild(option);
  });
  select.value = selecionado || '';
};

const carregarConsulta = async () => {
  const lista = document.getElementById('consulta-lista');
  lista.innerHTML = '<p class="muted">Carregando...</p>';
  try {
    const [catequisandos, turmas, comunidades] = await Promise.all([
      fetchJson('/api/catequisandos'),
      fetchJson('/api/turmas'),
      fetchJson('/api/comunidades')
    ]);
    catequisandosCache = catequisandos;

    preencherFiltro('consulta-turma-filtro', 'Todas as turmas', turmas,
      (t) => t.idTurma, (t) => t.nome);
    preencherFiltro('consulta-comunidade-filtro', 'Todas as comunidades', comunidades,
      (c) => c.idComunidade, (c) => c.nome);

    renderConsultaLista();
  } catch (err) {
    lista.innerHTML = `<p class="status error">Erro ao carregar catequisandos: ${escapeHtml(err.message)}</p>`;
  }
};

const renderConsultaLista = () => {
  const lista = document.getElementById('consulta-lista');
  const termo = (document.getElementById('consulta-busca').value || '').trim().toLowerCase();
  const turmaFiltro = document.getElementById('consulta-turma-filtro').value;
  const comunidadeFiltro = document.getElementById('consulta-comunidade-filtro').value;

  const filtrados = catequisandosCache.filter((c) => {
    const nomeOk = !termo || c.nome.toLowerCase().includes(termo);
    const turmaOk = !turmaFiltro || String(c.turma?.idTurma ?? '') === turmaFiltro;
    const comunidadeOk = !comunidadeFiltro || String(c.comunidade?.idComunidade ?? '') === comunidadeFiltro;
    return nomeOk && turmaOk && comunidadeOk;
  });

  // Guardado para a impressão em lote respeitar exatamente o filtro atual.
  consultaFiltrados = filtrados;

  const contador = document.getElementById('consulta-contador');
  const botaoLote = document.getElementById('btn-imprimir-lote');
  contador.textContent = filtrados.length
    ? `${filtrados.length} catequisando(s) na seleção atual`
    : '';
  botaoLote.disabled = !filtrados.length;

  if (!filtrados.length) {
    lista.innerHTML = '<p class="muted">Nenhum catequisando encontrado.</p>';
    return;
  }

  // Link de verdade: permite abrir em nova aba, nova janela ou com o botão do meio.
  lista.innerHTML = filtrados.map((c) => `
    <a class="result-item" href="ficha.html?id=${c.idCatequisando}" target="_blank" rel="noopener">
      <span>
        <span class="nome">${escapeHtml(c.nome)}</span>
        <span class="meta">${escapeHtml(c.turma?.nome || 'Sem turma')} · ${escapeHtml(c.comunidade?.nome || 'Sem comunidade')}</span>
      </span>
      <span class="botao-falso">Ver ficha</span>
    </a>
  `).join('');
};

document.getElementById('consulta-busca').addEventListener('input', renderConsultaLista);
document.getElementById('consulta-turma-filtro').addEventListener('change', renderConsultaLista);
document.getElementById('consulta-comunidade-filtro').addEventListener('change', renderConsultaLista);

// Impressão em lote: abre uma aba com todas as fichas da seleção e já manda imprimir.
document.getElementById('btn-imprimir-lote').addEventListener('click', () => {
  if (!consultaFiltrados.length) return;

  const turma = document.getElementById('consulta-turma-filtro').value;
  const comunidade = document.getElementById('consulta-comunidade-filtro').value;
  const termo = (document.getElementById('consulta-busca').value || '').trim();

  // Sem busca por nome, basta repassar os filtros — evita uma URL gigante.
  let query;
  if (!termo && (turma || comunidade)) {
    query = new URLSearchParams(
      Object.assign({ print: '1' }, turma ? { turma } : {}, comunidade ? { comunidade } : {})
    ).toString();
  } else {
    query = `ids=${consultaFiltrados.map((c) => c.idCatequisando).join(',')}&print=1`;
  }

  abrirFichaEmNovaAba(query);
});

// ---- Painel: catequistas, turmas e documentos faltantes ----
//
// A consulta pesada (catequisandos + documentos) só acontece depois que o
// filtro é aplicado. Antes, o painel carrega apenas as listas curtas que
// alimentam os selects.

const setStatusPainel = (texto, tipo = '') => {
  const box = document.getElementById('painel-status');
  box.innerHTML = texto ? `<div class="status ${tipo}">${escapeHtml(texto)}</div>` : '';
};

// Executa em grupos, para não disparar centenas de requisições de uma vez.
const emLotes = async (itens, tamanho, fn) => {
  for (let i = 0; i < itens.length; i += tamanho) {
    await Promise.all(itens.slice(i, i + tamanho).map(fn));
  }
};

// Só as listas leves: turmas, comunidades e catequistas.
const carregarDashboard = async () => {
  if (dashboardCache.turmas.length) return;

  try {
    const [catequistas, turmas, comunidades] = await Promise.all([
      fetchJson('/api/catequistas'),
      fetchJson('/api/turmas'),
      fetchJson('/api/comunidades')
    ]);

    dashboardCache = { catequisandos: [], turmas, catequistas };

    preencherFiltro('painel-turma-filtro', 'Todas as turmas', turmas,
      (t) => t.idTurma, (t) => t.nome);
    preencherFiltro('painel-comunidade-filtro', 'Todas as comunidades', comunidades,
      (c) => c.idComunidade, (c) => c.nome);
  } catch (err) {
    setStatusPainel(`Erro ao carregar os filtros: ${err.message}`, 'error');
  }
};

// Um catequisando está completo quando entregou todos os tipos de documento esperados.
const catequisandoCompleto = (idCatequisando, docsPorCatequisando) => {
  const entregues = docsPorCatequisando[idCatequisando] || new Set();
  return DOC_TYPES_ESPERADOS.every((tipo) => entregues.has(tipo));
};

const consultarPainel = async () => {
  const idTurma = document.getElementById('painel-turma-filtro').value;
  const idComunidade = document.getElementById('painel-comunidade-filtro').value;
  const statusDocumentos = document.getElementById('painel-status-filtro').value;
  const container = document.getElementById('dashboard-conteudo');
  const botao = document.getElementById('btn-painel-consultar');

  if (!idTurma && !idComunidade) {
    const ok = window.confirm(
      'Sem nenhum filtro, o painel vai consultar os documentos de todos os catequisandos da base. ' +
      'Isso pode demorar bastante. Deseja continuar?'
    );
    if (!ok) return;
  }

  botao.disabled = true;
  container.innerHTML = '';
  setStatusPainel('Carregando catequisandos...');

  try {
    // A lista de catequisandos é buscada uma vez e reaproveitada nos filtros seguintes.
    if (!dashboardCache.catequisandos.length) {
      dashboardCache.catequisandos = await fetchJson('/api/catequisandos');
    }

    const selecionados = dashboardCache.catequisandos
      .filter((c) => !idTurma || String(c.turma?.idTurma ?? '') === idTurma)
      .filter((c) => !idComunidade || String(c.comunidade?.idComunidade ?? '') === idComunidade);

    if (!selecionados.length) {
      setStatusPainel('Nenhum catequisando encontrado para esse filtro.', 'warning');
      return;
    }

    // Em vez de baixar /api/documentos inteiro, consulta só quem está no filtro.
    const docsPorCatequisando = {};
    let processados = 0;
    await emLotes(selecionados, 6, async (c) => {
      try {
        const docs = await fetchJson(`/api/documentos/catequisando/${c.idCatequisando}`);
        docsPorCatequisando[c.idCatequisando] = new Set(docs.map((d) => d.tipoDocumento));
      } catch (err) {
        docsPorCatequisando[c.idCatequisando] = new Set();
        console.warn(`Documentos do catequisando ${c.idCatequisando}:`, err.message);
      }
      processados += 1;
      setStatusPainel(`Consultando documentos... ${processados} de ${selecionados.length}`);
    });

    // Filtro de status só pode ser aplicado depois de saber quem entregou o quê.
    let visiveis = selecionados;
    if (statusDocumentos === 'completos') {
      visiveis = selecionados.filter((c) => catequisandoCompleto(c.idCatequisando, docsPorCatequisando));
    } else if (statusDocumentos === 'pendentes') {
      visiveis = selecionados.filter((c) => !catequisandoCompleto(c.idCatequisando, docsPorCatequisando));
    }

    if (!visiveis.length) {
      setStatusPainel('Nenhum catequisando encontrado para esse filtro.', 'warning');
      return;
    }

    // Mostra apenas as turmas que têm alguém na seleção (já com o filtro de documentos aplicado).
    const idsTurmas = new Set(visiveis.map((c) => c.turma?.idTurma).filter(Boolean));
    const turmasVisiveis = dashboardCache.turmas.filter((t) => idsTurmas.has(t.idTurma));

    let html = turmasVisiveis
      .map((t) => renderTurmaCard(
        t,
        dashboardCache.catequistas.find((k) => k.idCatequista === t.catequista?.idCatequista) || t.catequista,
        visiveis,
        docsPorCatequisando
      ))
      .join('');

    // Quem está sem turma não pode sumir do controle de documentos.
    const semTurma = visiveis.filter((c) => !c.turma?.idTurma);
    if (semTurma.length) {
      html += renderTurmaCard({ idTurma: null, nome: 'Sem turma definida' }, null,
        semTurma, docsPorCatequisando);
    }

    container.innerHTML = html || '<p class="muted">Nenhuma turma encontrada.</p>';

    // A impressão sempre usa os ids exibidos na tela, para bater exatamente com o
    // que o filtro de documentos (completos/pendentes) está mostrando no momento.
    container.querySelectorAll('[data-imprimir-ids]').forEach((btn) => {
      btn.addEventListener('click', () => {
        abrirFichaEmNovaAba(`ids=${btn.dataset.imprimirIds}&print=1`);
      });
    });

    setStatusPainel(`${visiveis.length} catequisando(s) em ${turmasVisiveis.length + (semTurma.length ? 1 : 0)} turma(s).`, 'ok');
  } catch (err) {
    setStatusPainel(`Erro ao consultar: ${err.message}`, 'error');
  } finally {
    botao.disabled = false;
  }
};

document.getElementById('btn-painel-consultar').addEventListener('click', consultarPainel);

const renderTurmaCard = (turma, catequista, catequisandos, docsPorCatequisando) => {
  // turma.idTurma nulo é o cartão "Sem turma definida"
  const catequisandosDaTurma = turma.idTurma
    ? catequisandos.filter((c) => c.turma?.idTurma === turma.idTurma)
    : catequisandos.filter((c) => !c.turma?.idTurma);

  let linhas = '';
  if (!catequisandosDaTurma.length) {
    linhas = '<p class="muted">Nenhum catequisando nesta turma.</p>';
  } else {
    catequisandosDaTurma.forEach((c) => {
      const entregues = docsPorCatequisando[c.idCatequisando] || new Set();
      const badges = DOC_TYPES_ESPERADOS.map((tipo) => {
        const ok = entregues.has(tipo);
        const label = DOC_TYPE_LABELS[tipo];
        return `<span class="doc-status ${ok ? '' : 'faltando'}">${ok ? '✅' : '❌'} ${escapeHtml(label)}</span>`;
      }).join('');
      linhas += `
        <div class="catequisando-row">
          <a class="nome-link" href="ficha.html?id=${c.idCatequisando}" target="_blank" rel="noopener">${escapeHtml(c.nome)}</a>
          <div class="docs">${badges}</div>
        </div>
      `;
    });
  }

  // Sempre imprime pelos ids exibidos no cartão, para respeitar também o
  // filtro de status de documentos (completos/pendentes), e não só turma/comunidade.
  let botaoImprimir = '';
  if (catequisandosDaTurma.length) {
    const rotulo = `Imprimir ${catequisandosDaTurma.length} ficha(s)${turma.idTurma ? ' desta turma' : ''}`;
    const ids = catequisandosDaTurma.map((c) => c.idCatequisando).join(',');
    botaoImprimir = `<button type="button" class="secondary" data-imprimir-ids="${ids}">${rotulo}</button>`;
  }

  return `
    <div class="turma-card">
      <div class="row" style="justify-content: space-between; align-items: flex-start;">
        <div>
          <h3>${escapeHtml(turma.nome)}${turma.ano ? ' (' + turma.ano + ')' : ''}</h3>
          <div class="catequista-nome">Catequista: ${escapeHtml(catequista?.nome || 'Sem catequista responsável')}</div>
        </div>
        ${botaoImprimir}
      </div>
      ${linhas}
    </div>
  `;
};

