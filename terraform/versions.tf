terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

locals {
  name        = var.project_name
  ingest_jar  = "${path.module}/../ingest/build/libs/ingest-${var.app_version}-lambda.jar"
  query_image = var.query_image
}
