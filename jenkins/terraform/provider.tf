terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.40.0"
    }
  }
}

provider "aws" {
  region  = "us-east-1"
  
  default_tags {
    tags = {
      CreatedBy      = "Terraform"
      OrchestratedBy = "Terraform"
    }
  }
}