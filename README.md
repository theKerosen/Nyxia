# NyxiaBot: Um Bot de Discord Poderoso em Java (Powered by Nekoffee)

![Java](https://img.shields.io/badge/Java-17%2B-5382A1?logo=java)
![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?logo=apache-maven)
![Discord API](https://img.shields.io/badge/Discord%20API-Custom%20Lib-7289DA?logo=discord)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

---

## ☕ Introdução

O **NyxiaBot** é um bot de Discord robusto e altamente modular, desenvolvido em Java. Sua principal característica é ser construído sobre a **Nekoffee**, uma biblioteca customizada de Discord API (Discord API Wrapper) criada do zero neste projeto.

Este bot demonstra como construir uma aplicação de Discord de alta performance e grande controle, utilizando comunicação REST e Gateway (WebSockets) com o Discord de forma manual, sem depender de bibliotecas de terceiros como JDA ou Discord4J.

---

## ✨ Funcionalidades Principais

*   **Processamento de Eventos em Tempo Real:** Conexão e tratamento de eventos do Discord Gateway (mensagens, entrada/saída de membros, atualizações de voz).
*   **Sistema de Comandos Customizável:** Um framework de comando modular e expansível.
*   **Sistema de XP e Níveis:**
    *   Atribuição de XP por mensagem (com cooldown).
    *   Cálculo de níveis dinâmico.
    *   **Atribuição Automática de Cargos de XP:** Recompensa usuários com cargos específicos ao atingir certos níveis.
*   **Canais de Voz Temporários:**
    *   Criação automática de canais de voz personalizados para usuários que entram em um canal "hub".
    *   Deleção automática de canais quando ficam vazios (com cache de estados de voz em tempo real).
    *   Comandos para gerenciar canais temporários (`!sala limite`, `!sala nome`, `!sala trancar`, `!sala destrancar`, `!sala permitir`, `!sala proibir`).
    *   Persistência de preferências de canais temporários por usuário (nome, limite, trancamento padrão).
*   **Sistema de Logs Detalhado:**
    *   Logs de mensagens editadas e deletadas.
    *   Logs de entrada e saída de membros.
    *   Logs de atualizações de membros (ex: mudança de apelido).
    *   Logs formatados com Embeds bonitos.
*   **Persistência de Dados:** Utiliza um banco de dados SQLite embutido para armazenar dados de XP, canais temporários e preferências do usuário.
*   **Arquitetura Modular:** Separação clara entre a biblioteca (`nekoffee-*`) e a aplicação (`nyxiabot-*`), facilitando a manutenção e expansão.

---

## 🚀 Como Começar

Siga estas instruções para configurar e rodar o NyxiaBot em seu próprio servidor.

### Pré-requisitos

*   **Java Development Kit (JDK) 17 ou superior:**
    *   Verifique sua versão: `java -version`
*   **Apache Maven 3.8 ou superior:**
    *   Verifique sua versão: `mvn -v`
*   **Git:** Para clonar o repositório.

### Configuração do Bot no Discord

1.  Vá para o [**Portal de Desenvolvedores do Discord**](https://discord.com/developers/applications).
2.  Clique em **"New Application"** (Nova Aplicação) e dê um nome (ex: "NyxiaBot").
3.  No menu lateral esquerdo, vá para a aba **"Bot"**.
4.  Clique em **"Add Bot"** e confirme.
5.  **Copie o Token do Bot:** Clique em "Copy" (ou "Reset Token" e depois "Copy"). **Mantenha este token em segurança!**
6.  **Habilite os Gateway Intents Privilegiados:** Role para baixo até a seção "Privileged Gateway Intents" e **ATIVE** as seguintes opções:
    *   `MESSAGE CONTENT INTENT`
    *   `SERVER MEMBERS INTENT`
    *   `GUILD PRESENCES INTENT` (se quiser eventos de presença, mas não estritamente necessário para as funções básicas de XP/TempChannels)
    *   `GUILD VOICE STATES`
7.  **Gere o Link de Convite:**
    *   Vá para a aba **"OAuth2" -> "URL Generator"**.
    *   Em **"SCOPES"**, selecione: `bot`.
    *   Em **"BOT PERMISSIONS"**, selecione as permissões necessárias:
        *   `Administrator` (para simplificar, mas **não recomendado para bots em produção!**)
        *   **Permissões Mínimas Recomendadas:** `Read Messages/View Channels`, `Send Messages`, `Embed Links`, `Manage Channels`, `Move Members`, `Manage Roles`, `Read Message History`.
    *   Copie o link gerado e cole-o no seu navegador para convidar o bot para o seu servidor.

### Configuração do Projeto

1.  **Clone o Repositório:**
    ```bash
    git clone https://github.com/theKerosen/Nyxia.git
    cd Nyxia
    ```

2.  **Configurações do Bot:**
    Crie um arquivo chamado `config.properties` dentro da pasta `nyxiabot/src/main/resources/` (ou `nyxiabot-app/src/main/resources/`, dependendo da sua estrutura).

    Cole o seguinte conteúdo e preencha com os IDs reais do seu servidor:

    ```properties
    # Token do Bot (OBRIGATÓRIO)
    BOT_TOKEN=SEU_TOKEN_DO_BOT_AQUI

    # Prefixos de Comando
    COMMAND_PREFIX=!

    # Canais de Log
    LOG_CHANNEL_ID=ID_DO_CANAL_DE_LOGS # Ex: logs, auditoria

    # Mensagem de Boas-Vindas
    WELCOME_CHANNEL_ID=ID_DO_CANAL_DE_BOAS_VINDAS # Ex: bem-vindos

    # Auto-atribuição de Cargo (ao entrar no servidor)
    AUTO_ASSIGN_ROLE_ID=ID_DO_CARGO_DE_AUTO_ATRIBUICAO # Ex: Membro

    # Cargos de XP (Level -> Role ID)
    # O bot precisa ter permissão para gerenciar estes cargos e estar acima deles na hierarquia.
    XP_ROLE_LEVEL_5=ID_DO_CARGO_NIVEL_5
    XP_ROLE_LEVEL_10=ID_DO_CARGO_NIVEL_10
    XP_ROLE_LEVEL_20=ID_DO_CARGO_NIVEL_20
    XP_ROLE_LEVEL_50=ID_DO_CARGO_NIVEL_50
    # ... adicione mais níveis conforme necessário

    # Canais de Voz Temporários
    HUB_CHANNEL_ID=ID_DO_CANAL_VOZ_HUB # ID do canal de voz 'Criar Sala'
    TEMP_CHANNEL_CATEGORY_ID=ID_DA_CATEGORIA_VOZ_TEMPORARIA # ID da categoria onde os canais temporários serão criados (opcional)
    TEMP_CHANNEL_NAME_PREFIX=Sala de # Prefixo para o nome do canal (ex: 'Sala de [NomeUsuário]')
    DEFAULT_TEMP_CHANNEL_USER_LIMIT=0 # Limite padrão de usuários (0 para ilimitado, 1-99 para limitado)
    DEFAULT_TEMP_CHANNEL_LOCK=false # Trancar por padrão (true/false)
    ```
    *Como alternativa, você pode definir essas configurações como variáveis de ambiente no seu sistema.*

### Rodando o Bot

1.  **Compile o Projeto (para criar um JAR executável único):**
    Abra seu terminal na **raiz do projeto** e execute:
    ```bash
    mvn clean package
    ```
    Este comando compilará todos os módulos e criará um JAR executável único (`.jar`) contendo todas as dependências na pasta `nyxiabot/target/` (ou `nyxiabot-app/target/`, dependendo do nome do seu módulo principal). O nome será algo como `nyxiabot-single.jar`.

2.  **Execute o Bot:**
    Navegue até o diretório `target/` do seu módulo principal (ex: `nyxiabot/target/`) e execute:
    ```bash
    java -jar nyxiabot.jar
    ```
    Seu bot deverá inicializar, conectar ao Discord Gateway e começar a processar eventos. Um arquivo `data/nyxiabot.db` será criado automaticamente na pasta de execução, contendo seu banco de dados SQLite.

---

## 🗺️ Estrutura do Projeto

Este projeto é um monorepo Maven multi-módulo, organizado para promover modularidade e separação de responsabilidades.

nekoffee-project/
├── nekoffee-api/ # Definições de API (interfaces, DTOs de dados de envio, eventos)
├── nekoffee-core/ # Nucleo do projeto, implementação do cliente
├── nekoffee-model/ # Implementações das entidades da API (MessageImpl, UserImpl, etc.)
├── nekoffee-http/ # Cliente HTTP (OkHttp) para comunicação REST com o Discord
├── nekoffee-gateway-client/ # Cliente WebSocket para o Discord Gateway (eventos em tempo real)
├── nekoffee-json-util/ # Utilitários de serialização/desserialização JSON (Jackson)
├── nekoffee-builder-util/ # Construtores de mensagens e embeds (MessageBuilder, EmbedBuilder)
├── nyxiabot-commands/ # Framework de comando e implementações de comandos específicos do bot
├── nyxiabot-config/ # Gerenciamento de configurações do bot
├── nyxiabot-database/ # Gerenciamento do banco de dados SQLite (JDBC)
└── nyxiabot/ # Módulo da aplicação principal (contém a classe main do bot)



### 📚 A Biblioteca Nekoffee

A **Nekoffee** (módulos `nekoffee-*`) é a camada fundamental deste projeto. É uma API de Discord para Java construída do zero, focando em:

*   **Controle Total:** Gerenciamento manual da comunicação REST e do protocolo Gateway.
*   **Modularidade:** Componentes bem definidos e independentes para HTTP, Gateway, JSON e modelos de dados.
*   **Assíncrona:** Utiliza `CompletableFuture` para operações não bloqueantes.

### 🤖 A Aplicação NyxiaBot

O **NyxiaBot** (módulos `nyxiabot-*`) é a aplicação de bot real que utiliza a biblioteca Nekoffee. Ela demonstra como consumir a API da Nekoffee e implementar funcionalidades complexas de bot através de:

*   **Listeners de Eventos:** Classes dedicadas que reagem a eventos específicos do Discord.
*   **Sistema de Comandos:** Um framework para criar e gerenciar comandos facilmente.
*   **Serviços:** Classes que encapsulam a lógica de negócio de alto nível (ex: gerenciamento de XP Roles).
*   **Persistência:** Integração com o banco de dados SQLite.

---

## 🤝 Contribuições

Contribuições são bem-vindas! Sinta-se à vontade para abrir issues, propor melhorias ou enviar pull requests.

---

## 📄 Licença

Este projeto está licenciado sob a Licença MIT.

---