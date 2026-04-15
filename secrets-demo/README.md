# secrets-demo

POC (Proof of Concept) que demonstra a integração de uma aplicação Spring Boot com o **AWS Secrets Manager**, incluindo rotação automática de credenciais vinculada a um banco **RDS MySQL**.

O objetivo é validar que:
- A aplicação busca as credenciais do banco diretamente no Secrets Manager (nunca em `application.properties` ou variáveis de ambiente)
- Após uma rotação de senha, a aplicação continua funcionando sem nenhuma intervenção manual
- O código é idêntico entre ambiente local e produção — apenas **quem fornece as credenciais ao SDK** muda

---

## Como funciona

Na inicialização, `DataSourceConfig` chama o Secrets Manager e obtém um JSON com as credenciais do banco:

```json
{
  "username": "admin",
  "password": "...",
  "host": "seu-rds.us-east-2.rds.amazonaws.com",
  "port": "3306",
  "dbname": "seubanco"
}
```

Esse JSON é desserializado por `DeserializaJson` e usado para configurar o pool de conexões `HikariCP` dinamicamente. Nenhuma senha toca o código ou qualquer arquivo de configuração da aplicação.

---

## Stack

- Java 21 / Spring Boot 4.x
- Spring Data JPA + HikariCP
- MySQL (`mysql-connector-j`)
- AWS SDK v2 — `software.amazon.awssdk:secretsmanager`

---

## Criando o segredo com rotação no AWS Secrets Manager

### 1. Pré-requisitos

- RDS MySQL já provisionado e acessível
- AWS CLI instalado e configurado (ou acesso ao Console AWS)

### 2. Criar o segredo via Console

1. Acesse **AWS Console > Secrets Manager > Store a new secret**
2. Selecione **Credentials for Amazon RDS database**
3. Preencha usuário e senha do banco
4. Selecione o RDS instance vinculado
5. Nomeie o segredo como `secrets-demo/db-credentials` (ou altere a constante `SECRET_NAME` em `DataSourceConfig.java`)
6. Em **Configure rotation**:
   - Ative **Automatic rotation**
   - Defina o intervalo (ex: 30 dias)
   - Selecione **Use a Lambda function that Secrets Manager will create** — a AWS cria automaticamente a Lambda de rotação para RDS
7. Confirme e salve

> A Lambda de rotação altera a senha no RDS e atualiza o valor do segredo atomicamente. A aplicação sempre busca o valor atual via SDK na inicialização.

### 3. Criar o segredo via AWS CLI

```bash
# Cria o segredo com as credenciais iniciais
aws secretsmanager create-secret \
  --name "secrets-demo/db-credentials" \
  --region us-east-2 \
  --secret-string '{
    "username": "admin",
    "password": "SuaSenhaInicial",
    "host": "seu-rds.us-east-2.rds.amazonaws.com",
    "port": "3306",
    "dbname": "seubanco"
  }'

# Ativa a rotação automática a cada 30 dias
# (substitua o ARN pela Lambda de rotação do RDS criada pela AWS)
aws secretsmanager rotate-secret \
  --secret-id "secrets-demo/db-credentials" \
  --rotation-lambda-arn arn:aws:lambda:us-east-2:ACCOUNT_ID:function:SecretsManagerRDSMySQLRotationSingleUser \
  --rotation-rules AutomaticallyAfterDays=30 \
  --region us-east-2
```

> Para que a Lambda de rotação exista, você deve tê-la criado previamente via Console (passo 2) ou pelo [Serverless Application Repository da AWS](https://serverlessrepo.aws.amazon.com/applications/arn:aws:serverlessrepo:us-east-1:297356227824:applications~SecretsManagerRDSMySQLRotationSingleUser).

### 4. Testando a rotação manualmente

```bash
# Força uma rotação imediata para validar o fluxo
aws secretsmanager rotate-secret \
  --secret-id "secrets-demo/db-credentials" \
  --region us-east-2

# Verifica o valor atual do segredo após a rotação
aws secretsmanager get-secret-value \
  --secret-id "secrets-demo/db-credentials" \
  --region us-east-2 \
  --query SecretString \
  --output text
```

Reinicie a aplicação após a rotação e verifique que ela sobe normalmente e responde às requisições — confirmando que o SDK pegou a senha nova.

---

## Rodando localmente

O AWS SDK resolve as credenciais automaticamente seguindo a [Default Credential Provider Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). Para desenvolvimento local, a forma mais simples é usar a AWS CLI configurada.

### 1. Configure a AWS CLI

```bash
aws configure
```

Preencha:
```
AWS Access Key ID:     <sua-access-key>
AWS Secret Access Key: <sua-secret-key>
Default region name:   us-east-2
Default output format: json
```

Isso grava as credenciais em `~/.aws/credentials`. O SDK as detecta automaticamente — nenhuma configuração extra na aplicação é necessária.

### 2. Confirme o acesso ao segredo

```bash
aws secretsmanager get-secret-value \
  --secret-id "secrets-demo/db-credentials" \
  --region us-east-2
```

### 3. Suba a aplicação

```bash
./mvnw spring-boot:run
```

### 4. Testando os endpoints

```bash
# Listar produtos
curl http://localhost:8080/produtos

# Criar um produto
curl -X POST http://localhost:8080/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome": "Teclado", "preco": 199.90}'
```

---

## Produção — IAM Role na EC2 (padrao recomendado)

**Em produção, nunca configure credenciais manualmente.** O padrão correto é atribuir uma **IAM Role** à instância EC2 onde a aplicação roda. O SDK detecta essas credenciais automaticamente via **Instance Metadata Service (IMDS)** — sem `aws configure`, sem variáveis de ambiente, sem nenhum arquivo de chave.

### Por que é mais seguro

| | Local (AWS CLI) | Produção (IAM Role) |
|---|---|---|
| Onde ficam as credenciais | `~/.aws/credentials` no disco | Apenas na memória, via IMDS |
| Rotação | Manual | Automática pela AWS (a cada hora) |
| Risco de vazamento | Arquivo pode ser exposto | Sem credenciais persistentes |
| Configuração na aplicação | Nenhuma | Nenhuma |

### Como configurar

**1. Crie uma IAM Role para EC2 com a policy abaixo:**

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:us-east-2:ACCOUNT_ID:secret:secrets-demo/db-credentials-*"
    }
  ]
}
```

**2. Associe a Role à instância EC2:**

- Console: EC2 > Instância > Actions > Security > Modify IAM Role
- CLI:
```bash
aws ec2 associate-iam-instance-profile \
  --instance-id i-XXXXXXXXXXXXXXXXX \
  --iam-instance-profile Name=NomeDaSuaRole
```

**3. Suba a aplicação normalmente — nenhuma alteração no código:**

```bash
./mvnw spring-boot:run
```

O SDK detecta a Role via IMDS (`http://169.254.169.254/...`) e obtém credenciais temporárias com rotação automática. O código em `DataSourceConfig.java` é exatamente o mesmo que no ambiente local.

---

## Estrutura do projeto

```
src/main/java/com/example/secrets_demo/
├── Main.java
├── config/
│   ├── DataSourceConfig.java        # Busca o segredo e configura o DataSource
│   └── utils/
│       ├── DeserializaJson.java     # Desserializa o JSON do segredo
│       └── SetHikariCP.java         # Configura o pool HikariCP
├── controller/
│   └── ProdutoController.java       # GET /produtos, POST /produtos
├── model/
│   └── Produto.java
└── repository/
    └── ProdutoRepository.java
```

---

## Variáveis para ajustar

Todas as configurações sensíveis ao ambiente estão em `DataSourceConfig.java`:

```java
private static final String SECRET_NAME = "secrets-demo/db-credentials"; // nome do segredo no Secrets Manager
private static final Region REGION = Region.US_EAST_2;                    // região AWS
```