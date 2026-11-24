# Flynn 🐶 — app de cuidados

App Android (Kotlin + Jetpack Compose) para nós dois cuidarmos do Flynn:

- **Início**: total de refeições hoje (desde a meia-noite), última refeição (hora e quem deu) e 3 botões lado a lado — **🍖 Dei comida**, **💧 Pinguei colírio** e **🪥 Escovei os dentes** — cada um com a contagem do dia. Se alguém registrou o mesmo cuidado há menos de 30 min, o app pergunta antes de registrar de novo. O colírio fica verde ao bater a meta diária; os dentes ficam vermelhos após 3 dias sem escovar. Também mostra os próximos cuidados e os registros recentes (com desfazer/apagar). Tudo sincronizado entre os 2 celulares em tempo real.
- **Histórico** (sub-abas Comida, Colírio e Dentes): um cartão por dia (últimos 30 dias) com horário, quem fez e quantas vezes cada um fez. Na sub-aba Colírio dá para definir a **meta diária** e um **lembrete** que só avisa se a meta ainda não foi cumprida.
- **Peso**: registrar pesagens (kg + data) e curva de peso com 3 visualizações: **Dia**, **Mês** e **Ano** (mês/ano = média das pesagens do período).
- **Saúde** (sub-abas Vacinas, Antipulgas e Exames):
- **Vacinas** e **Antipulgas** (produto + dose): lembretes **2 dias antes, 1 dia antes e no dia**, às 9h.
- **Exames**: dados e resultados digitados à mão, lembretes **personalizáveis** (quais dias antes e horário).

## Como funciona a segurança

| Camada | O que protege |
|---|---|
| Firebase Authentication (e-mail/senha) | Só quem tem uma das 2 contas entra. As contas são criadas por você no console. |
| Regras do Firestore (`firestore.rules`) | O servidor só aceita leitura/escrita se o e-mail logado for um dos 2 da lista. Mesmo alguém que extraia o APK não lê nada. |
| HTTPS/TLS | Todo tráfego com o Firebase é criptografado. |
| GitHub Secrets | `google-services.json` e a chave de assinatura nunca entram no repositório público. |
| Assinatura do APK | O Android só aceita atualizações assinadas com a mesma chave — ninguém consegue publicar uma "atualização" falsa. |

Os dados do Flynn nunca ficam no GitHub; o repo tem só o código.

---

## Configuração (uma vez só)

### 1. Ferramentas no PC (Windows)
```powershell
winget install Git.Git GitHub.cli EclipseAdoptium.Temurin.17.JDK
```
Feche e reabra o terminal depois.

### 2. Firebase
1. Acesse https://console.firebase.google.com → **Adicionar projeto** (pode desativar o Google Analytics).
2. **Adicionar app → Android**, pacote `com.flynn.care`. Baixe o **`google-services.json`** (guarde, não coloque no repo).
3. **Authentication → Método de login → E-mail/senha → Ativar.**
4. **Authentication → Usuários → Adicionar usuário**: crie a sua conta e a da sua esposa.
5. (Opcional, recomendado) **Authentication → Configurações → Ações do usuário**: desmarque "Ativar criação (inscrição)".
6. **Firestore Database → Criar banco** → modo produção → região `southamerica-east1` (São Paulo).
7. **Firestore → Regras**: cole o conteúdo de `firestore.rules`, troque os 2 e-mails e **Publicar**.

### 3. Chave de assinatura (guarde um backup! sem ela não dá para atualizar o app)
```powershell
keytool -genkeypair -v -keystore flynn.jks -alias flynn -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("flynn.jks")) | Set-Clipboard
```
O segundo comando copia a chave em base64 para a área de transferência.

### 4. Repositório no GitHub
```powershell
cd flynn-app
git init; git add .; git commit -m "Primeira versão do app do Flynn"
gh auth login
gh repo create flynn-app --public --source . --push
```

Depois, em **Settings → Secrets and variables → Actions → New repository secret**, crie:

| Nome | Valor |
|---|---|
| `GOOGLE_SERVICES_JSON` | conteúdo inteiro do `google-services.json` |
| `KEYSTORE_BASE64` | o que foi copiado no passo 3 |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `flynn` |
| `KEY_PASSWORD` | senha da chave (normalmente a mesma) |

### 5. Publicar uma versão
```powershell
git tag v1.0.0
git push origin v1.0.0
```
O GitHub Actions compila e cria uma **Release** com `flynn-v1.0.0.apk` (aba *Actions* mostra o progresso).

### 6. Instalar nos celulares, com atualização automática
1. Instale o **Obtainium** (https://github.com/ImranR98/Obtainium/releases — baixe o `app-arm64-v8a-release.apk`).
2. No Obtainium: **Adicionar app** → cole `https://github.com/SEU_USUARIO/flynn-app` → Adicionar → Instalar.
3. O Obtainium verifica novas releases periodicamente e atualiza o app. O próprio app também mostra um aviso "Nova versão disponível".
4. Abra o app, entre com seu e-mail/senha e **permita notificações**.
5. Em celulares Samsung/Xiaomi/Motorola, deixe o Flynn **sem restrição de bateria** para os lembretes não atrasarem.

Para lançar uma atualização: altere o código, faça commit/push e crie uma nova tag (`v1.0.1`, `v1.1.0`…).

---

## Estrutura

```
app/src/main/java/com/flynn/care/
  data/Repo.kt              Firestore (registros por tipo, itens, peso, offline)
  data/Models.kt            CareItem, ActionType, CareEvent, EyedropSettings
  data/EventStats.kt        "hoje" desde a meia-noite, aviso de clique repetido
  reminders/                alarmes locais, notificação, reboot, sync a cada 6h
  ui/                       telas (Início, abas, login)
  UpdateChecker.kt          aviso de nova versão via API do GitHub
.github/workflows/release.yml   build + release assinada
firestore.rules                 regras de segurança do servidor
```
