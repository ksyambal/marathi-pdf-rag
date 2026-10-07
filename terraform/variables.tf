variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "project_name" {
  type    = string
  default = "marathi-pdf-rag"
}

variable "app_version" {
  type    = string
  default = "0.1.0"
}

variable "query_image" {
  type        = string
  description = "ARM64 ECR image URI for the AgentCore runtime, e.g. 123.dkr.ecr.ap-south-1.amazonaws.com/marathi-pdf-rag-query:0.1.0"
}

variable "openai_api_key" {
  type      = string
  sensitive = true
}

variable "vector_dimension" {
  type        = number
  default     = 1536
  description = "Must match the OpenAI embedding model. text-embedding-3-small is 1536."
}

variable "vector_namespace" {
  type        = string
  default     = "default"
  description = "Stored as S3 Vectors metadata. S3 Vectors has no native namespace."
}

variable "ocr_image" {
  type        = string
  default     = ""
  description = "Optional ECR image URI for the page OCR worker. Empty uses <ocr repository>:0.1.0."
}

variable "raw_prefix" {
  type    = string
  default = "raw/"
}
