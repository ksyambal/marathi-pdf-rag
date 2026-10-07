# marathi-pdf-rag

Cloud-native RAG on AWS: PDFs land in S3, EventBridge starts a Java ingest pipeline, Textract OCRs English pages, embeddings go to **Amazon S3 Vectors**, and a Spring Boot query service on ECS calls **OpenAI** only when retrieval is confident.

**Marathi constraint:** Amazon Textract does not OCR Devanagari. Prefix Marathi scans with `raw/mr/` — the Step Functions path fails until you plug in an Indic OCR worker. English PDFs go to `raw/`. Comprehend is NLP (language/PII), not OCR.

## Architecture

```text
Upload PDF --> S3 (raw/) --> EventBridge --> SQS --> Lambda
                                                    |
                                                    v
                                            Step Functions
                                     /                      \
                          Textract (en)                 Indic OCR (mr)
                                     \                      /
                                      v                    v
                               Lambda: chunk + OpenAI embed + S3 Vectors upsert

Client --> InvokeAgentRuntime --> AgentCore Runtime (ARM64 Docker, port 8080)
                                      |                         |
                                      v                         v
                               S3 Vectors                  OpenAI chat
```

## Modules

| Path | Role |
|---|---|
| `common` | Chunking, OCR routing, S3 Vectors, OpenAI, query orchestrator |
| `ingest` | Java 21 Lambdas (shaded JAR) |
| `query` | Spring Boot 3 API |
| `terraform` | S3, EventBridge, SQS, Lambda, Step Functions, ECR, Bedrock AgentCore, CloudWatch |

S3 Vectors index: cosine, dimension **1536** for `text-embedding-3-small`. Terraform creates the vector bucket and index. Query distance is converted to similarity (`1 - distance`) before the score threshold.

## Local build

Java 21 and Gradle 8.8+ on PATH:

```bash
gradle :common:test :ingest:shadowJar :query:bootJar
```

Query service. Set these in the same PowerShell window, then start the app. `set` does not export environment variables in PowerShell, so the process would call OpenAI with an empty key.

```powershell
$env:OPENAI_API_KEY = "sk-..."
$env:S3_VECTOR_BUCKET = "marathi-pdf-rag-vec-<account-id>"
$env:S3_VECTOR_INDEX = "chunks"
gradle :query:bootRun
```

```bash
curl -X POST http://localhost:8080/query -H "Content-Type: application/json" -d "{\"question\":\"What is in the circular?\"}"
```

## Deploy

1. Copy `terraform/terraform.tfvars.example` to `terraform/terraform.tfvars` and set the OpenAI key. Terraform creates the S3 vector bucket and `chunks` index.
2. Build the ingest JAR (Terraform hashes `ingest/build/libs/ingest-0.1.0-lambda.jar`).
3. Apply once so Terraform creates the ECR repository, then build the ARM64 image and push it. AgentCore rejects non-ARM64 images. Set `query_image` to that URI and apply again:

```bash
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.ap-south-1.amazonaws.com
gradle :query:bootJar
docker buildx build --platform linux/arm64 -f query/Dockerfile -t <account>.dkr.ecr.ap-south-1.amazonaws.com/marathi-pdf-rag-query:0.1.0 --push .
```

The container listens on port 8080 and exposes `GET /ping` and `POST /invocations`.

4. Apply:

```bash
cd terraform
terraform init
terraform apply
```

5. Upload an English PDF:

```bash
aws s3 cp sample.pdf s3://<pdf_bucket>/raw/sample.pdf
```

6. Invoke the agent. `runtimeSessionId` must be at least 33 characters:

```bash
aws bedrock-agentcore invoke-agent-runtime --region ap-south-1 --agent-runtime-arn <agent_runtime_arn> --runtime-session-id 11111111-1111-1111-1111-111111111111 --payload "{\"prompt\":\"Summarize the document\"}" outfile.json
```

## Page OCR image

Marathi and mixed PDFs run on ECS Fargate with Tesseract `mar+eng`. English text-layer PDFs stay on async Textract, which resumes Step Functions from the SNS completion message.

```powershell
gradle :ocr:shadowJar
docker build -f ocr/Dockerfile -t 325054522625.dkr.ecr.ap-south-1.amazonaws.com/marathi-pdf-rag-ocr:0.1.0 .
docker push 325054522625.dkr.ecr.ap-south-1.amazonaws.com/marathi-pdf-rag-ocr:0.1.0
```

Put Marathi or mixed scans in `raw/mr/`. A digital PDF under `raw/` that contains Devanagari is routed to the same worker.

## Implementation plan (remaining)

1. API auth on the AgentCore runtime.
2. Move AgentCore from `PUBLIC` network mode to a VPC if the runtime must stay private.

## Query orchestration

`QueryOrchestrator` skips the LLM for greetings. When S3 Vectors returns a confident match, OpenAI answers from those PDF excerpts and may add general knowledge for anything the excerpts do not cover. When no excerpt is confident enough, OpenAI answers from general knowledge and the response kind is `llm`.
