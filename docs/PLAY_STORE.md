# Publicação na Google Play — checklist e respostas

## Permissões do app (verificado no manifest final do release)

| Permissão | Visível ao usuário | Uso |
|---|---|---|
| `RECORD_AUDIO` | Sim — diálogo em tempo de execução | Afinador: análise do som em memória, sem gravação nem envio |
| `com.pitchandmetronome.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Não | Interna do AndroidX (adicionada automaticamente) |

- Sem `INTERNET`: o app não tem como enviar dados.
- `RECORD_AUDIO` não exige o formulário de declaração de permissões da Play
  (esse formulário é para SMS, registro de chamadas, localização em segundo
  plano, etc.). Exige **política de privacidade** e **Segurança dos dados**.
- Microfone declarado como `required="false"`: o app aparece também para
  aparelhos sem microfone (o metrônomo funciona).

## Política de privacidade

Arquivo pronto em [`privacy-policy.html`](privacy-policy.html).

1. Trocar `SEU_EMAIL_AQUI` pelo e-mail de contato de desenvolvedor.
2. Hospedar em URL pública (ex.: GitHub Pages servindo a pasta `docs/`,
   ou Google Sites). A Play só aceita URL acessível, não PDF nem arquivo.
3. Colar a URL em **Play Console > Política > Conteúdo do app > Política de privacidade**.

## Segurança dos dados (Data safety) — respostas

- O app coleta ou compartilha algum dos tipos de dados obrigatórios? **Não.**
  - Áudio processado só no aparelho e nunca transmitido **não conta como
    coleta** pela definição da Play.
- Com "Não", o formulário termina sem seções de criptografia/exclusão.

## Outras declarações em "Conteúdo do app"

- **Anúncios:** não contém anúncios.
- **Acesso ao app:** todas as funções disponíveis sem restrição (sem login).
- **Classificação de conteúdo:** questionário IARC, categoria "Utilitário /
  Produtividade"; responder "não" a tudo → classificação Livre.
- **Público-alvo:** escolher faixas **13+** (ex.: 13–15, 16–17, 18+). Incluir
  menores de 13 aplica a política de Famílias, com exigências extras.
- **App de notícias / governo / financeiro / saúde:** não.

## Ficha da loja (criar fora do código)

- Ícone 512×512 PNG (32 bits, sem transparência obrigatória nas bordas).
- Gráfico de recursos 1024×500.
- Ao menos 2 capturas de tela de celular (afinador e metrônomo).
- Descrição curta (até 80 caracteres) e completa (até 4000).

## Antes de enviar

- [ ] Contas pessoais criadas após nov/2023: a Play exige **teste fechado com
      12+ testadores por 14 dias** antes de liberar produção.
- [ ] Guardar o keystore de upload e as senhas fora do computador (sem ele, só
      com pedido de redefinição de chave de upload ao suporte).
- [ ] Ativar **Assinatura de apps do Google Play** no primeiro envio.
- [ ] `applicationId` `com.pitchandmetronome` é permanente após o primeiro
      envio — confirmar antes.
- [ ] Commitar e enviar as alterações: o pipeline gera o `.aab` a partir do Git.
